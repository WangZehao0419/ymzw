package com.ruoyi.equipment.event.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.entity.vo.MonitorDataVO;
import com.ruoyi.equipment.event.SensorDataReceivedEvent;
import com.ruoyi.equipment.service.EquipmentSensorService;
import com.ruoyi.equipment.stream.SensorStreamConnectionPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 传感器实时推送监听器
 * <p>
 * 异步执行（pushExecutor 线程池），在数据落盘后通过 NDJSON 流式通道
 * 推送给订阅该设备的前端。Order=1 确保持久化先完成。
 * 推送失败仅记录日志，不阻塞后续链路。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorDataPushListener {

    private final EquipmentSensorService sensorService;
    private final SensorStreamConnectionPool streamPool;

    @Async("pushExecutor")
    @EventListener
    @Order(1)
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        // 查找传感器信息用于构建 VO
        EquipmentSensor sensor = sensorService.getOne(
                new LambdaQueryWrapper<EquipmentSensor>()
                        .eq(EquipmentSensor::getSensorCode, event.getSensorCode()));

        if (sensor == null) {
            log.debug("传感器未注册，跳过推送: sensorCode={}", event.getSensorCode());
            return;
        }

        MonitorDataVO vo = new MonitorDataVO();
        vo.setSensorId(sensor.getId());
        vo.setSensorCode(sensor.getSensorCode());
        vo.setSensorName(sensor.getSensorName());
        vo.setSensorUnit(sensor.getSensorUnit());
        vo.setSensorValue(event.getSensorValue());
        vo.setEquipmentId(sensor.getEquipmentId());
        vo.setEquipmentName(sensor.getEquipmentName());
        vo.setCreateTime(event.getDataTimestamp());

        try {
            // NDJSON 流式推送给订阅该设备的前端
            streamPool.send(sensor.getEquipmentId(), vo);
        } catch (Exception e) {
            log.error("NDJSON 流推送失败: sensorCode={}, error={}", event.getSensorCode(), e.getMessage());
        }

        log.debug("实时推送完成: sensorCode={}, equipmentId={}", event.getSensorCode(), sensor.getEquipmentId());
    }
}
