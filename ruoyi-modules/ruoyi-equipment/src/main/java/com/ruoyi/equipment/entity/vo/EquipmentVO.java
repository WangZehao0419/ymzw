package com.ruoyi.equipment.entity.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备信息视图对象
 * <p>
 * 用于前端展示设备详情相关信息
 * </p>
 *
 * @author smartartisan
 */
@Data
public class EquipmentVO {

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 设备编号
     */
    private String equipmentNo;

    /**
     * 设备名称
     */
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
