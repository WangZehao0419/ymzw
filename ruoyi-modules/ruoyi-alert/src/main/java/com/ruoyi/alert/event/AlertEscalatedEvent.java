package com.ruoyi.alert.event;

import com.ruoyi.alert.entity.AlertEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 告警升级领域事件
 * <p>
 * 同方向(upper/lower)更高等级规则命中且存在活动告警时发布,
 * 携带的 AlertEvent 为升级后的既有告警(已含最新等级/升级次数/证据),
 * 由落库(update)/流推送/责任人通知监听器消费。
 * </p>
 *
 * @author smartartisan
 */
@Getter
public class AlertEscalatedEvent extends ApplicationEvent {

    /** 升级后的告警事件(既有记录,非新建) */
    private final AlertEvent alertEvent;

    public AlertEscalatedEvent(Object source, AlertEvent alertEvent) {
        super(source);
        this.alertEvent = alertEvent;
    }
}
