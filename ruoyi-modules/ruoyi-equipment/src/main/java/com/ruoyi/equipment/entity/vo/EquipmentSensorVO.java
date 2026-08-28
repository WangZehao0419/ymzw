package com.ruoyi.equipment.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备传感器视图对象
 * <p>
 * 用于前端展示传感器详情相关信息
 * </p>
 *
 * @author smartartisan
 */
@Data
public class EquipmentSensorVO {

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 传感器参数编号（如TH-001）
     */
    private String sensorCode;

    /**
     * 传感器参数名称（如主轴转速、主轴温度）
     */
    private String sensorName;

    /**
     * 传感器参数单位（如rpm、°C、mm/s）
     */
    private String sensorUnit;

    /**
     * 传感器状态（0-禁用，1-启用）
     */
    private Integer sensorStatus;

    /**
     * 传感器状态描述
     */
    private String sensorStatusDesc;

    /**
     * 设备型号id
     */
    private Integer equipmentId;

    /**
     * 设备型号名称
     */
    private String equipmentName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    private String createUser;

    /**
     * 修改人
     */
    private String updateUser;
}
