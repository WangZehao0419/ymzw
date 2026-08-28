package com.ruoyi.inspection.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 零件信息视图对象
 * <p>
 * 用于前端展示零件详情相关信息
 * </p>
 *
 * @author smartartisan
 */
@Data
public class PartVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 零件名称
     */
    private String partName;

    /**
     * 零件编码
     */
    private String partCode;

    /**
     * 检测标准ID
     */
    private Long standardId;

    /**
     * 零件参数（JSON格式）
     */
    private String parameters;

    /**
     * 是否合格（true-合格，false-不合格）
     */
    private Boolean isQualified;

    /**
     * 检测标志（true-已检测，false-未检测）
     */
    private Boolean inspectionFlag;

    /**
     * 检测时间
     */
    private LocalDateTime inspectionTime;

    /**
     * 检测详情
     */
    private String inspectionDetails;

    /**
     * 检测建议
     */
    private String inspectionSuggestion;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 删除标志（false-正常，true-已删除）
     */
    private Boolean deleteFlag;
}
