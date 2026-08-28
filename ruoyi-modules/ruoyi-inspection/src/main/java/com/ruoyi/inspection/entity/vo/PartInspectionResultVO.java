package com.ruoyi.inspection.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI检测返回结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartInspectionResultVO {

    /**
     * 状态码：0-不合格，1-合格，2-不存在
     */
    private Integer inspectionStatus;

    /**
     * 检测详情
     */
    private String inspectionDetails;

    /**
     * 检测建议
     */
    private String inspectionSuggestion;

    /**
     * 零件ID
     */
    private Long partId;
}
