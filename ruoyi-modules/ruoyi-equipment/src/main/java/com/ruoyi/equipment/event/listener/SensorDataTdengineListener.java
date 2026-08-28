package com.ruoyi.equipment.event.listener;

import com.ruoyi.equipment.event.SensorDataReceivedEvent;
import com.ruoyi.equipment.service.TdengineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 传感器数据 TDengine 时序落库监听器
 * <p>
 * 异步执行(tdengineExecutor 独立线程池),与 MySQL 落库、实时推送、
 * RocketMQ 转发并行,互不阻塞。
 * TDengine 不可用时仅记录日志,不影响其他链路。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorDataTdengineListener {

    private final TdengineService tdengineService;

    @Async("tdengineExecutor")
    @EventListener
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        try {
            tdengineService.insertSensorData(event.getSensorCode(), event.getEquipmentId(),
                    event.getSensorValue(), event.getDataTimestamp());
            log.debug("TDengine 落库完成: sensorCode={}, value={}", event.getSensorCode(), event.getSensorValue());
        } catch (Exception e) {
            // TDengine 不可用时只记日志,不阻断 MQTT 消费与其他监听器
            log.error("TDengine 落库失败: sensorCode={}, value={}, error={}",
                    event.getSensorCode(), event.getSensorValue(), e.getMessage());
        }
    }
}
