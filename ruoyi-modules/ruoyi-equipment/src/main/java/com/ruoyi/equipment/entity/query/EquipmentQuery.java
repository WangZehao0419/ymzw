package com.ruoyi.equipment.entity.query;

import lombok.Data;

/**
 * 设备分页查询参数
 * <p>
 * 用于设备列表的条件筛选和分页查询
 * </p>
 *
 * @author smartartisan
 */
@Data
public class EquipmentQuery {

    /**
     * 当前页码
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 10;

    /**
     * 设备名称（模糊查询）
     */
    private String equipmentName;

    /**
     * 设备编号（模糊查询）
     */
    private String equipmentNo;

    /**
     * 运行状态（0-运行中，1-停机，2-维修，3-待验收）
     */
    private String equipmentStatus;

    /**
     * 所属车间ID
     */
    private Integer workshopId;
}
