package com.ruoyi.alert.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警事件实体
 *
 * @author smartartisan
 */
@Data
@TableName("alert_event")
public class AlertEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("equipment_id")
    private Integer equipmentId;

    @TableField("equipment_name")
    private String equipmentName;

    @TableField("sensor_id")
    private Integer sensorId;

    @TableField("sensor_code")
    private String sensorCode;

    @TableField("sensor_name")
    private String sensorName;

    /** 告警类型: RULE/STAT/PREDICT */
    @TableField("alert_type")
    private String alertType;

    /** 告警等级: NORMAL/WARNING/SEVERE */
    @TableField("alert_level")
    private String alertLevel;

    /** 告警状态: FIRING/ACKED/RESOLVED */
    @TableField("alert_status")
    private String alertStatus;

    /** 命中证据 JSON 字符串 {layer,value,threshold,sustain} */
    @TableField("evidence")
    private String evidence;

    /** 告警摘要(L3 回填) */
    @TableField("summary")
    private String summary;

    /** 根因(L3 回填) */
    @TableField("root_cause")
    private String rootCause;

    /** 处置建议(L3 回填) */
    @TableField("suggestion")
    private String suggestion;

    @TableField("sensor_value")
    private Double sensorValue;

    @TableField("trigger_time")
    private LocalDateTime triggerTime;

    @TableField("resolve_time")
    private LocalDateTime resolveTime;

    @TableField("escalation_count")
    private Integer escalationCount;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
