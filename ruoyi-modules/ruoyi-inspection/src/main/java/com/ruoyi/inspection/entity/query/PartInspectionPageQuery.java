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
public class PartInspectionPageQuery {

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
     * 零件代码（模糊查询）
     */
    private String partCode;

    /**
     * 检测标准ID
     */
    private Long standardId;

    /**
     * 是否已检测
     */
    private Boolean inspectionFlag;
}
