package com.ruoyi.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.entity.query.EquipmentSensorQuery;
import com.ruoyi.equipment.entity.vo.EquipmentSensorVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备传感器 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有 CRUD 功能
 * </p>
 *
 * @author smartartisan
 */
@Mapper
public interface EquipmentSensorMapper extends BaseMapper<EquipmentSensor> {

    /**
     * 分页查询传感器列表
     *
     * @param page  分页对象
     * @param query 查询参数
     * @return 分页结果
     */
    IPage<EquipmentSensorVO> selectSensorPage(Page<EquipmentSensorVO> page, @Param("query") EquipmentSensorQuery query);

    /**
     * 根据设备ID查询传感器列表
     *
     * @param equipmentId 设备ID
     * @return 传感器列表
     */
    List<EquipmentSensorVO> selectByEquipmentId(@Param("equipmentId") Integer equipmentId);
}
