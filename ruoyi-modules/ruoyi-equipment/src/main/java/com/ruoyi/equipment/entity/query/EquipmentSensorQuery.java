package com.ruoyi.equipment.entity.query;

import lombok.Data;

/**
 * 设备传感器分页查询参数
 * <p>
 * 用于传感器列表的条件筛选和分页查询
 * </p>
 *
 * @author smartartisan
 */
@Data
public class EquipmentSensorQuery {

    /**
     * 当前页码
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 10;

    /**
     * 传感器参数编号（模糊查询）
     */
    private String sensorCode;

    /**
     * 传感器参数名称（模糊查询）
     */
    private String sensorName;

    /**
     * 设备型号ID
     */
    private Integer equipmentId;

    /**
     * 传感器状态（0-禁用，1-启用）
     */
    private Integer sensorStatus;
}
