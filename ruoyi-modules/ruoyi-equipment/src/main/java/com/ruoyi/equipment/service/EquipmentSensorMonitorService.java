package com.ruoyi.equipment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.ruoyi.equipment.entity.EquipmentSensorMonitor;
import com.ruoyi.equipment.entity.query.MonitorDataQuery;
import com.ruoyi.equipment.entity.vo.MonitorDataVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备监控 Service 接口
 * <p>
 * 提供监测数据的上报、查询等业务操作
 * </p>
 *
 * @author smartartisan
 */
public interface EquipmentSensorMonitorService extends IRepository<EquipmentSensorMonitor> {

    /**
     * 查询传感器实时数据
     *
     * @param sensorId 传感器ID
     * @return 最新监测数据
     */
    MonitorDataVO getRealtimeBySensorId(Integer sensorId);

    /**
     * 查询设备实时状态（所有传感器最新数据）
     *
     * @param equipmentId 设备ID
     * @return 传感器最新监测数据列表
     */
    List<MonitorDataVO> getRealtimeByEquipmentId(Integer equipmentId);

    /**
     * 查询历史数据（分页）
     *
     * @param query 查询参数
     * @return 分页结果
     */
    IPage<MonitorDataVO> queryHistory(MonitorDataQuery query);

    /**
     * 上报传感器监测数据（MQTT 接入）
     *
     * @param sensorCode  传感器编码
     * @param sensorValue 传感器数值
     * @param timestamp   数据时间戳
     */
    void reportSensorData(String sensorCode, Double sensorValue, LocalDateTime timestamp);
}
