package com.ruoyi.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.equipment.entity.Equipment;
import com.ruoyi.equipment.entity.query.EquipmentQuery;
import com.ruoyi.equipment.entity.vo.EquipmentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 设备基础信息 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有 CRUD 功能
 * </p>
 *
 * @author smartartisan
 */
@Mapper
public interface EquipmentMapper extends BaseMapper<Equipment> {

    /**
     * 分页查询设备列表
     *
     * @param page  分页对象
     * @param query 查询参数
     * @return 分页结果
     */
    IPage<EquipmentVO> selectEquipmentPage(Page<EquipmentVO> page, @Param("query") EquipmentQuery query);
}
