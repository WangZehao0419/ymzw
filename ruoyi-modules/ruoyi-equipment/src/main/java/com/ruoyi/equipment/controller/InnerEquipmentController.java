package com.ruoyi.equipment.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.equipment.api.domain.EquipmentMetaDTO;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;
import com.ruoyi.equipment.entity.Equipment;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.service.EquipmentSensorService;
import com.ruoyi.equipment.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * 设备元数据内部接口 Controller（内部服务调用）
 * <p>
 * 供告警等模块经 OpenFeign 远程查询设备/传感器元数据,
 * 端点由 @InnerAuth 保护,仅限服务间携带内部凭证的调用,
 * 不对网关外暴露。
 * </p>
 *
 * @author smartartisan
 */
@RestController
@RequestMapping("/inner")
@RequiredArgsConstructor
public class InnerEquipmentController {

    private final EquipmentService equipmentService;
    private final EquipmentSensorService sensorService;

    /**
     * 根据设备ID查询设备元数据（内部服务调用，@InnerAuth 保护）
     *
     * @param equipmentId 设备ID
     * @return 设备元数据
     */
    @InnerAuth
    @GetMapping("/equipment/{equipmentId}")
    public R<EquipmentMetaDTO> getEquipmentMeta(@PathVariable("equipmentId") Integer equipmentId) {
        Equipment equipment = equipmentService.getById(equipmentId);
        if (equipment == null) {
            return R.fail("设备不存在");
        }
        EquipmentMetaDTO dto = new EquipmentMetaDTO();
        dto.setId(equipment.getId());
        dto.setEquipmentName(equipment.getEquipmentName());
        // 负责人未分配时为 null,属正常,由调用方判空
        dto.setEquipmentUserId(equipment.getEquipmentUserId());
        return R.ok(dto);
    }

    /**
     * 根据传感器ID列表批量查询传感器元数据（内部服务调用，@InnerAuth 保护）
     *
     * @param sensorIds 传感器ID列表
     * @return 传感器元数据列表
     */
    @InnerAuth
    @PostMapping("/sensor/meta/batch")
    public R<List<SensorMetaDTO>> listSensorMetaByIds(@RequestBody List<Integer> sensorIds) {
        if (sensorIds == null || sensorIds.isEmpty()) {
            // 空列表直接返回,避免 listByIds 生成 in () 空 SQL 异常
            return R.ok(Collections.emptyList());
        }
        List<SensorMetaDTO> result = sensorService.listByIds(sensorIds).stream()
                .map(this::toSensorMeta)
                .toList();
        return R.ok(result);
    }

    /**
     * 查询全部传感器元数据列表（内部服务调用，@InnerAuth 保护）
     * <p>
     * 预测性维护(告警模块 PredictTask)每轮调度的全量取数入口:
     * 直接查 equipment_sensor 表(逻辑删除由 @TableLogic 自动过滤),
     * 设备名称取表内冗余字段,与 listSensorMetaByIds 复用同一拼装逻辑。
     * </p>
     *
     * @return 全部传感器元数据列表
     */
    @InnerAuth
    @GetMapping("/sensor/list")
    public R<List<SensorMetaDTO>> listAllSensors() {
        List<SensorMetaDTO> result = sensorService.list().stream()
                .map(this::toSensorMeta)
                .toList();
        return R.ok(result);
    }

    /**
     * 实体转元数据 DTO 的统一拼装(listSensorMetaByIds / listAllSensors 共用)
     */
    private SensorMetaDTO toSensorMeta(EquipmentSensor sensor) {
        SensorMetaDTO dto = new SensorMetaDTO();
        dto.setId(sensor.getId());
        dto.setSensorCode(sensor.getSensorCode());
        dto.setSensorName(sensor.getSensorName());
        dto.setUnit(sensor.getSensorUnit());
        dto.setEquipmentId(sensor.getEquipmentId());
        dto.setEquipmentName(sensor.getEquipmentName());
        return dto;
    }
}
