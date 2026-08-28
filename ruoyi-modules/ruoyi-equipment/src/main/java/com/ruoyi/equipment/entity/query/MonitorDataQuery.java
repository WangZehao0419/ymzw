package com.ruoyi.equipment.entity.query;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监测数据分页查询参数
 * <p>
 * 用于监测数据列表的条件筛选和分页查询，支持按传感器、设备、时间范围等条件筛选
 * </p>
 *
 * @author smartartisan
 */
@Data
public class MonitorDataQuery {

    /**
     * 当前页码
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 10;

    /**
     * 传感器ID
     */
    private Integer sensorId;

    /**
     * 设备ID
     */
    private Integer equipmentId;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;
}
