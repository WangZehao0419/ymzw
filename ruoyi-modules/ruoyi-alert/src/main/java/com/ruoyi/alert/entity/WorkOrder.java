package com.ruoyi.alert.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维保工单实体
 *
 * @author smartartisan
 */
@Data
@TableName("work_order")
public class WorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单编号 WO+yyyyMMddHHmmss+3位随机 */
    @TableField("order_no")
    private String orderNo;

    /** 工单类型: 故障维修/预防维护 */
    @TableField("order_type")
    private String orderType;

    /** 关联业务ID(order_type路由:故障维修→alert_event,预防维护→maintenance_plan) */
    @TableField("related_id")
    private Long relatedId;

    @TableField("equipment_id")
    private Integer equipmentId;

    @TableField("equipment_name")
    private String equipmentName;

    @TableField("sensor_id")
    private Integer sensorId;

    @TableField("sensor_name")
    private String sensorName;

    /** 级别: WARNING/IMPORTANT/SEVERE/CRITICAL */
    @TableField("alert_level")
    private String alertLevel;

    /** 工单内容(自动生成) */
    @TableField("description")
    private String description;

    /** 状态: PENDING/PROCESSING/COMPLETED/CANCELLED */
    @TableField("status")
    private String status;

    /** 处理人用户ID(关联sys_user,工单生成时为设备负责人,未绑定为NULL待转派) */
    @TableField("handler")
    private Long handler;

    /** 处理人姓名 */
    @TableField("handler_name")
    private String handlerName;

    /** 处理结果说明 */
    @TableField("handle_remark")
    private String handleRemark;

    /** 取消原因 */
    @TableField("cancel_reason")
    private String cancelReason;

    @TableField("finish_time")
    private LocalDateTime finishTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
