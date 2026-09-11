package com.ruoyi.ai.entity.query;

import lombok.Data;

/**
 * 诊断报告分页查询参数
 * <p>
 * 用于诊断历史记录的条件筛选和分页查询，equipmentId/sensorCode 均为可选过滤
 * </p>
 *
 * @author smartartisan
 */
@Data
public class DiagnosisRecordQuery {

    /**
     * 当前页码
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 10;

    /**
     * 设备ID（可选）
     */
    private Integer equipmentId;

    /**
     * 传感器编号（可选，精确匹配）
     */
    private String sensorCode;
}
