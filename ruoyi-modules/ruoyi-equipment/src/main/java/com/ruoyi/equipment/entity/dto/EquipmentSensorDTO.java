package com.ruoyi.equipment.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设备传感器数据传输对象
 * <p>
 * 用于接收前端提交的传感器新增和更新请求参数
 * </p>
 *
 * @author smartartisan
 */
@Data
public class EquipmentSensorDTO {

    /**
     * 主键ID（更新时必填）
     */
    private Integer id;

    /**
     * 传感器参数编号（如TH-001）
     */
    @NotBlank(message = "传感器编号不能为空")
    private String sensorCode;

    /**
     * 传感器参数名称（如主轴转速、主轴温度）
     */
    @NotBlank(message = "传感器名称不能为空")
    private String sensorName;

    /**
     * 传感器参数单位（如rpm、°C、mm/s）
     */
    @NotBlank(message = "传感器单位不能为空")
    private String sensorUnit;

    /**
     * 传感器状态（0-禁用，1-启用）
     */
    private Integer sensorStatus;

    /**
     * 设备型号id
     */
    @NotNull(message = "设备型号ID不能为空")
    private Integer equipmentId;

    /**
     * 设备型号名称
     */
    @NotBlank(message = "设备型号名称不能为空")
    private String equipmentName;
}
