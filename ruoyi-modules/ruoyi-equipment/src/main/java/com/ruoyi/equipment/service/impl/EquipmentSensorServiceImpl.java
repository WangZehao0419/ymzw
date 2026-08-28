package com.ruoyi.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.equipment.repository.BaseRepository;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.entity.query.EquipmentSensorQuery;
import com.ruoyi.equipment.entity.vo.EquipmentSensorVO;
import com.ruoyi.equipment.enums.SensorStatusEnum;
import com.ruoyi.equipment.mapper.EquipmentSensorMapper;
import com.ruoyi.equipment.service.EquipmentSensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备传感器 Service 实现类
 * <p>
 * 使用 MyBatis-Plus 的 Service 层进行数据操作
 * </p>
 *
 * @author smartartisan
 */
@Service
@RequiredArgsConstructor
public class EquipmentSensorServiceImpl extends BaseRepository<EquipmentSensorMapper, EquipmentSensor> implements EquipmentSensorService {

    private final EquipmentSensorMapper equipmentSensorMapper;

    @Override
    public EquipmentSensorMapper getBaseMapper() {
        return equipmentSensorMapper;
    }

    @Override
    public IPage<EquipmentSensorVO> page(EquipmentSensorQuery query) {
        Page<EquipmentSensorVO> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<EquipmentSensorVO> result = equipmentSensorMapper.selectSensorPage(page, query);
        // 填充状态描述
        result.getRecords().forEach(this::fillStatusDesc);
        return result;
    }

    @Override
    public EquipmentSensorVO getDetailById(Integer id) {
        EquipmentSensor sensor = this.getById(id);
        if (sensor == null) {
            return null;
        }
        EquipmentSensorVO vo = new EquipmentSensorVO();
        BeanUtils.copyProperties(sensor, vo);
        fillStatusDesc(vo);
        return vo;
    }

    @Override
    public List<EquipmentSensorVO> getByEquipmentId(Integer equipmentId) {
        List<EquipmentSensorVO> list = equipmentSensorMapper.selectByEquipmentId(equipmentId);
        list.forEach(this::fillStatusDesc);
        return list;
    }

    @Override
    public boolean checkSensorCodeUnique(String sensorCode, Integer excludeId) {
        LambdaQueryWrapper<EquipmentSensor> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EquipmentSensor::getSensorCode, sensorCode);
        if (excludeId != null) {
            wrapper.ne(EquipmentSensor::getId, excludeId);
        }
        return this.count(wrapper) == 0;
    }

    @Override
    public boolean addSensor(EquipmentSensor sensor) {
        if (!checkSensorCodeUnique(sensor.getSensorCode(), null)) {
            throw new ServiceException("传感器编号已存在");
        }
        // 默认设置为禁用状态
        if (sensor.getSensorStatus() == null) {
            sensor.setSensorStatus(SensorStatusEnum.DISABLED.getCode());
        }
        return this.save(sensor);
    }

    @Override
    public boolean updateSensor(EquipmentSensor sensor) {
        EquipmentSensor existingSensor = this.getById(sensor.getId());
        if (existingSensor == null) {
            throw new ServiceException("传感器不存在");
        }
        if (!checkSensorCodeUnique(sensor.getSensorCode(), sensor.getId())) {
            throw new ServiceException("传感器编号已存在");
        }
        return this.updateById(sensor);
    }

    @Override
    public boolean updateStatus(Integer id, Integer status) {
        EquipmentSensor sensor = this.getById(id);
        if (sensor == null) {
            throw new ServiceException("传感器不存在");
        }
        // 验证状态值是否有效
        if (SensorStatusEnum.getByCode(status) == null) {
            throw new ServiceException("无效的传感器状态");
        }
        sensor.setSensorStatus(status);
        return this.updateById(sensor);
    }

    /**
     * 填充传感器状态描述
     *
     * @param vo 传感器视图对象
     */
    private void fillStatusDesc(EquipmentSensorVO vo) {
        SensorStatusEnum statusEnum = SensorStatusEnum.getByCode(vo.getSensorStatus());
        if (statusEnum != null) {
            vo.setSensorStatusDesc(statusEnum.getDesc());
        }
    }
}
