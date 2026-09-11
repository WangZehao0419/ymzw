package com.ruoyi.equipment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.entity.query.EquipmentSensorQuery;
import com.ruoyi.equipment.entity.vo.EquipmentSensorVO;

import java.util.List;

/**
 * 设备传感器 Service 接口
 * <p>
 * 提供传感器信息的增删改查等业务操作
 * </p>
 *
 * @author smartartisan
 */
public interface EquipmentSensorService extends IRepository<EquipmentSensor> {

    /**
     * 分页查询传感器列表
     *
     * @param query 查询参数
     * @return 分页结果
     */
    IPage<EquipmentSensorVO> page(EquipmentSensorQuery query);

    /**
     * 根据ID查询传感器详情
     *
     * @param id 传感器ID
     * @return 传感器详情
     */
    EquipmentSensorVO getDetailById(Integer id);

    /**
     * 根据设备ID查询传感器列表
     *
     * @param equipmentId 设备ID
     * @return 传感器列表
     */
    List<EquipmentSensorVO> getByEquipmentId(Integer equipmentId);

    /**
     * 检查传感器编号是否唯一
     *
     * @param sensorCode 传感器编号
     * @param excludeId  排除的传感器ID（用于更新时排除自身）
     * @return true-唯一，false-已存在
     */
    boolean checkSensorCodeUnique(String sensorCode, Integer excludeId);

    /**
     * 新增传感器
     *
     * @param sensor 传感器信息
     * @return 是否成功
     */
    boolean addSensor(EquipmentSensor sensor);

    /**
     * 更新传感器
     *
     * @param sensor 传感器信息
     * @return 是否成功
     */
    boolean updateSensor(EquipmentSensor sensor);

    /**
     * 启用/禁用传感器
     *
     * @param id     传感器ID
     * @param status 传感器状态（0-禁用，1-启用）
     * @return 是否成功
     */
    boolean updateStatus(Integer id, Integer status);

    /**
     * 按编码查询传感器元数据（带 60 秒 TTL 进程内缓存，含负缓存），供高频事件监听器使用
     *
     * @param sensorCode 传感器编号
     * @return 传感器元数据，未注册返回 null
     */
    EquipmentSensor getByCodeCached(String sensorCode);

    /**
     * 采集设备健康基线：该设备每个传感器取当前时刻前最近 BASELINE_POINTS 个数据点序列化存 sensor_baseline
     *
     * @param equipmentId 设备ID
     * @return {collected: 采集成功传感器数, empty: 无数据传感器数}
     */
    java.util.Map<String, Integer> collectBaseline(Integer equipmentId);
}
