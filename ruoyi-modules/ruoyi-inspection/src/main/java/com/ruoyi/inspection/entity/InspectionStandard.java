package com.ruoyi.inspection.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检测标准实体类
 * <p>
 * 用于存储零件检测的标准参数和规范
 * </p>
 *
 * @author smartartisan
 */
@Data
@TableName("inspection_standards")
public class InspectionStandard {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 零件类型
     */
    @TableField("part_type")
    private String partType;

    /**
     * 标准名称
     */
    @TableField("standard_name")
    private String standardName;

    /**
     * 标准参数（JSON格式）
     */
    @TableField("standard_parameters")
    private String standardParameters;

    /**
     * 描述
     */
    @TableField("description")
    private String description;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
