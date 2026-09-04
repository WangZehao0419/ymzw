package com.ruoyi.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.event.AlertEscalatedEvent;
import com.ruoyi.alert.event.AlertTriggeredEvent;
import com.ruoyi.alert.event.SensorDataReceivedEvent;
import com.ruoyi.alert.mapper.AlertEventMapper;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L1 规则判定引擎(自研轻量,不引 Drools)
 * <p>
 * 数据到达 → 查该传感器全部启用规则(支持一传感器多规则分级) → 逐条阈值判断(>upper 或 <lower)
 * → 各规则按自身 sustainPoints 独立防抖计数(计数 key=ruleId) → 命中后按"活动告警+等级比较"决策:
 * 无同方向活动告警则新建,更高等级则升级既有告警(只升不降),否则忽略去重。
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
    // 升级决策需查既有活动告警(RULE 类型 FIRING/ACKED),读库旁路不影响判定主链路
    private final AlertEventMapper alertEventMapper;

    /** 持续越界计数: key=ruleId(多规则各自独立防抖,互不污染), value=连续越界次数 */
    private final Map<Long, Integer> sustainCounter = new ConcurrentHashMap<>();

    /**
     * 对单个数据点做 L1 规则判定
     */
    public void detect(SensorDataReceivedEvent event) {
//        传感器id
        Integer sensorId = event.getSensorId();
        if (sensorId == null) {
            return;
        }
//        告警规则
        List<AlertRule> rules = findRules(sensorId);
        if (rules == null || rules.isEmpty()) {
            return;
        }
//        传感器数值
        double sensorValue = event.getSensorValue();
        for (AlertRule rule : rules) {
//            判断是否越界
            String breachReason = breachReason(rule, sensorValue);
            if (breachReason == null) {
//                未越界
                sustainCounter.remove(rule.getId());
                continue;
            }

//            已越界
            Integer count = sustainCounter.merge(rule.getId(), 1, Integer::sum);
//            规则越界次数
            Integer need = rule.getSustainPoints();

            if (count>=need) {
                sustainCounter.remove(rule.getId());
                decideAlert(event, rule, sensorValue, breachReason, count);
            }

        }
    }

    /**
     * 按传感器ID查全部启用规则(每次查库,数据量小可接受)
     */
    private List<AlertRule> findRules(Integer sensorId) {
        return ruleService.lambdaQuery()
                .eq(AlertRule::getSensorId, sensorId)
                .eq(AlertRule::getEnabled, 1)
                .list();
    }

    /**
     * 告警决策:无同方向活动告警新建,更高等级升级,否则去重忽略
     * <p>
     * 同传感器+同方向(upper/lower)+RULE 类型至多一条活动告警;
     * 升级单向只升不降(值回落时保持既有等级,恢复由人工确认)。
     * </p>
     */
    private void decideAlert(SensorDataReceivedEvent event, AlertRule rule,
                             double value, String breach, int count) {
        // 等级归一化:规则未配等级按 WARNING 处理,与新建告警默认一致
        String newLevel = rule.getLevel() == null ? "WARNING" : rule.getLevel();
        AlertEvent active = findActiveAlert(event.getSensorId(), breach);
        if (active == null) {
            publishAlert(event, rule, value, breach, count);
            return;
        }
        if (levelRank(newLevel) <= levelRank(active.getAlertLevel())) {
            // 同向已有活动告警且新等级不高:去重,不新建不重复通知(修正现状反复插表的刷屏行为)
            log.info("[L1] 同方向活动告警已存在且等级不低于本次命中,忽略: sensorId={}, breach={}, activeLevel={}, newLevel={}",
                    event.getSensorId(), breach, active.getAlertLevel(), newLevel);
            return;
        }
        escalateAlert(event, active, rule, value, newLevel);
    }

    /**
     * 升级既有活动告警并发布升级事件(同一告警记录,不新建)
     */
    private void escalateAlert(SensorDataReceivedEvent event, AlertEvent active,
                               AlertRule rule, double value, String newLevel) {
        String oldLevel = active.getAlertLevel();
        active.setAlertLevel(newLevel);
        // 升级次数 null 当 0 起算
        active.setEscalationCount(active.getEscalationCount() == null ? 1 : active.getEscalationCount() + 1);
        active.setSensorValue(value);
        // 已确认告警升级需重新引起关注,重置 FIRING;FIRING 保持
        if ("ACKED".equals(active.getAlertStatus())) {
            active.setAlertStatus("FIRING");
        }
        // triggerTime 保留首次触发时间("告警开始时间"语义),升级时刻记在 evidence.escalations
        active.setEvidence(appendEscalation(active.getEvidence(), event, rule, value, oldLevel, newLevel));
        eventPublisher.publishEvent(new AlertEscalatedEvent(this, active));
        log.info("[L1] 告警升级: sensorId={}, alertId={}, from={}, to={}, value={}",
                event.getSensorId(), active.getId(), oldLevel, newLevel, value);
    }

    /**
     * 查同传感器同方向(upper/lower)的活动告警(FIRING/ACKED),返回最新一条,无则 null
     * <p>
     * 方向记录在 evidence JSON 的 breach 字段,需逐条解析比对;
     * 个别记录 evidence 脏数据/解析失败跳过,不影响其余候选。
     * </p>
     */
    private AlertEvent findActiveAlert(Integer sensorId, String breach) {
        List<AlertEvent> actives = alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getSensorId, sensorId)
                .eq(AlertEvent::getAlertType, "RULE")
                .in(AlertEvent::getAlertStatus, "FIRING", "ACKED")
                .orderByDesc(AlertEvent::getId));
        if (actives == null || actives.isEmpty()) {
            return null;
        }
        for (AlertEvent alert : actives) {
            try {
                Map<?, ?> ev = objectMapper.readValue(
                        alert.getEvidence() == null ? "{}" : alert.getEvidence(), Map.class);
                if (breach.equals(ev.get("breach"))) {
                    return alert;
                }
            } catch (Exception e) {
                log.warn("[L1] 活动告警 evidence 解析失败,跳过该条: alertId={}, error={}", alert.getId(), e.getMessage());
            }
        }
        return null;
    }

    /**
     * 告警等级序: NORMAL(0) < WARNING(1) < IMPORTANT(2) < SEVERE(3) < CRITICAL(4),null/未知按 0 处理
     * <p>
     * IMPORTANT 插入中间档、CRITICAL 顶部新增,存量 WARNING/SEVERE 相对关系不变,零迁移兼容。
     * </p>
     */
    private static int levelRank(String level) {
        if ("CRITICAL".equals(level)) {
            return 4;
        }
        if ("SEVERE".equals(level)) {
            return 3;
        }
        if ("IMPORTANT".equals(level)) {
            return 2;
        }
        if ("WARNING".equals(level)) {
            return 1;
        }
        return 0;
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

    /**
     * 在原 evidence 上追加本次升级记录,保留首次触发信息
     * <p>
     * 追加结构: escalations: [{time, from, to, value, threshold, ruleId}];
     * 原 evidence 缺失/非法时重建基础结构,序列化整体异常时降级为仅含本次升级记录的最简 JSON。
     * </p>
     */
    private String appendEscalation(String evidence, SensorDataReceivedEvent event, AlertRule rule,
                                    double value, String oldLevel, String newLevel) {
        LocalDateTime time = event.getDataTime() == null ? LocalDateTime.now() : event.getDataTime();
        // 命中阈值按越界方向取上限/下限
        String breach = breachReason(rule, value);
        Object threshold = "upper".equals(breach) ? rule.getUpperLimit() : rule.getLowerLimit();
        Map<String, Object> record = new HashMap<>();
        record.put("time", time.toString());
        record.put("from", oldLevel);
        record.put("to", newLevel);
        record.put("value", value);
        record.put("threshold", threshold);
        record.put("ruleId", rule.getId());
        try {
            Map<String, Object> ev;
            try {
                ev = evidence == null ? new HashMap<>() : objectMapper.readValue(evidence, Map.class);
            } catch (Exception parseEx) {
                // 原 evidence 非法:重建基础结构,升级链从本次起算
                ev = new HashMap<>();
                ev.put("layer", "RULE");
            }
            List<Object> escalations = new ArrayList<>();
            Object existing = ev.get("escalations");
            if (existing instanceof List) {
                escalations.addAll((List<?>) existing);
            }
            escalations.add(record);
            ev.put("escalations", escalations);
            return objectMapper.writeValueAsString(ev);
        } catch (Exception e) {
            // 兜底:至少保留本次升级记录,绝不因证据拼接失败阻断升级主链路
            try {
                Map<String, Object> minimal = new HashMap<>();
                minimal.put("layer", "RULE");
                minimal.put("escalations", List.of(record));
                return objectMapper.writeValueAsString(minimal);
            } catch (Exception ex) {
                return "{}";
            }
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
