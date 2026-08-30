package com.ruoyi.equipment.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.entity.query.MonitorDataQuery;
import com.ruoyi.equipment.entity.td.TdSensorPoint;
import com.ruoyi.equipment.entity.vo.MonitorDataVO;
import com.ruoyi.equipment.tdengine.TdSensorDataMapper;
import com.ruoyi.equipment.service.EquipmentSensorMonitorService;
import com.ruoyi.equipment.service.EquipmentSensorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备监控 Service 实现类
 * <p>
 * 时序数据读写已彻底切换 TDengine:本类不再持有 MySQL monitor 表的
 * Mapper/实体,仅作为查询编排层——TDengine 负责时序点(sensor_id/ts/val),
 * MySQL 传感器表负责元数据(名称/单位/所属设备),查询后按 id 关联补齐 VO。
 * 实时推送仍由事件监听器走 NDJSON 流式通道完成,与本类无关。
 * </p>
 *
 * @author smartartisan
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EquipmentSensorMonitorServiceImpl implements EquipmentSensorMonitorService {

    private final TdSensorDataMapper tdSensorDataMapper;
    private final EquipmentSensorService equipmentSensorService;

    @Override
    public IPage<MonitorDataVO> queryHistory(MonitorDataQuery query) {
        // 归一化分页参数:page>=1 保证 offset 非负,防止外部传入 0/负数或显式 null 覆盖默认值
        int page = Math.max(1, query.getPage() == null ? 1 : query.getPage());
        int pageSize = query.getPageSize() == null || query.getPageSize() < 0 ? 10 : query.getPageSize();

        // TDengine tag 已直接存 sensor_id(MySQL 主键),可透传过滤,无需再回 MySQL 换编码
        List<Integer> sensorIds = query.getSensorId() != null ? List.of(query.getSensorId()) : null;

        try {
            long total = tdSensorDataMapper.countHistory(sensorIds, query.getEquipmentId(),
                    query.getStartTime(), query.getEndTime());
            // total 为 0 时跳过明细查询,省一次 TDengine 往返
            List<TdSensorPoint> points = total == 0
                    ? Collections.emptyList()
                    : tdSensorDataMapper.queryHistory(sensorIds, query.getEquipmentId(),
                            query.getStartTime(), query.getEndTime(), (page - 1) * pageSize, pageSize);
            Map<Integer, EquipmentSensor> sensorMap = loadSensorMeta(points);
            List<MonitorDataVO> records = points.stream()
                    .map(p -> toVO(p, sensorMap.get(p.getSensorId())))
                    .collect(Collectors.toList());
            Page<MonitorDataVO> result = new Page<>(page, pageSize, total);
            result.setRecords(records);
            return result;
        } catch (Exception e) {
            // TDengine 不可用时降级返回空页,避免历史查询接口整体报错影响前端
            log.error("[TDengine] 历史数据查询失败, sensorId={}, equipmentId={}",
                    query.getSensorId(), query.getEquipmentId(), e);
            return emptyPage(page, pageSize);
        }
    }

    @Override
    public MonitorDataVO getRealtimeBySensorId(Integer sensorId) {
        if (sensorId == null) {
            return null;
        }
        try {
            // tag 已是 sensor_id,先查时序点;无数据直接返回,省一次 MySQL 元数据查询
            TdSensorPoint point = tdSensorDataMapper.queryLatest(sensorId);
            if (point == null) {
                return null;
            }
            // 再回 MySQL 补元数据;查不到时 toVO 内部降级,仅填 id/数值/时间
            EquipmentSensor sensor = equipmentSensorService.getById(sensorId);
            return toVO(point, sensor);
        } catch (Exception e) {
            // 降级返回 null,Controller 统一转换为"暂无数据"提示
            log.error("[TDengine] 传感器实时数据查询失败, sensorId={}", sensorId, e);
            return null;
        }
    }

    @Override
    public List<MonitorDataVO> getRealtimeByEquipmentId(Integer equipmentId) {
        try {
            List<TdSensorPoint> points = tdSensorDataMapper.queryLatestByEquipment(equipmentId);
            if (points.isEmpty()) {
                return Collections.emptyList();
            }
            Map<Integer, EquipmentSensor> sensorMap = loadSensorMeta(points);
            return points.stream()
                    .map(p -> toVO(p, sensorMap.get(p.getSensorId())))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // 降级返回空列表,保证设备实时状态接口可用
            log.error("[TDengine] 设备实时数据查询失败, equipmentId={}", equipmentId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 批量加载传感器元数据(id -> sensor)
     * <p>时序点自带 sensor_id,一次按主键批量取回元数据,避免逐条回表 MySQL。</p>
     */
    private Map<Integer, EquipmentSensor> loadSensorMeta(List<TdSensorPoint> points) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Integer> ids = points.stream()
                .map(TdSensorPoint::getSensorId)
                .distinct()
                .collect(Collectors.toList());
        List<EquipmentSensor> sensors = equipmentSensorService.listByIds(ids);
        // 主键理论唯一,merge 兜底防止脏数据导致 toMap 抛异常
        return sensors.stream().collect(
                Collectors.toMap(EquipmentSensor::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * TDengine 时序点 + 传感器元数据 -> 前端 VO
     * <p>元数据缺失(如传感器已删除但时序数据残留)时仅填 id/数值/时间,不让单条脏数据拖垮整个列表。</p>
     */
    private MonitorDataVO toVO(TdSensorPoint point, EquipmentSensor sensor) {
        MonitorDataVO vo = new MonitorDataVO();
        // TDengine 无自增主键概念,时序行不对应 MySQL 主键
        vo.setId(null);
        // 时序点自带 sensor_id(tag 列),元数据缺失时也能保证前端按 id 正确关联
        vo.setSensorId(point.getSensorId());
        vo.setSensorValue(point.getVal());
        vo.setCreateTime(point.getTs());
        if (sensor != null) {
            vo.setSensorCode(sensor.getSensorCode());
            vo.setSensorName(sensor.getSensorName());
            vo.setSensorUnit(sensor.getSensorUnit());
            vo.setEquipmentId(sensor.getEquipmentId());
            vo.setEquipmentName(sensor.getEquipmentName());
        }
        return vo;
    }

    /**
     * 构造空页(TDengine 异常降级时使用)
     */
    private Page<MonitorDataVO> emptyPage(int page, int pageSize) {
        Page<MonitorDataVO> result = new Page<>(page, pageSize, 0);
        result.setRecords(Collections.emptyList());
        return result;
    }
}
