package com.ruoyi.equipment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.ruoyi.equipment.entity.Equipment;
import com.ruoyi.equipment.entity.query.EquipmentQuery;
import com.ruoyi.equipment.entity.vo.EquipmentVO;

/**
 * 设备基础信息 Service 接口
 * <p>
 * 提供设备信息的增删改查等业务操作
 * </p>
 *
 * @author smartartisan
 */
public interface EquipmentService extends IRepository<Equipment> {

    /**
     * 分页查询设备列表
     *
     * @param query 查询参数
     * @return 分页结果
     */
    IPage<EquipmentVO> page(EquipmentQuery query);

    /**
     * 根据ID查询设备详情
     *
     * @param id 设备ID
     * @return 设备详情
     */
    EquipmentVO getDetailById(Integer id);

    /**
     * 检查设备编号是否唯一
     *
     * @param equipmentNo 设备编号
     * @param excludeId   排除的设备ID（用于更新时排除自身）
     * @return true-唯一，false-已存在
     */
    boolean checkEquipmentNoUnique(String equipmentNo, Integer excludeId);

    /**
     * 新增设备
     *
     * @param equipment 设备信息
     * @return 是否成功
     */
    boolean addEquipment(Equipment equipment);

    /**
     * 更新设备
     *
     * @param equipment 设备信息
     * @return 是否成功
     */
    boolean updateEquipment(Equipment equipment);

    /**
     * 更新设备状态
     *
     * @param id     设备ID
     * @param status 设备状态
     * @return 是否成功
     */
    boolean updateStatus(Integer id, String status);
}
