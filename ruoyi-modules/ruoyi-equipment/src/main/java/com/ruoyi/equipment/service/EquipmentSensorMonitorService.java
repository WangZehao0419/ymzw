package com.ruoyi.equipment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.equipment.entity.query.MonitorDataQuery;
import com.ruoyi.equipment.entity.vo.MonitorDataVO;

import java.util.List;

/**
 * 设备监控 Service 接口
 * <p>
 * 提供监测数据的实时/历史查询等业务操作。
 * 时序数据已彻底切换 TDengine,不再继承 IRepository(MySQL monitor 表已下线),
 * 数据写入由 TDengine 监听器完成,本接口仅保留查询能力。
 * </p>
 *
 * @author smartartisan
 */
public interface EquipmentSensorMonitorService {

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
}
