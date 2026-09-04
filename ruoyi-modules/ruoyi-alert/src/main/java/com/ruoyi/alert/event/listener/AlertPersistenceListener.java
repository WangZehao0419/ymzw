package com.ruoyi.alert.event.listener;

import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.entity.PredictAlert;
import com.ruoyi.alert.event.AlertEscalatedEvent;
import com.ruoyi.alert.event.AlertTriggeredEvent;
import com.ruoyi.alert.mapper.AlertEventMapper;
import com.ruoyi.alert.mapper.PredictAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 告警落库监听器
 * <p>
 * 消费 AlertTriggeredEvent,按告警类型分流落库(D1): 用户视角
 * "告警记录=已发生的阈值告警",故 RULE/STAT 落 alert_event(告警记录),
 * PREDICT(将发生的预测告警)落独立表 predict_alert(预测性维护),两表互不掺杂。
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

    private final PredictAlertMapper predictAlertMapper;

    @EventListener
    public void onAlertTriggered(AlertTriggeredEvent event) {
        AlertEvent alertEvent = event.getAlertEvent();
        if ("PREDICT".equals(alertEvent.getAlertType())) {
            // 预测告警独立落 predict_alert;from 含 id 拷贝,插入前必须清空走自增主键
            PredictAlert pa = PredictAlert.from(alertEvent);
            pa.setId(null);
            predictAlertMapper.insert(pa);
            // 主键回填(D6): AlertPushListener 等待 id 回填后广播,状态机
            // firePredictAlert 在 publish 后取 id 作句柄,回填到同一事件对象保证两者零感知
            alertEvent.setId(pa.getId());
        } else {
            alertEventMapper.insert(alertEvent);
        }
    }

    /**
     * 升级是既有告警记录的字段更新(等级/升级次数/证据),走 update 而非 insert
     */
    @EventListener
    public void onAlertEscalated(AlertEscalatedEvent event) {
        alertEventMapper.updateById(event.getAlertEvent());
    }
    /*
        AlertEvent alert = event.getAlertEvent();  // 从事件中取出告警实体
        alertEventMapper.insert(alert);  // 插入 alert_event 表存证
    */
}
