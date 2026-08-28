package com.ruoyi.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.equipment.entity.EquipmentSensorMonitor;
import com.ruoyi.equipment.entity.query.MonitorDataQuery;
import com.ruoyi.equipment.entity.vo.MonitorDataVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备监控 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有 CRUD 功能
 * </p>
 *
 * @author smartartisan
 */
@Mapper
public interface EquipmentSensorMonitorMapper extends BaseMapper<EquipmentSensorMonitor> {

    /**
     * 查询传感器最新数据
     *
     * @param sensorId 传感器ID
     * @return 最新监测数据
     */
    MonitorDataVO selectLatestBySensorId(@Param("sensorId") Integer sensorId);

    /**
     * 查询设备所有传感器最新数据
     *
     * @param equipmentId 设备ID
     * @return 传感器最新监测数据列表
     */
    List<MonitorDataVO> selectLatestByEquipmentId(@Param("equipmentId") Integer equipmentId);

    /**
     * 历史数据分页查询
     *
     * @param page  分页对象
     * @param query 查询参数
     * @return 分页结果
     */
    IPage<MonitorDataVO> selectMonitorDataPage(Page<MonitorDataVO> page, @Param("query") MonitorDataQuery query);
}
