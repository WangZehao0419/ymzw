package com.ruoyi.equipment.entity.query;

import lombok.Data;

/**
 * 车间分页查询参数
 * <p>
 * 用于车间列表的条件筛选和分页查询
 * </p>
 *
 * @author smartartisan
 */
@Data
public class WorkshopQuery {

    /**
     * 当前页码
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 10;

    /**
     * 车间编号（精确查询）
     */
    private String workshopNo;

    /**
     * 车间名称（模糊查询）
     */
    private String workshopName;

    /**
     * 车间状态（0-启用，1-停用）
     */
    private String workshopStatus;
}
