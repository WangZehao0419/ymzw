package com.ruoyi.equipment.event.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.equipment.event.SensorDataReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 传感器数据 RocketMQ 转发监听器
 * <p>
 * 异步执行(mqExecutor 独立线程池),与落库、推送并行。
 * 消息体为 JSON 字符串(显式序列化,消费端 alert 模块按 String 接收),
 * topic 与 alert 侧 SensorDataMqConsumer 的注解约定一致。
 * Broker 未部署阶段发送失败仅记日志,不阻断其他链路。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorDataMqForwardListener {

    /** 传感器数据流 topic(equipment → alert) */
    public static final String TOPIC = "cloud-iot-sensor-data";

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Async("mqExecutor")
    @EventListener
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("sensorCode", event.getSensorCode());
            msg.put("sensorValue", event.getSensorValue());
            msg.put("equipmentId", event.getEquipmentId());
            msg.put("timestamp", event.getDataTimestamp());
            String json = objectMapper.writeValueAsString(msg);

            SendResult result = rocketMQTemplate.syncSend(TOPIC,
                    MessageBuilder.withPayload(json).build());
            log.debug("RocketMQ 转发完成: sensorCode={}, msgId={}, status={}",
                    event.getSensorCode(), result.getMsgId(), result.getSendStatus());
        } catch (Exception e) {
            // broker 未部署/不可达阶段只记日志,后续部署后自动恢复
            log.error("RocketMQ 转发失败: sensorCode={}, error={}", event.getSensorCode(), e.getMessage());
        }
    }
}
