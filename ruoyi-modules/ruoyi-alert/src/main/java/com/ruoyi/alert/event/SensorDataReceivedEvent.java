package com.ruoyi.alert.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 传感器数据到达领域事件
 * <p>
 * MQTT 消息解析后发布,由落库监听器与判定监听器消费。
 * </p>
 *
 * @author smartartisan
 */
@Getter
public class SensorDataReceivedEvent extends ApplicationEvent {

    /** 传感器编号 */
    private final String sensorCode;

    /** 传感器ID(规则匹配主键,消息未回填时为 null) */
    private final Integer sensorId;

    /** 传感器数值 */
    private final double sensorValue;

    /** 设备 ID */
    private final int equipmentId;

    /** 采集时间(字段名 dataTime,避免与 ApplicationEvent.final getTimestamp() 冲突) */
    private final LocalDateTime dataTime;

    public SensorDataReceivedEvent(Object source, String sensorCode, Integer sensorId, double sensorValue,
                                   int equipmentId, LocalDateTime dataTime) {
        super(source);
        this.sensorCode = sensorCode;
        this.sensorId = sensorId;
        this.sensorValue = sensorValue;
        this.equipmentId = equipmentId;
        this.dataTime = dataTime;
    }
}
