package com.ruoyi.equipment.api.domain;

import lombok.Data;

/**
 * 传感器元数据传输对象，用于跨模块元数据传输，只含告警侧必需字段
 *
 * @author smartartisan
 */
@Data
public class SensorMetaDTO
{
    /**
     * 传感器ID
     */
    private Integer id;

    /**
     * 传感器编码
     */
    private String sensorCode;

    /**
     * 传感器名称
     */
    private String sensorName;

    /**
     * 传感器参数单位(rpm/°C/mm/s)
     */
    private String unit;

    /**
     * 所属设备ID
     */
    private Integer equipmentId;

    /**
     * 所属设备名称
     */
    private String equipmentName;
}
