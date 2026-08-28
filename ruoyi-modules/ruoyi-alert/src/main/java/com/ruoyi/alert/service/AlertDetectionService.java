package com.ruoyi.alert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.event.AlertTriggeredEvent;
import com.ruoyi.alert.event.SensorDataReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
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

    /** 持续越界计数: key=sensorCode, value=连续越界次数 */
    private final Map<String, Integer> sustainCounter = new ConcurrentHashMap<>();

    /**
     * 对单个数据点做 L1 规则判定
     */
    public void detect(SensorDataReceivedEvent event) {
        String code = event.getSensorCode();
        AlertRule rule = findRule(code);
        if (rule == null) {
            sustainCounter.remove(code);
            return;
        }

        double value = event.getSensorValue();
        String breach = breachReason(rule, value);
        if (breach == null) {
            // 未越界,清零持续计数
            sustainCounter.remove(code);
            return;
        }

        // 越界,持续计数 +1
        int count = sustainCounter.merge(code, 1, Integer::sum);
        int need = rule.getSustainPoints() == null || rule.getSustainPoints() < 1
                ? 1 : rule.getSustainPoints();

        if (count >= need) {
            sustainCounter.remove(code);
            publishAlert(event, rule, value, breach, count);
        }
    }

    /**
     * 按传感器编号查启用的规则(每次查库,单条等值查询,数据量小可接受)
     */
    private AlertRule findRule(String code) {
        return ruleService.lambdaQuery()
                .eq(AlertRule::getSensorCode, code)
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
        alert.setAlertType("RULE");
        alert.setAlertLevel(rule.getLevel() == null ? "WARNING" : rule.getLevel());
        alert.setAlertStatus("FIRING");
        alert.setSensorValue(value);
        alert.setTriggerTime(event.getDataTime() == null ? LocalDateTime.now() : event.getDataTime());
        alert.setEscalationCount(0);
        alert.setEvidence(buildEvidence(value, breach, count));

        eventPublisher.publishEvent(new AlertTriggeredEvent(this, alert));
        log.info("[L1] 规则命中告警: code={}, value={}, breach={}, sustain={}, level={}",
                event.getSensorCode(), value, breach, count, alert.getAlertLevel());
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
