package com.ruoyi.alert.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警处置日志实体
 *
 * @author smartartisan
 */
@Data
@TableName("alert_action_log")
public class AlertActionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联告警事件ID */
    @TableField("alert_id")
    private Long alertId;

    /** 动作: ACK/RESOLVE/ESCALATE */
    @TableField("action")
    private String action;

    @TableField("operator")
    private String operator;

    @TableField("remark")
    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
