package com.ruoyi.alert.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 维护计划：定时任务按重复规则触发并自动生成维保工单
 *
 * @author smartartisan
 */
@Data
@TableName("maintenance_plan")
public class MaintenancePlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 计划编号 MP+yyyyMMddHHmmss+3位随机 */
    @TableField("plan_no")
    private String planNo;

    @TableField("plan_name")
    private String planName;

    /** 维保对象(设备级) */
    @TableField("equipment_id")
    private Integer equipmentId;

    @TableField("equipment_name")
    private String equipmentName;

    /** 保养类型: 日常保养/一级保养/二级保养/精度校准/润滑保养 */
    @TableField("maintenance_type")
    private String maintenanceType;

    /** 维护内容说明 */
    @TableField("content")
    private String content;

    /** 重复类型: ONCE/DAILY/WEEKDAYS/MONTHLY/LEGAL_WORKDAY */
    @TableField("repeat_type")
    private String repeatType;

    /** 触发时刻 HH:mm */
    @TableField("fire_time")
    private LocalTime fireTime;

    /** MONTHLY: 每月几号(1-31) */
    @TableField("fire_day")
    private Integer fireDay;

    /** ONCE: 触发日期 */
    @TableField("fire_date")
    private LocalDate fireDate;

    /** 下次触发时间(预计算,DONE 为 NULL) */
    @TableField("next_fire_time")
    private LocalDateTime nextFireTime;

    /** 上次触发时间 */
    @TableField("last_fire_time")
    private LocalDateTime lastFireTime;

    /** 负责人用户ID(可空=生成后待指派) */
    @TableField("assignee_id")
    private Long assigneeId;

    @TableField("assignee_name")
    private String assigneeName;

    /** 状态: ENABLED/PAUSED/DONE */
    @TableField("status")
    private String status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
