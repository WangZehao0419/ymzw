package com.ruoyi.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备传感器实体类
 * <p>
 * 用于存储设备传感器参数信息，包括传感器编号、名称、单位、状态等
 * </p>
 *
 * @author smartartisan
 */
@Data
@TableName("equipment_sensor")
public class EquipmentSensor {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 传感器参数编号（如TH-001）
     */
    @TableField("sensor_code")
    private String sensorCode;

    /**
     * 传感器参数名称（如主轴转速、主轴温度）
     */
    @TableField("sensor_name")
    private String sensorName;

    /**
     * 传感器参数单位（如rpm、°C、mm/s）
     */
    @TableField("sensor_unit")
    private String sensorUnit;

    /**
     * 传感器状态（0-禁用，1-启用）
     */
    @TableField("sensor_status")
    private Integer sensorStatus;

    /**
     * 设备型号id
     */
    @TableField("equipment_id")
    private Integer equipmentId;

    /**
     * 设备型号名称
     */
    @TableField("equipment_name")
    private String equipmentName;

    /**
     * 记录创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 记录修改时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    @TableField("create_user")
    private String createUser;

    /**
     * 修改人
     */
    @TableField("update_user")
    private String updateUser;

    /**
     * 删除状态（0-未删除，1-已删除）
     */
    @TableLogic
    @TableField("delete_flag")
    private Integer deleteFlag;
}
