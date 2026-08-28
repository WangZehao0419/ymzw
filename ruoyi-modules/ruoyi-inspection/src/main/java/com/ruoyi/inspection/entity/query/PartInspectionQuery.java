package com.ruoyi.inspection.entity.query;

import lombok.Data;

/**
 * 零件质量检测信息分页查询参数
 * <p>
 * 用于检测信息列表的条件筛选和分页查询
 * </p>
 *
 * @author smartartisan
 */
@Data
public class PartInspectionQuery {

    /**
     * 当前页码
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 10;

    /**
     * 零件名称（模糊查询）
     */
    private String partName;

    /**
     * AI检测结果（0-不良，1-良品）
     */
    private Integer isQualified;

    /**
     * 检测标志（0-未检测，1-已检测）
     */
    private Boolean inspectionFlag;

    /**
     * 检测开始时间
     */
    private String inspectionStartTime;

    /**
     * 检测结束时间
     */
    private String inspectionEndTime;
}
