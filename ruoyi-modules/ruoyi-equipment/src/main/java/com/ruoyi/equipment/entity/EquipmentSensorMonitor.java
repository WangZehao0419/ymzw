package com.ruoyi.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备监控实体类
 * <p>
 * 用于存储设备传感器的实时监测数据，包括传感器参数值、记录时间等
 * </p>
 *
 * @author smartartisan
 */
@Data
@TableName("equipment_sensor_monitor")
public class EquipmentSensorMonitor {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 传感器id
     */
    @TableField("sensor_id")
    private Integer sensorId;

    /**
     * 传感器参数值
     */
    @TableField("sensor_value")
    private Double sensorValue;

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
