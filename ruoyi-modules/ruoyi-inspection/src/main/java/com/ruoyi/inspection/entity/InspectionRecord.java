package com.ruoyi.inspection.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检测记录实体类
 * <p>
 * 用于存储每次检测的详细记录
 * </p>
 *
 * @author smartartisan
 */
@Data
@TableName("inspection_records")
public class InspectionRecord {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 零件ID
     */
    @TableField("part_id")
    private Long partId;

    /**
     * 检测时间
     */
    @TableField("inspection_time")
    private LocalDateTime inspectionTime;

    /**
     * 是否合格（true-合格，false-不合格）
     */
    @TableField("is_qualified")
    private Boolean isQualified;

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
     * 检测人员
     */
    @TableField("inspector")
    private String inspector;
}
