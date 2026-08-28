package com.ruoyi.inspection.entity.query;

import lombok.Data;

/**
 * 检测标准分页查询参数
 * <p>
 * 用于检测标准列表的条件筛选和分页查询
 * </p>
 *
 * @author smartartisan
 */
@Data
public class InspectionStandardQuery {

    /**
     * 当前页码
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 10;

    /**
     * 零件类型（模糊查询）
     */
    private String partType;

    /**
     * 标准名称（模糊查询）
     */
    private String standardName;
}
