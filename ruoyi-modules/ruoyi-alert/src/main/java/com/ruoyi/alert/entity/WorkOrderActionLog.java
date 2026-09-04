package com.ruoyi.alert.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单流转记录实体
 *
 * @author smartartisan
 */
@Data
@TableName("work_order_action_log")
public class WorkOrderActionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单ID */
    @TableField("order_id")
    private Long orderId;

    /** 工单编号(冗余,免联表直查) */
    @TableField("order_no")
    private String orderNo;

    /** 动作: CREATE/ASSIGN/START/COMPLETE/CANCEL */
    @TableField("action")
    private String action;

    /** 操作人(系统自动生成为 system) */
    @TableField("operator")
    private String operator;

    /** 流转详情(完整中文句子,含派单对象/转派方向/取消原因等) */
    @TableField("detail")
    private String detail;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
