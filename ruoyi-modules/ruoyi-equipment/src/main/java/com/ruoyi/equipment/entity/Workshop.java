package com.ruoyi.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车间基础信息实体类
 * <p>
 * 用于存储车间的基本信息，包括名称、位置、负责人、状态等
 * </p>
 *
 * @author smartartisan
 */
@Data
@TableName("workshop")
public class Workshop {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 车间名称
     */
    @TableField("workshop_name")
    private String workshopName;

    /**
     * 车间位置
     */
    @TableField("workshop_location")
    private String workshopLocation;

    /**
     * 车间负责人用户ID（逻辑关联 sys_user.user_id）
     */
    // updateById 默认忽略 null 字段，编辑时清空负责人会残留旧 ID，故强制参与 SET（与 Equipment 负责人模式一致）
    @TableField(value = "workshop_manager_id", updateStrategy = FieldStrategy.ALWAYS)
    private Integer workshopManagerId;

    /**
     * 车间负责人姓名（冗余显示，随用户选择覆盖）
     */
    // 与负责人ID成对处理，保证两字段清空/赋值语义一致
    @TableField(value = "workshop_manager", updateStrategy = FieldStrategy.ALWAYS)
    private String workshopManager;

    /**
     * 车间状态（0-启用，1-停用）
     */
    @TableField("workshop_status")
    private String workshopStatus;

    /**
     * 备注
     */
    @TableField("workshop_remark")
    private String workshopRemark;

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
