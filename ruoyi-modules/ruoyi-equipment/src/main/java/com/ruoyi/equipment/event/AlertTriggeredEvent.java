package com.ruoyi.equipment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 告警触发领域事件
 * <p>
 * AI 预测告警判定需要告警时发布，由 AlertPersistenceListener / AlertNotificationListener
 * 等独立消费。当前仅定义事件结构，监听器后续实现。
 * </p>
 *
 * @author smartartisan
 */
@Getter
public class AlertTriggeredEvent extends ApplicationEvent {

    /** 设备 ID */
    private final Integer equipmentId;

    /** 传感器 ID */
    private final Integer sensorId;

    /** 传感器编码 */
    private final String sensorCode;

    /** 告警级别：NORMAL / WARNING / SEVERE */
    private final String alertLevel;

    /** AI 返回的告警分析结论 */
    private final String alertMessage;

    /** AI 原始响应（完整 JSON，用于后续人工审查） */
    private final String aiRawResponse;

    public AlertTriggeredEvent(Object source,
                               Integer equipmentId,
                               Integer sensorId,
                               String sensorCode,
                               String alertLevel,
                               String alertMessage,
                               String aiRawResponse) {
        super(source);
        this.equipmentId = equipmentId;
        this.sensorId = sensorId;
        this.sensorCode = sensorCode;
        this.alertLevel = alertLevel;
        this.alertMessage = alertMessage;
        this.aiRawResponse = aiRawResponse;
    }
}
