package com.ruoyi.equipment.event.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.event.AlertTriggeredEvent;
import com.ruoyi.equipment.event.SensorDataReceivedEvent;
import com.ruoyi.equipment.feign.AiServiceFeignClient;
import com.ruoyi.equipment.service.EquipmentSensorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 预测告警监听器
 * <p>
 * 异步执行（aiExecutor 线程池），通过 Feign 调用 cloud-ai 预测性告警接口。
 * 此链路可能耗时数秒，独立线程池不阻塞数据入库和实时推送。
 * AI 调用失败仅记录日志，不影响核心数据链路。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorDataPredictiveAlertListener {

    private final AiServiceFeignClient aiServiceFeignClient;
    private final EquipmentSensorService sensorService;
    private final ApplicationEventPublisher eventPublisher;

    @Async("aiExecutor")
    @EventListener
    @Order(2)
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        String sensorCode = event.getSensorCode();
        Double sensorValue = event.getSensorValue();

        // 查询传感器信息获取 sensorId 和 equipmentId
        EquipmentSensor sensor = sensorService.getOne(
                new LambdaQueryWrapper<EquipmentSensor>()
                        .eq(EquipmentSensor::getSensorCode, sensorCode));

        if (sensor == null) {
            log.debug("传感器未注册，跳过 AI 预测告警: sensorCode={}", sensorCode);
            return;
        }

        try {
            Map<String, Object> result = aiServiceFeignClient.predictiveAlarm(
                    sensor.getEquipmentId(),
                    sensor.getId(),
                    sensorValue);

            log.debug("AI 预测告警响应: sensorCode={}, result={}", sensorCode, result);

            if (result == null || !Boolean.TRUE.equals(result.get("success"))) {
                log.warn("AI 预测告警未成功: sensorCode={}, result={}", sensorCode, result);
                return;
            }

            String aiResponse = (String) result.get("response");
            if (aiResponse == null || aiResponse.isEmpty()) {
                return;
            }

            // 从 AI 响应中提取告警级别
            // AI 响应格式示例："[告警级别: 警告] 温度传感器 TH-001 检测到趋势漂移..."
            String alertLevel = extractAlertLevel(aiResponse);

            // 仅当告警级别为 WARNING 或 SEVERE 时发布告警事件
            if ("WARNING".equals(alertLevel) || "SEVERE".equals(alertLevel)) {
                AlertTriggeredEvent alertEvent = new AlertTriggeredEvent(
                        this,
                        sensor.getEquipmentId(),
                        sensor.getId(),
                        sensorCode,
                        alertLevel,
                        aiResponse,
                        aiResponse // 原始响应即告警消息
                );
                eventPublisher.publishEvent(alertEvent);

                log.info("告警已触发: equipmentId={}, sensorCode={}, level={}",
                        sensor.getEquipmentId(), sensorCode, alertLevel);
            }

        } catch (Exception e) {
            // AI 调用失败不影响数据入库和推送的核心链路
            log.error("AI 预测告警调用异常: sensorCode={}, error={}", sensorCode, e.getMessage());
        }
    }

    /**
     * 从 AI 响应文本中提取告警级别
     * <p>
     * 匹配模式：[告警级别: XXX] 或 alertLevel:XXX。
     * 未匹配到则默认返回 NORMAL。
     * </p>
     */
    private String extractAlertLevel(String aiResponse) {
        if (aiResponse == null) {
            return "NORMAL";
        }

        // 匹配中文格式: [告警级别: 警告] 或 [告警级别: 严重]
        java.util.regex.Matcher cnMatcher = java.util.regex.Pattern
                .compile("告警级别[：:]\\s*(\\S+)")
                .matcher(aiResponse);
        if (cnMatcher.find()) {
            String level = cnMatcher.group(1).trim();
            if (level.contains("警告") || level.contains("WARNING")) {
                return "WARNING";
            }
            if (level.contains("严重") || level.contains("SEVERE") || level.contains("紧急")) {
                return "SEVERE";
            }
            if (level.contains("正常") || level.contains("NORMAL")) {
                return "NORMAL";
            }
        }

        // 匹配英文格式: alertLevel: WARNING
        java.util.regex.Matcher enMatcher = java.util.regex.Pattern
                .compile("alertLevel[：:]\\s*(\\w+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(aiResponse);
        if (enMatcher.find()) {
            return enMatcher.group(1).toUpperCase();
        }

        return "NORMAL";
    }
}
