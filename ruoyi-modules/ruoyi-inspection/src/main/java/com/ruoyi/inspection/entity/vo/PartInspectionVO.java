package com.ruoyi.inspection.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 零件检测视图对象
 * <p>
 * 用于前端展示零件检测相关信息
 * </p>
 *
 * @author smartartisan
 */
@Data
public class PartInspectionVO {

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
     * 零件类型编码
     */
    private String partType;

    /**
     * 检测标准参数（JSON格式）
     */
    private String standardParameters;

    /**
     * 零件参数（JSON格式）
     */
    private String partParameters;

    /**
     * 合格标志（true-合格，false-不合格）
     */
    private Boolean qualifiedFlag;

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
     * 检测标志（true-已检测，false-未检测）
     */
    private Boolean inspectionFlag;
}
