package com.ruoyi.alert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.event.AlertTriggeredEvent;
import com.ruoyi.alert.event.SensorDataReceivedEvent;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L1 规则判定引擎(自研轻量,不引 Drools)
 * <p>
 * 数据到达 → 查规则 → 阈值判断(>upper 或 <lower) → 持续 N 点计数(防抖) → 命中发布告警事件。
 * 持续计数用内存 Map(单实例假设),不越界或触发后清零。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDetectionService {

    private final RuleService ruleService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    // 跨模块元数据经 Feign 获取,不再直连 equipment_sensor 表
    private final RemoteEquipmentService remoteEquipmentService;

    /** 持续越界计数: key=sensorId, value=连续越界次数 */
    private final Map<Integer, Integer> sustainCounter = new ConcurrentHashMap<>();

    /**
     * 对单个数据点做 L1 规则判定
     */
    public void detect(SensorDataReceivedEvent event) {
        Integer sensorId = event.getSensorId();
        if (sensorId == null) {
            // 消息未携带 sensorId(equipment 侧按编码回填失败),无主键可匹配规则
            log.debug("[L1] 消息缺少 sensorId,跳过检测: code={}", event.getSensorCode());
            return;
        }

        AlertRule rule = findRule(sensorId);
        if (rule == null) {
            sustainCounter.remove(sensorId);
            return;
        }

        double value = event.getSensorValue();
        String breach = breachReason(rule, value);
        if (breach == null) {
            // 未越界,清零持续计数
            sustainCounter.remove(sensorId);
            return;
        }

        // 越界,持续计数 +1
        int count = sustainCounter.merge(sensorId, 1, Integer::sum);
        int need = rule.getSustainPoints() == null || rule.getSustainPoints() < 1
                ? 1 : rule.getSustainPoints();

        if (count >= need) {
            sustainCounter.remove(sensorId);
            publishAlert(event, rule, value, breach, count);
        }
    }

    /**
     * 按传感器ID查启用的规则(每次查库,单条等值查询,数据量小可接受)
     */
    private AlertRule findRule(Integer sensorId) {
        return ruleService.lambdaQuery()
                .eq(AlertRule::getSensorId, sensorId)
                .eq(AlertRule::getEnabled, 1)
                .one();
    }

    /**
     * 判断是否越界,返回越界原因(不越界返回 null)
     */
    private String breachReason(AlertRule rule, double value) {
        if (rule.getUpperLimit() != null && value > rule.getUpperLimit()) {
            return "upper";
        }
        if (rule.getLowerLimit() != null && value < rule.getLowerLimit()) {
            return "lower";
        }
        return null;
    }

    /**
     * 构造告警事件并发布
     */
    private void publishAlert(SensorDataReceivedEvent event, AlertRule rule,
                              double value, String breach, int count) {
        AlertEvent alert = new AlertEvent();
        alert.setEquipmentId(event.getEquipmentId());
        alert.setSensorCode(event.getSensorCode());
        // 事件直接携带 sensorId,不再从元数据查询结果回填
        alert.setSensorId(event.getSensorId());
        // MQ 消息只携带 id/编码,展示字段(名称等)经 Feign 查设备服务回填;查不到保持 null 不阻断告警
        fillSensorMeta(alert, event.getSensorId());
        alert.setAlertType("RULE");
        alert.setAlertLevel(rule.getLevel() == null ? "WARNING" : rule.getLevel());
        alert.setAlertStatus("FIRING");
        alert.setSensorValue(value);
        alert.setTriggerTime(event.getDataTime() == null ? LocalDateTime.now() : event.getDataTime());
        alert.setEscalationCount(0);
        alert.setEvidence(buildEvidence(value, breach, count));

        eventPublisher.publishEvent(new AlertTriggeredEvent(this, alert));
        log.info("[L1] 规则命中告警: sensorId={}, value={}, breach={}, sustain={}, level={}",
                event.getSensorId(), value, breach, count, alert.getAlertLevel());
    }

    /**
     * 经 Feign 查设备服务回填告警展示字段
     * <p>
     * 只在告警命中时执行(防抖后频率极低),单条查询;
     * 传感器未注册/设备服务不可用等异常情况仅告警缺字段,不影响告警主链路。
     * </p>
     */
    private void fillSensorMeta(AlertEvent alert, Integer sensorId) {
        try {
            R<List<SensorMetaDTO>> result = remoteEquipmentService.listSensorMetaByIds(
                    List.of(sensorId), SecurityConstants.INNER);
            if (result == null || R.FAIL == result.getCode()
                    || result.getData() == null || result.getData().isEmpty()) {
                // 降级:元数据缺失仅导致展示字段为空,告警本身照常发布
                log.warn("[L1] 传感器元数据查询失败或不存在(不影响告警): sensorId={}", sensorId);
                return;
            }
            SensorMetaDTO meta = result.getData().get(0);
            alert.setSensorCode(meta.getSensorCode());
            alert.setSensorName(meta.getSensorName());
            alert.setEquipmentName(meta.getEquipmentName());
        } catch (Exception e) {
            log.warn("[L1] 告警元数据回填失败(不影响告警): sensorId={}, error={}", sensorId, e.getMessage());
        }
    }

    private String buildEvidence(double value, String breach, int count) {
        try {
            Map<String, Object> ev = new HashMap<>();
            ev.put("layer", "RULE");
            ev.put("value", value);
            ev.put("breach", breach);
            ev.put("sustain", count);
            return objectMapper.writeValueAsString(ev);
        } catch (Exception e) {
            return "{}";
        }
    }
}
