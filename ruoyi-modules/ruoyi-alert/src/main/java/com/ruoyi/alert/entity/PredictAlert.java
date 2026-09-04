package com.ruoyi.alert.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预测告警实体(独立表 predict_alert)
 * <p>
 * 字段/列名与 alert_event 完全对齐(D2): 前端预测页字段零改动。
 * 用户视角"告警记录=已发生的阈值告警",PREDICT 预测告警(将发生)
 * 独立存储,从数据层面与告警记录彻底分开(D1)。
 * </p>
 *
 * @author smartartisan
 */
@Data
@TableName("predict_alert")
public class PredictAlert {

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

    /** 告警类型: 本表恒为 PREDICT(列保留对齐 alert_event) */
    @TableField("alert_type")
    private String alertType;

    /** 告警等级: NORMAL/WARNING/IMPORTANT/SEVERE/CRITICAL */
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

    /** 预计越界时刻(PREDICT 类型专用) */
    @TableField("predicted_breach_time")
    private LocalDateTime predictedBreachTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 从 AlertEvent 全字段拷贝构造(含 id)
     * <p>
     * 含 id 是为了状态机 updateById 场景: 句柄 id 直接命中 predict_alert。
     * 插入场景须在 insert 前显式 setId(null)——MyBatis-Plus 对非 null id
     * 会带着该值插入而非走自增主键,不清空会导致主键冲突或脏引用。
     * </p>
     */
    public static PredictAlert from(AlertEvent src) {
        PredictAlert pa = new PredictAlert();
        pa.setId(src.getId());
        pa.setEquipmentId(src.getEquipmentId());
        pa.setEquipmentName(src.getEquipmentName());
        pa.setSensorId(src.getSensorId());
        pa.setSensorCode(src.getSensorCode());
        pa.setSensorName(src.getSensorName());
        pa.setAlertType(src.getAlertType());
        pa.setAlertLevel(src.getAlertLevel());
        pa.setAlertStatus(src.getAlertStatus());
        pa.setEvidence(src.getEvidence());
        pa.setSummary(src.getSummary());
        pa.setRootCause(src.getRootCause());
        pa.setSuggestion(src.getSuggestion());
        pa.setSensorValue(src.getSensorValue());
        pa.setTriggerTime(src.getTriggerTime());
        pa.setResolveTime(src.getResolveTime());
        pa.setEscalationCount(src.getEscalationCount());
        pa.setPredictedBreachTime(src.getPredictedBreachTime());
        pa.setCreateTime(src.getCreateTime());
        return pa;
    }
}
