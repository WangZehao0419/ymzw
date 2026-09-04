package com.ruoyi.alert.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.alert.event.SensorDataReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 传感器数据 RocketMQ 消费者(接入层)
 * <p>
 * 消费 equipment 模块转发的 cloud-iot-sensor-data 主题(消息体为 JSON 字符串),
 * 解析后发布 SensorDataReceivedEvent,进入与 MQTT 时代完全相同的
 * 事件驱动检测链路(RuleDetectionListener → AlertPersistenceListener)。
 * name-server 取全局 rocketmq.name-server 配置。
 * </p>
 * <p>
 * 异常语义:解析失败(毒消息)吞掉记日志避免无限重试;
 * 检测链路抛出的异常向上传播,由 rocketmq-spring 自动 RECONSUME_LATER 重试。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "cloud-iot-sensor-data",
        consumerGroup = "alert-sensor-consumer")
        // [二分诊断] ORDERLY 在本组合(client 5.3.1 remoting + broker 5.3.2)下消息被跳过未进 listener,暂时回退并发模式定位根因
        // consumeMode = ConsumeMode.ORDERLY
public class SensorDataMqConsumer implements RocketMQListener<String> {

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String json) {
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (Exception e) {
            // 毒消息:解析失败重试无意义,吞掉并记日志
            log.error("[MQ] 消息解析失败,跳过: {}, error={}", json, e.getMessage());
            return;
        }

        String sensorCode = node.path("sensorCode").asText(null);
        double sensorValue = node.path("sensorValue").asDouble(Double.NaN);

        if (sensorCode == null || sensorCode.isEmpty() || Double.isNaN(sensorValue)) {
            log.warn("[MQ] 消息缺少必填字段 sensorCode/sensorValue: {}", json);
            return;
        }

        int equipmentId = node.path("equipmentId").asInt(0);
        // asInt 缺省 0 表示字段缺失或非法,归一化为 null 保持"无 id"语义(下游按 id 匹配规则)
        Integer sensorId = node.path("sensorId").asInt(0);
        if (sensorId <= 0) {
            sensorId = null;
        }
        LocalDateTime ts = parseTimestamp(node.path("timestamp").asText(null));

        // 发布领域事件,由 L1 规则判定监听器消费(同步执行,异常向上抛触发 MQ 重试)
        eventPublisher.publishEvent(new SensorDataReceivedEvent(this, sensorCode, sensorId, sensorValue, equipmentId, ts));
        log.debug("[MQ] 事件已发布: code={}, sensorId={}, equipmentId={}, value={}", sensorCode, sensorId, equipmentId, sensorValue);
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
            log.warn("[MQ] 时间戳解析失败,使用当前时间: {}", tsStr);
            return LocalDateTime.now();
        }
    }
}
