package com.ruoyi.alert.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警规则实体(L1 阈值规则)
 *
 * @author smartartisan
 */
@Data
@TableName("alert_rule")
public class AlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 传感器ID(主键标识,匹配与关联用) */
    @TableField("sensor_id")
    private Integer sensorId;

    /** 传感器编号(展示兼容,由 sensorId 经 Feign 查询回填) */
    @TableField("sensor_code")
    private String sensorCode;

    /** 阈值上限 */
    @TableField("upper_limit")
    private Double upperLimit;

    /** 阈值下限 */
    @TableField("lower_limit")
    private Double lowerLimit;

    /** 持续越界点数(防抖) */
    @TableField("sustain_points")
    private Integer sustainPoints;

    /** 静默时段开始 HH:mm */
    @TableField("silence_start")
    private String silenceStart;

    /** 静默时段结束 HH:mm */
    @TableField("silence_end")
    private String silenceEnd;

    /** 命中告警等级: NORMAL/WARNING/IMPORTANT/SEVERE/CRITICAL */
    @TableField("level")
    private String level;

    /** 是否启用 0-禁用 1-启用 */
    @TableField("enabled")
    private Integer enabled;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 传感器名称(非表字段,列表展示用,查询后按 sensorId 经 Feign 查设备服务回填) */
    @TableField(exist = false)
    private String sensorName;
}
