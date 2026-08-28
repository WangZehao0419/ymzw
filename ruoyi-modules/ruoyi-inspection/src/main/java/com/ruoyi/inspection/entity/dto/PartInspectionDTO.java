package com.ruoyi.inspection.entity.dto;

import lombok.Data;

import java.util.Map;

/**
 * AI检测请求参数
 */
@Data
public class PartInspectionDTO {

    /**
     * 零件ID（可选）
     */
    private Long partId;

    /**
     * 零件类型
     */
    private String partType;

    /**
     * 待检测零件参数
     */
    private Map<String, Object> partData;

    /**
     * 检测标准参数
     */
    private Map<String, Object> standardData;
}
