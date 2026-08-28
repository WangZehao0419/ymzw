package com.ruoyi.equipment.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 设备信息数据传输对象
 * <p>
 * 用于接收前端提交的设备新增和更新请求参数
 * </p>
 *
 * @author smartartisan
 */
@Data
public class EquipmentDTO {

    /**
     * 主键ID（更新时必填）
     */
    private Integer id;

    /**
     * 设备编号
     */
    @NotBlank(message = "设备编号不能为空")
    private String equipmentNo;

    /**
     * 设备名称
     */
    @NotBlank(message = "设备名称不能为空")
    private String equipmentName;

    /**
     * 设备型号ID
     */
    private Integer equipmentModelId;

    /**
     * 设备型号名称
     */
    private String equipmentModelName;

    /**
     * 所属车间ID
     */
    private Integer workshopId;

    /**
     * 所属车间名称
     */
    private String workshopName;

    /**
     * 运行状态（0-运行中，1-停机，2-维修，3-待验收）
     */
    private String equipmentStatus;

    /**
     * 安装日期
     */
    private LocalDate equipmentInstallDate;

    /**
     * 负责人ID
     */
    private Integer equipmentUserId;

    /**
     * 负责人名称
     */
    private String equipmentUserName;

    /**
     * 备注
     */
    private String equipmentRemark;
}
