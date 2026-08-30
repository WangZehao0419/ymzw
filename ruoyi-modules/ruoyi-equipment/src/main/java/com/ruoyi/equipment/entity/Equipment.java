package com.ruoyi.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备基础信息实体类
 * <p>
 * 用于存储设备的基本信息，包括设备编号、名称、型号、车间、状态等
 * </p>
 *
 * @author smartartisan
 */
@Data
@TableName("equipment")
public class Equipment {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 设备编号
     */
    @TableField("equipment_no")
    private String equipmentNo;

    /**
     * 设备名称
     */
    @TableField("equipment_name")
    private String equipmentName;

    /**
     * 设备型号ID
     */
    @TableField("equipment_model_id")
    private Integer equipmentModelId;

    /**
     * 设备型号名称
     */
    @TableField("equipment_model_name")
    private String equipmentModelName;

    /**
     * 所属车间ID
     */
    @TableField("workshop_id")
    private Integer workshopId;

    /**
     * 所属车间名称
     */
    @TableField("workshop_name")
    private String workshopName;

    /**
     * 运行状态（0-运行中，1-停机，2-维修，3-待验收）
     */
    @TableField("equipment_status")
    private String equipmentStatus;

    /**
     * 安装日期
     */
    @TableField("equipment_install_date")
    private LocalDate equipmentInstallDate;

    /**
     * 负责人ID
     */
    // updateById 默认忽略 null 字段，导致编辑时清空负责人后 ID 残留旧值（名称空串却能更新），故强制参与 SET
    @TableField(value = "equipment_user_id", updateStrategy = FieldStrategy.ALWAYS)
    private Integer equipmentUserId;

    /**
     * 负责人名称
     */
    // 与负责人ID成对处理，保证两字段清空/赋值语义一致
    @TableField(value = "equipment_user_name", updateStrategy = FieldStrategy.ALWAYS)
    private String equipmentUserName;

    /**
     * 备注
     */
    @TableField("equipment_remark")
    private String equipmentRemark;

    /** 孪生布局X(米,地面世界坐标,中心原点,NULL表示未摆放) */
    @TableField("layout_x")
    private Double layoutX;

    /** 孪生布局Y(米,地面世界坐标,中心原点,NULL表示未摆放) */
    @TableField("layout_y")
    private Double layoutY;

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
