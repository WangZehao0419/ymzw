package com.ruoyi.alert.api.domain;

import lombok.Data;

/**
 * 手动创建维保工单命令（Feign 契约侧）
 * <p>
 * 供 ruoyi-mcp 等外部服务经 RemoteWorkOrderService 组装建单参数。
 * 刻意不带 validation 注解（与 ruoyi-api-equipment 的契约 DTO 风格一致）：
 * 必填裁决统一收敛在 alert 侧 Service，以中文 ServiceException 消息回传调用方，
 * 避免契约层与服务端两套校验规则各自漂移。
 * </p>
 *
 * @author smartartisan
 */
@Data
public class WorkOrderCreateDTO {

    /** 工单类型：故障维修/预防维护（必填） */
    private String orderType;

    /** 设备ID（必填） */
    private Integer equipmentId;

    /** 设备名称（必填，建单时快照，保证告警/设备数据清理后工单仍可读） */
    private String equipmentName;

    /** 传感器ID（可选，设备级工单不填） */
    private Integer sensorId;

    /** 传感器名称（可选，与 sensorId 配套的快照字段） */
    private String sensorName;

    /** 告警级别（可选，缺省 WARNING） */
    private String alertLevel;

    /** 工单描述（必填，故障现象/维修内容） */
    private String description;

    /** 处理人用户ID（可选，未指定时工单挂起待转派） */
    private Long handler;

    /** 处理人姓名（可选，与 handler 配套） */
    private String handlerName;
}
