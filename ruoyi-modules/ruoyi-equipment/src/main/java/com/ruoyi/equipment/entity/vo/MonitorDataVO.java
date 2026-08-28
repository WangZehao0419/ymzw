package com.ruoyi.equipment.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监测数据视图对象
 * <p>
 * 用于前端展示监测数据详情，包含监测数据及关联的传感器和设备信息
 * </p>
 *
 * @author smartartisan
 */
@Data
public class MonitorDataVO {

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 传感器ID
     */
    private Integer sensorId;

    /**
     * 传感器参数值
     */
    private Double sensorValue;

    /**
     * 传感器编号
     */
    private String sensorCode;

    /**
     * 传感器名称
     */
    private String sensorName;

    /**
     * 传感器单位
     */
    private String sensorUnit;

    /**
     * 设备ID
     */
    private Integer equipmentId;

    /**
     * 设备名称
     */
    private String equipmentName;

    /**
     * 记录创建时间
     */
    private LocalDateTime createTime;

    /**
     * 记录修改时间
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
