package com.ruoyi.alert.api.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维保工单返回 DTO（Feign 契约侧）
 * <p>
 * 字段与 alert 模块 WorkOrder 实体对齐，但作为跨服务契约独立于实体存在：
 * 实体演进（加列/改类型）不会直接击穿 MCP 侧反序列化，契约须变更时在此显式同步。
 * </p>
 *
 * @author smartartisan
 */
@Data
public class WorkOrderDTO {

    /** 工单ID */
    private Long id;

    /** 工单编号 WO+yyyyMMddHHmmss+3位随机 */
    private String orderNo;

    /** 工单类型：故障维修/预防维护 */
    private String orderType;

    /** 关联业务ID（order_type 路由：故障维修→alert_event，预防维护→maintenance_plan；手动单为 null） */
    private Long relatedId;

    /** 设备ID */
    private Integer equipmentId;

    /** 设备名称 */
    private String equipmentName;

    /** 传感器ID（设备级工单为 null） */
    private Integer sensorId;

    /** 传感器名称 */
    private String sensorName;

    /** 级别：WARNING/IMPORTANT/SEVERE/CRITICAL */
    private String alertLevel;

    /** 工单内容 */
    private String description;

    /** 状态：PENDING/PROCESSING/COMPLETED/CANCELLED */
    private String status;

    /** 处理人用户ID（未转派为 null） */
    private Long handler;

    /** 处理人姓名 */
    private String handlerName;

    /** 处理结果说明 */
    private String handleRemark;

    /** 取消原因 */
    private String cancelReason;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
