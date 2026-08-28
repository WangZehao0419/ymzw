package com.ruoyi.alert.event.listener;

import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.event.AlertTriggeredEvent;
import com.ruoyi.alert.mapper.AlertEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 告警落库监听器
 * <p>
 * 消费 AlertTriggeredEvent,将告警事件落 alert_event 表。
 * 去重/静默/状态机等处理层逻辑在 T4 补充。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertPersistenceListener {

    private final AlertEventMapper alertEventMapper;

    @EventListener
    public void onAlertTriggered(AlertTriggeredEvent event) {
        AlertEvent alert = event.getAlertEvent();
        alertEventMapper.insert(alert);
        log.info("[L1] 告警已落库: id={}, code={}, level={}",
                alert.getId(), alert.getSensorCode(), alert.getAlertLevel());
    }
}
