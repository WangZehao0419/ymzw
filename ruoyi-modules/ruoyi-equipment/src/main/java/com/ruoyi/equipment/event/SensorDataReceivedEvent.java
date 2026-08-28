package com.ruoyi.equipment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 传感器数据到达领域事件
 * <p>
 * MQTT 接收到传感器上报数据后发布，由各 Listener 独立消费：
 * 持久化、实时推送、AI 预测告警。
 * equipmentCode 携带多级主题 sensor/{equipmentCode}/{sensorCode} 中的设备编码，
 * 存量二段主题 sensor/{sensorCode} 无该信息时为 null。
 * </p>
 *
 * @author smartartisan
 */
@Getter
public class SensorDataReceivedEvent extends ApplicationEvent {

    /** 传感器编码（业务唯一标识） */
    private final String sensorCode;

    /** 传感器数值 */
    private final Double sensorValue;

    /** 设备 ID（TDengine TAG 与 RocketMQ 转发均需要，报文缺失时为 0） */
    private final int equipmentId;

    /** 数据时间戳 */
    private final LocalDateTime dataTimestamp;

    /** MQTT 原始主题（用于扩展场景：不同 topic 不同策略） */
    private final String mqttTopic;

    /** 设备编码（多级主题 sensor/{equipmentCode}/{sensorCode} 提取；存量二段主题为 null） */
    private final String equipmentCode;

    /**
     * 旧构造函数：兼容存量调用点，equipmentCode 默认 null（等价存量二段主题时代的行为）
     */
    public SensorDataReceivedEvent(Object source,
                                   String sensorCode,
                                   Double sensorValue,
                                   int equipmentId,
                                   LocalDateTime timestamp,
                                   String mqttTopic) {
        this(source, sensorCode, sensorValue, equipmentId, timestamp, mqttTopic, null);
    }

    public SensorDataReceivedEvent(Object source,
                                   String sensorCode,
                                   Double sensorValue,
                                   int equipmentId,
                                   LocalDateTime timestamp,
                                   String mqttTopic,
                                   String equipmentCode) {
        super(source);
        this.sensorCode = sensorCode;
        this.sensorValue = sensorValue;
        this.equipmentId = equipmentId;
        this.dataTimestamp = timestamp;
        this.mqttTopic = mqttTopic;
        this.equipmentCode = equipmentCode;
    }
}
