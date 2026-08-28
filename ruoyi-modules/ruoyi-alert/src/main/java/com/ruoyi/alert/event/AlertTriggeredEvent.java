package com.ruoyi.alert.event;

import com.ruoyi.alert.entity.AlertEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 告警触发领域事件
 * <p>
 * L1/L2 判定命中时发布,由告警落库监听器等消费。
 * </p>
 *
 * @author smartartisan
 */
@Getter
public class AlertTriggeredEvent extends ApplicationEvent {

    /** 待落库的告警事件 */
    private final AlertEvent alertEvent;

    public AlertTriggeredEvent(Object source, AlertEvent alertEvent) {
        super(source);
        this.alertEvent = alertEvent;
    }
}
