package com.ruoyi.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.equipment.repository.BaseRepository;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.entity.EquipmentSensorMonitor;
import com.ruoyi.equipment.entity.query.MonitorDataQuery;
import com.ruoyi.equipment.entity.vo.MonitorDataVO;
import com.ruoyi.equipment.mapper.EquipmentSensorMonitorMapper;
import com.ruoyi.equipment.service.EquipmentSensorMonitorService;
import com.ruoyi.equipment.service.EquipmentSensorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备监控 Service 实现类
 * <p>
 * 使用 MyBatis-Plus 的 Service 层进行数据操作，
 * 集成 MQTT 实时数据采集，实时推送由事件监听器走 NDJSON 流式通道完成
 * </p>
 *
 * @author smartartisan
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EquipmentSensorMonitorServiceImpl extends BaseRepository<EquipmentSensorMonitorMapper, EquipmentSensorMonitor> implements EquipmentSensorMonitorService {

    private final EquipmentSensorMonitorMapper monitorMapper;

    @Override
    public EquipmentSensorMonitorMapper getBaseMapper() {
        return monitorMapper;
    }
    private final EquipmentSensorService equipmentSensorService;

    @Override
    public MonitorDataVO getRealtimeBySensorId(Integer sensorId) {
        return monitorMapper.selectLatestBySensorId(sensorId);
    }

    @Override
    public List<MonitorDataVO> getRealtimeByEquipmentId(Integer equipmentId) {
        return monitorMapper.selectLatestByEquipmentId(equipmentId);
    }

    @Override
    public IPage<MonitorDataVO> queryHistory(MonitorDataQuery query) {
        Page<MonitorDataVO> page = new Page<>(query.getPage(), query.getPageSize());
        return monitorMapper.selectMonitorDataPage(page, query);
    }

    @Override
    public void reportSensorData(String sensorCode, Double sensorValue, LocalDateTime timestamp) {
        EquipmentSensor sensor = equipmentSensorService.getOne(
                new LambdaQueryWrapper<EquipmentSensor>()
                        .eq(EquipmentSensor::getSensorCode, sensorCode)
        );

        if (sensor == null) {
            log.warn("未识别的传感器编码: {}", sensorCode);
            return;
        }

        EquipmentSensorMonitor monitor = new EquipmentSensorMonitor();
        monitor.setSensorId(sensor.getId());
        monitor.setSensorValue(sensorValue);
        monitor.setCreateTime(timestamp != null ? timestamp : LocalDateTime.now());
        save(monitor);
        // 实时推送由 SensorDataPushListener 监听事件后走 NDJSON 流式通道完成，此处只负责落库
    }

    /**
     * 验证传感器是否存在并返回传感器信息
     *
     * @param sensorId 传感器ID
     * @return 传感器实体
     * @throws ServiceException 当传感器不存在时抛出异常
     */
    private EquipmentSensor validateAndGetSensor(Integer sensorId) {
        EquipmentSensor sensor = equipmentSensorService.getById(sensorId);
        if (sensor == null) {
            throw new ServiceException("传感器不存在");
        }
        return sensor;
    }
}
