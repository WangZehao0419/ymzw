package com.ruoyi.alert.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预测性维护结果实体(按传感器一行的最新快照)
 *
 * @author smartartisan
 */
@Data
@TableName("predict_result")
public class PredictResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("sensor_code")
    private String sensorCode;

    @TableField("equipment_id")
    private Integer equipmentId;

    /** 预测状态: NORMAL/DEGRADING/BREACHED(PredictStateMachine 维护) */
    @TableField("status")
    private String status;

    @TableField("health_score")
    private Double healthScore;

    /** 趋势斜率(WLS 拟合) */
    @TableField("slope")
    private Double slope;

    /** 预计越界点数(相对当前;t1 超外推时域/已触线时沿用旧值) */
    @TableField("t1_points")
    private Integer t1Points;

    /** 预测越限时间(趋势外推) */
    @TableField("predicted_breach_time")
    private LocalDateTime predictedBreachTime;

    /** 劣化起点时间 */
    @TableField("onset_time")
    private LocalDateTime onsetTime;

    /** 趋势置信带 JSON */
    @TableField("band_json")
    private String bandJson;

    /** AI 预测可用: 0-不可用 1-可用 */
    @TableField("ai_available")
    private Integer aiAvailable;

    @TableField("ai_p10_json")
    private String aiP10Json;

    @TableField("ai_p50_json")
    private String aiP50Json;

    @TableField("ai_p90_json")
    private String aiP90Json;

    /** AI 与统计外激发散比 */
    @TableField("divergence_ratio")
    private Double divergenceRatio;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
