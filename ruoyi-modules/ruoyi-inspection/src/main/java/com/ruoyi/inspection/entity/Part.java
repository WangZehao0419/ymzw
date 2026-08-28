package com.ruoyi.inspection.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 零件实体类
 * <p>
 * 用于存储零件的基本信息和检测结果
 * </p>
 *
 * @author smartartisan
 */
@Data
@TableName("parts")
public class Part {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 零件名称
     */
    @TableField("part_name")
    private String partName;

    /**
     * 零件编码
     */
    @TableField("part_code")
    private String partCode;

    /**
     * 检测标准ID
     */
    @TableField("standard_id")
    private Long standardId;

    /**
     * 零件参数（JSON格式）
     */
    @TableField("parameters")
    private String parameters;

    /**
     * 是否合格（true-合格，false-不合格）
     */
    @TableField("is_qualified")
    private Boolean isQualified;

    /**
     * 检测时间
     */
    @TableField("inspection_time")
    private LocalDateTime inspectionTime;

    /**
     * 检测详情
     */
    @TableField("inspection_details")
    private String inspectionDetails;

    /**
     * 检测建议
     */
    @TableField("inspection_suggestion")
    private String inspectionSuggestion;

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

    /**
     * 删除标志（0-正常，1-已删除）
     */
    @TableLogic
    @TableField("delete_flag")
    private Boolean deleteFlag;

    /**
     * 检测标志（0-未检测，1-已检测）
     */
    @TableField("inspection_flag")
    private Boolean inspectionFlag;
}
