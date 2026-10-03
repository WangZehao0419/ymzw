package com.ruoyi.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.equipment.repository.BaseRepository;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.entity.query.EquipmentSensorQuery;
import com.ruoyi.equipment.entity.vo.EquipmentSensorVO;
import com.ruoyi.equipment.enums.SensorStatusEnum;
import com.ruoyi.equipment.mapper.EquipmentSensorMapper;
import com.ruoyi.equipment.service.EquipmentSensorService;
import com.ruoyi.equipment.tdengine.TdSensorDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备传感器 Service 实现类
 * <p>
 * 使用 MyBatis-Plus 的 Service 层进行数据操作
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquipmentSensorServiceImpl extends BaseRepository<EquipmentSensorMapper, EquipmentSensor> implements EquipmentSensorService {

    private final EquipmentSensorMapper equipmentSensorMapper;
    private final TdSensorDataMapper tdSensorDataMapper;

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
     * 基线窗口点数:2^10,模型输入窗口惯例长度(固定按条数取最近点,非时间窗口)
     */
    private static final int BASELINE_POINTS = 1024;

    /**
     * 基线 JSON 序列化器:无配置依赖,静态单例即可(线程安全)
     */
    private static final ObjectMapper BASELINE_MAPPER = new ObjectMapper();

    @Override
    public java.util.Map<String, Integer> collectBaseline(Integer equipmentId) {
        List<EquipmentSensor> sensors = this.lambdaQuery()
                .eq(EquipmentSensor::getEquipmentId, equipmentId)
                .list();
        int collected = 0;
        int empty = 0;
        for (EquipmentSensor sensor : sensors) {
            try {
                // DESC 取最近 n 点再反转为升序(与 inner 历史接口同口径,便于曲线直接绘制)
                List<com.ruoyi.equipment.api.domain.SensorPointDTO> desc =
                        tdSensorDataMapper.selectRecentWindow(sensor.getId(), BASELINE_POINTS);
                if (desc.isEmpty()) {
                    // 无数据如实置空并清掉旧基线(重复采集幂等)
                    sensor.setSensorBaseline(null);
                    this.updateById(sensor);
                    empty++;
                    continue;
                }
                List<com.ruoyi.equipment.api.domain.SensorPointDTO> asc = new java.util.ArrayList<>(desc);
                java.util.Collections.reverse(asc);
                sensor.setSensorBaseline(BASELINE_MAPPER.writeValueAsString(asc));
                this.updateById(sensor);
                collected++;
            } catch (Exception e) {
                // 单传感器失败(TDengine 不可用/序列化异常)不中断其余传感器
                log.warn("[baseline] 传感器基线采集失败, sensorId={}, sensorCode={}: {}",
                        sensor.getId(), sensor.getSensorCode(), e.getMessage());
                empty++;
            }
        }
        java.util.Map<String, Integer> result = new java.util.HashMap<>();
        result.put("collected", collected);
        result.put("empty", empty);
        return result;
    }

    /** sensorCode 缓存 TTL(毫秒):过期后下一次查询回源 MySQL */
    private static final long CODE_CACHE_TTL_MS = 60_000L;

    /**
     * sensorCode → 元数据进程内缓存:OPC-UA 高频报文下多个事件监听器逐事件查库会积压,
     * 高频路径改走缓存;不引入 Caffeine 等新依赖,用 ConcurrentHashMap 手写。
     * 缓存与传感器增删改写路径存在最长 60 秒的不一致窗口,比赛场景可接受。
     */
    private final ConcurrentHashMap<String, CacheEntry> codeCache = new ConcurrentHashMap<>();

    /**
     * 缓存条目:sensor 为 null 表示负缓存(编码未注册),避免脏编码逐事件穿透查库
     */
    private static final class CacheEntry {
        final EquipmentSensor sensor;
        final long expireAt;

        CacheEntry(EquipmentSensor sensor, long expireAt) {
            this.sensor = sensor;
            this.expireAt = expireAt;
        }
    }

    // todo:处理+设备id
    @Override
    public EquipmentSensor getByCodeCached(String sensorCode) {
        CacheEntry entry = codeCache.get(sensorCode);
        // 命中且未过期直接返回(值可为 null 负缓存)
        if (entry != null && entry.expireAt > System.currentTimeMillis()) {
            return entry.sensor;
        }
        // 未命中/已过期回源查库;查库异常不缓存、直接向上抛出,保持调用方既有异常处理语义
        EquipmentSensor sensor = this.getOne(new LambdaQueryWrapper<EquipmentSensor>()
                .eq(EquipmentSensor::getSensorCode, sensorCode));
        // 未注册编码存 null 引用(负缓存)
        codeCache.put(sensorCode, new CacheEntry(sensor, System.currentTimeMillis() + CODE_CACHE_TTL_MS));
        return sensor;
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
