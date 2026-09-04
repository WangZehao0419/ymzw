package com.ruoyi.equipment.event.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.event.SensorDataReceivedEvent;
import com.ruoyi.equipment.service.EquipmentSensorService;
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
 * 消息体携带 sensorId(按 sensorCode 反查 equipment_sensor 回填,
 * 告警侧规则按传感器主键 id 匹配)。
 * Broker 未部署阶段发送失败仅记日志,不阻断其他链路。
 * 分区顺序消息：同 sensorCode 固定队列，消费端 ConsumeMode.ORDERLY 队列级串行。
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
    private final EquipmentSensorService sensorService;

    @Async("mqExecutor")
    @EventListener
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("sensorCode", event.getSensorCode());
            msg.put("sensorValue", event.getSensorValue());
            msg.put("equipmentId", event.getEquipmentId());
            msg.put("timestamp", event.getDataTimestamp());
            // 回填 sensorId:MQTT 入口只携带编码,而告警侧规则按主键 id 匹配,
            // 元数据查不到或查询异常均不阻断转发(消费端按无 sensorId 场景处理)
            try {
                EquipmentSensor sensor = sensorService.getOne(new LambdaQueryWrapper<EquipmentSensor>()
                        .eq(EquipmentSensor::getSensorCode, event.getSensorCode()));
                if (sensor != null) {
                    msg.put("sensorId", sensor.getId());
                }
            } catch (Exception e) {
                log.warn("sensorId 回填查询失败,消息不带 sensorId 继续转发: sensorCode={}, error={}",
                        event.getSensorCode(), e.getMessage());
            }
            String json = objectMapper.writeValueAsString(msg);

            // 分区顺序消息：同 sensorCode 经 hash 固定路由到同一队列，配合消费端 ORDERLY 串行。
            // hashKey 选 sensorCode 而非 sensorId：sensorCode 是必填字段必有值，
            // sensorId 为回填字段可能缺失，路由键只需同传感器稳定一致
            SendResult result = rocketMQTemplate.syncSendOrderly(TOPIC,
                    MessageBuilder.withPayload(json).build(), event.getSensorCode());
            log.debug("RocketMQ 转发完成: sensorCode={}, msgId={}, status={}",
                    event.getSensorCode(), result.getMsgId(), result.getSendStatus());
        } catch (Exception e) {
            // broker 未部署/不可达阶段只记日志,后续部署后自动恢复
            log.error("RocketMQ 转发失败: sensorCode={}, error={}", event.getSensorCode(), e.getMessage());
        }
    }
}
