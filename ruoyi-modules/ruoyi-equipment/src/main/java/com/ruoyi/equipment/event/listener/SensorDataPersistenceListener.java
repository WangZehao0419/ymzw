package com.ruoyi.equipment.event.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.entity.EquipmentSensorMonitor;
import com.ruoyi.equipment.event.SensorDataReceivedEvent;
import com.ruoyi.equipment.service.EquipmentSensorMonitorService;
import com.ruoyi.equipment.service.EquipmentSensorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 传感器数据持久化监听器
 * <p>
 * 最高优先级（Order=0）、同步执行。保证数据落盘后才继续后续链路。
 * 查询传感器信息 + 写入 monitor 记录在同一线程完成，天然事务一致。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorDataPersistenceListener {

    private final EquipmentSensorMonitorService monitorService;
    private final EquipmentSensorService sensorService;

    @EventListener
    @Order(0)
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        String sensorCode = event.getSensorCode();
        Double sensorValue = event.getSensorValue();
        LocalDateTime timestamp = event.getDataTimestamp();

        // 根据 sensorCode 查找传感器注册信息
        EquipmentSensor sensor = sensorService.getOne(
                new LambdaQueryWrapper<EquipmentSensor>()
                        .eq(EquipmentSensor::getSensorCode, sensorCode));

        if (sensor == null) {
            log.warn("未识别的传感器编码，跳过持久化: sensorCode={}", sensorCode);
            return;
        }

        EquipmentSensorMonitor monitor = new EquipmentSensorMonitor();
        monitor.setSensorId(sensor.getId());
        monitor.setSensorValue(sensorValue);
        monitor.setCreateTime(timestamp != null ? timestamp : LocalDateTime.now());
        monitorService.save(monitor);

        log.debug("传感器数据已持久化: sensorCode={}, sensorId={}, value={}", sensorCode, sensor.getId(), sensorValue);
    }
}
