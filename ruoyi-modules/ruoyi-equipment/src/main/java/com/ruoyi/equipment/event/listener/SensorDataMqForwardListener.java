package com.ruoyi.equipment.event.listener;

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
            msg.put("timestamp", event.getDataTimestamp());
            // 回填 sensorId:MQTT 入口只携带编码,而告警侧规则按主键 id 匹配,
            // 元数据查不到或查询异常均不阻断转发(消费端按无 sensorId 场景处理)
            try {
                // 高频报文走 60 秒 TTL 缓存;查库异常不缓存直接抛出,由下方 catch 保持"继续转发"语义
                EquipmentSensor sensor = sensorService.getByCodeCached(event.getSensorCode());
                if (sensor != null) {
                    msg.put("sensorId", sensor.getId());
                    // equipmentId 同样以 MySQL 元数据为权威来源:OPC-UA 批量报文与
                    // 存量二段主题都不携带设备 ID,事件字段恒为 0,直接透传会让 alert 侧
                    // 设备反查(责任人/通知/工单)全部落空。与 TDengine/Push/Predictive
                    // 三个监听器的取值口径保持一致
                    msg.put("equipmentId", sensor.getEquipmentId());
                } else {
                    return;
                    // 编码未注册:退回事件值兜底(单传感器新格式报文可能自带 payload
                    // equipmentId;OPC-UA 批量场景该值为 0,消费端按无归属设备处理)
//                    msg.put("equipmentId", event.getEquipmentId());
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
