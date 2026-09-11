package com.ruoyi.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI诊断报告实体类
 * <p>
 * 诊断智能体生成的设备诊断报告落库记录，对应 diagnosis_record 表。
 * report 为 LLM 生成的 Markdown 全文，历史接口按设备/传感器分页回查，
 * 供前端展示诊断历史与追溯生成证据。
 * </p>
 *
 * @author smartartisan
 */
@Data
@TableName("diagnosis_record")
public class DiagnosisRecord {

    /**
     * 诊断记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 设备ID
     */
    @TableField("equipment_id")
    private Integer equipmentId;

    /**
     * 传感器编号（如 TH-001）
     */
    @TableField("sensor_code")
    private String sensorCode;

    /**
     * 诊断报告（Markdown 全文）
     */
    @TableField("report")
    private String report;

    /**
     * 创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;
}
