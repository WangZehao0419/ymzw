package com.ruoyi.equipment.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.equipment.event.SensorDataReceivedEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * MQTT 消息处理器(接入层)
 * <p>
 * 订阅 sensor/# 主题,解析传感器数据 JSON 后发布 SensorDataReceivedEvent。
 * 本模块是传感器数据唯一入口:落库(MySQL/TDengine)、实时推送、AI 预测告警、
 * RocketMQ 转发告警模块,均由独立监听器消费,实现事件驱动解耦。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageHandler implements MqttCallback {

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private MqttClient mqttClient;

    @PostConstruct
    public void init() {
        if (mqttClient == null) {
            log.warn("[MQTT] 客户端未初始化,消息处理器不启动");
            return;
        }
        mqttClient.setCallback(this);
        log.info("[MQTT] 消息处理器已注册");
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("[MQTT] 连接断开: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        if (!topic.startsWith("sensor/")) {
            log.debug("[MQTT] 忽略非 sensor 主题: {}", topic);
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(payload);

            // 先解析 topic 再取 payload 字段:多级 topic 中 equipmentCode/sensorCode 以 topic 为权威,
            // payload 里的 sensorCode 仅作交叉校验,避免两个来源不一致时数据归属错乱
            String[] parts = topic.split("/");
            String equipmentCode;
            String sensorCode;

            if (parts.length == 3) {
                // 新格式 sensor/{equipmentCode}/{sensorCode}:设备编码与传感器编码均从 topic 提取
                equipmentCode = parts[1];
                sensorCode = parts[2];
                if (equipmentCode.isEmpty() || sensorCode.isEmpty()) {
                    log.warn("[MQTT] 多级主题存在空段,忽略: {}", topic);
                    return;
                }
                // topic 为权威:payload 中 sensorCode 不一致时仅告警不阻断,以 topic 为准
                String payloadCode = node.path("sensorCode").asText(null);
                if (payloadCode != null && !payloadCode.isEmpty() && !payloadCode.equals(sensorCode)) {
                    log.warn("[MQTT] payload sensorCode({}) 与 topic sensorCode({}) 不一致,以 topic 为准: {}",
                            payloadCode, sensorCode, topic);
                }
            } else if (parts.length == 2) {
                // 存量格式 sensor/{sensorCode}:无设备编码,sensorCode 只能从 payload 取
                equipmentCode = null;
                sensorCode = node.path("sensorCode").asText(null);
                if (sensorCode == null || sensorCode.isEmpty()) {
                    log.warn("[MQTT] 二段主题消息缺少 sensorCode,忽略: topic={}, payload={}", topic, payload);
                    return;
                }
            } else {
                log.warn("[MQTT] 无法识别的主题段数,忽略: {}", topic);
                return;
            }

            // sensorValue 只能来自 payload(缺失则无法判定数值,保持原有忽略行为)
            double sensorValue = node.path("sensorValue").asDouble(Double.NaN);
            if (Double.isNaN(sensorValue)) {
                log.warn("[MQTT] 消息缺少必填字段 sensorValue: {}", payload);
                return;
            }

            int equipmentId = node.path("equipmentId").asInt(0);
            LocalDateTime ts = parseTimestamp(node.path("timestamp").asText(null));

            // 发布领域事件,由各监听器独立消费(持久化/推送/AI告警/MQ转发)
            eventPublisher.publishEvent(new SensorDataReceivedEvent(
                    this, sensorCode, sensorValue, equipmentId, ts, topic, equipmentCode));
            log.info("[MQTT] 事件已发布: code={}, equipmentCode={}, equipmentId={}, value={}, ts={}",
                    sensorCode, equipmentCode, equipmentId, sensorValue, ts);
        } catch (Exception e) {
            log.error("[MQTT] 消息处理异常: topic={}, payload={}, error={}", topic, payload, e.getMessage());
        }
    }

    /**
     * 解析时间戳,失败或缺失时使用当前时间
     */
    private LocalDateTime parseTimestamp(String tsStr) {
        if (tsStr == null || tsStr.isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(tsStr);
        } catch (Exception e) {
            log.warn("[MQTT] 时间戳解析失败,使用当前时间: {}", tsStr);
            return LocalDateTime.now();
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 只订阅不发布,空实现
    }
}
