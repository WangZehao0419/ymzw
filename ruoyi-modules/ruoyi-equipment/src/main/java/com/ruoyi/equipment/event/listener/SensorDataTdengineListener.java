package com.ruoyi.equipment.event.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.event.SensorDataReceivedEvent;
import com.ruoyi.equipment.tdengine.TdSensorDataMapper;
import com.ruoyi.equipment.service.EquipmentSensorService;
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

    private final TdSensorDataMapper tdSensorDataMapper;
    private final EquipmentSensorService sensorService;

    @Async("tdengineExecutor")
    @EventListener
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        try {
            // tag 用 sensor_id,需查 MySQL 元数据把编码换成 id(MQTT 入口只携带编码)
            EquipmentSensor sensor = sensorService.getOne(new LambdaQueryWrapper<EquipmentSensor>()
                    .eq(EquipmentSensor::getSensorCode, event.getSensorCode()));
            if (sensor == null) {
                // 未知编码无 id 可写,跳过落库:脏编码只告警一次,不逐条刷错误日志
                log.warn("未识别的传感器编码,跳过TDengine落库: sensorCode={}", event.getSensorCode());
                return;
            }
            // MyBatis Mapper 版写入:insertOne(sensorId, equipmentId, ts, val),语义与原 JdbcTemplate 版一致
            tdSensorDataMapper.insertOne(sensor.getId(), event.getEquipmentId(),
                    event.getDataTimestamp(), event.getSensorValue());
            log.debug("TDengine 落库完成: sensorId={}, value={}", sensor.getId(), event.getSensorValue());
        } catch (Exception e) {
            // 含元数据查询在内的异常都只记日志,不阻断 MQTT 消费与其他监听器
            // (失败路径 sensor 变量未必已解析,故错误日志仍用编码定位)
            log.error("TDengine 落库失败: sensorCode={}, value={}, error={}",
                    event.getSensorCode(), event.getSensorValue(), e.getMessage());
        }
    }
}
