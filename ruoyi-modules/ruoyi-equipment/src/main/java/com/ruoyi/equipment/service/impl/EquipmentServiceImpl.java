package com.ruoyi.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.equipment.repository.BaseRepository;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.equipment.entity.Equipment;
import com.ruoyi.equipment.entity.query.EquipmentQuery;
import com.ruoyi.equipment.entity.vo.EquipmentVO;
import com.ruoyi.equipment.mapper.EquipmentMapper;
import com.ruoyi.equipment.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * 设备基础信息 Service 实现类
 * <p>
 * 使用 MyBatis-Plus 的 Service 层进行数据操作
 * </p>
 *
 * @author smartartisan
 */
@Service
@RequiredArgsConstructor
public class EquipmentServiceImpl extends BaseRepository<EquipmentMapper, Equipment> implements EquipmentService {

    private final EquipmentMapper equipmentMapper;

    @Override
    public EquipmentMapper getBaseMapper() {
        return equipmentMapper;
    }

    @Override
    public IPage<EquipmentVO> page(EquipmentQuery query) {
        Page<EquipmentVO> page = new Page<>(query.getPage(), query.getPageSize());
        return equipmentMapper.selectEquipmentPage(page, query);
    }

    @Override
    public EquipmentVO getDetailById(Integer id) {
        Equipment equipment = this.getById(id);
        if (equipment == null) {
            return null;
        }
        EquipmentVO vo = new EquipmentVO();
        BeanUtils.copyProperties(equipment, vo);
        return vo;
    }

    @Override
    public boolean checkEquipmentNoUnique(String equipmentNo, Integer excludeId) {
        LambdaQueryWrapper<Equipment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Equipment::getEquipmentNo, equipmentNo);
        if (excludeId != null) {
            wrapper.ne(Equipment::getId, excludeId);
        }
        return this.count(wrapper) == 0;
    }

    @Override
    public boolean addEquipment(Equipment equipment) {
        if (!checkEquipmentNoUnique(equipment.getEquipmentNo(), null)) {
            throw new ServiceException("设备编号已存在");
        }
        return this.save(equipment);
    }

    @Override
    public boolean updateEquipment(Equipment equipment) {
        Equipment existingEquipment = this.getById(equipment.getId());
        if (existingEquipment == null) {
            throw new ServiceException("设备不存在");
        }
        if (!checkEquipmentNoUnique(equipment.getEquipmentNo(), equipment.getId())) {
            throw new ServiceException("设备编号已存在");
        }
        return this.updateById(equipment);
    }

    @Override
    public boolean updateStatus(Integer id, String status) {
        Equipment equipment = this.getById(id);
        if (equipment == null) {
            throw new ServiceException("设备不存在");
        }
        equipment.setEquipmentStatus(status);
        return this.updateById(equipment);
    }
}
