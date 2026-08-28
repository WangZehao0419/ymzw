package com.ruoyi.inspection.entity.query;

import lombok.Data;

/**
 * 零件分页查询参数
 * <p>
 * 用于零件列表的条件筛选和分页查询
 * </p>
 *
 * @author smartartisan
 */
@Data
public class PartQuery {

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
     * 零件编码（模糊查询）
     */
    private String partCode;

    /**
     * 是否合格（true-合格，false-不合格）
     */
    private Boolean isQualified;

    /**
     * 检测标志（true-已检测，false-未检测）
     */
    private Boolean inspectionFlag;
}
