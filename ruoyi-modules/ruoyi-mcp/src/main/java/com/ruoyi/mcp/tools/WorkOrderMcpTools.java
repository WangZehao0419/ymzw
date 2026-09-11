package com.ruoyi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.alert.api.RemoteWorkOrderService;
import com.ruoyi.alert.api.domain.WorkOrderCreateDTO;
import com.ruoyi.alert.api.domain.WorkOrderDTO;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 维保工单全生命周期 MCP 工具集
 * <p>
 * 将 ruoyi-alert 的 /inner/work-orders 内部接口与 ruoyi-equipment 的传感器清单
 * 封装为标准 MCP 工具，供对话智能体与外部 MCP 客户端以自然语言驱动工单流转。
 * 所有工具返回紧凑 JSON 字符串：成功统一带 success=true，失败带 success=false
 * 与中文 error 原因，避免把 Java 堆栈直接抛给 LLM 造成上下文污染。
 * </p>
 * <p>
 * 工具由 spring-ai-mcp-annotations 的 BeanPostProcessor 扫描注册
 * （@ConditionalOnProperty matchIfMissing=true，默认开启），无需额外启用注解。
 * </p>
 *
 * @author ruoyi
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderMcpTools {

    /** 下游 Feign 契约（alert：工单建单/流转） */
    private final RemoteWorkOrderService remoteWorkOrderService;

    /** 下游 Feign 契约（equipment：传感器清单） */
    private final RemoteEquipmentService remoteEquipmentService;

    /** FeignException 消息可能内嵌整段响应体，超长会撑爆 LLM 上下文，统一截断 */
    private static final int ERROR_MSG_MAX_LEN = 200;

    /**
     * 工具一：查询全部设备与传感器清单
     * <p>
     * 创建工单的必前置步骤：LLM 没有设备/传感器的先验知识，
     * 不先取清单就无法正确填 equipmentId/sensorId 等外键参数。
     * 按设备分组是为了让 LLM 一次调用即可建立"设备-传感器"归属关系。
     * </p>
     */
    @McpTool(description = "查询全部设备与传感器清单。创建工单前必须先调用本工具获取设备ID/名称与传感器ID/名称/编码。返回按设备分组的传感器列表")
    public String listEquipmentSensors() {
        try {
            R<List<SensorMetaDTO>> result = remoteEquipmentService.listAllSensors(SecurityConstants.INNER);
            if (R.isError(result)) {
                return fail("查询传感器清单失败：" + result.getMsg());
            }
            // LinkedHashMap 保证输出顺序稳定，便于 LLM 对照与用户阅读
            Map<String, JSONObject> byEquipment = new LinkedHashMap<>();
            for (SensorMetaDTO sensor : result.getData()) {
                String key = sensor.getEquipmentId() + "|" + sensor.getEquipmentName();
                JSONObject equipment = byEquipment.get(key);
                if (equipment == null) {
                    equipment = new JSONObject(new LinkedHashMap<>());
                    equipment.put("equipmentId", sensor.getEquipmentId());
                    equipment.put("equipmentName", sensor.getEquipmentName());
                    equipment.put("sensors", new JSONArray());
                    byEquipment.put(key, equipment);
                }
                JSONObject s = new JSONObject(new LinkedHashMap<>());
                s.put("id", sensor.getId());
                s.put("sensorCode", sensor.getSensorCode());
                s.put("sensorName", sensor.getSensorName());
                s.put("unit", sensor.getUnit());
                equipment.getJSONArray("sensors").add(s);
            }
            JSONObject payload = new JSONObject(new LinkedHashMap<>());
            payload.put("success", true);
            payload.put("equipments", new JSONArray(byEquipment.values()));
            return payload.toJSONString();
        } catch (Exception e) {
            log.warn("MCP工具 listEquipmentSensors 调用下游异常", e);
            return fail("设备服务调用异常：" + e.getMessage());
        }
    }

    /**
     * 工具二：手动创建维保工单
     * <p>
     * 必填参数由 alert 侧 Service 统一裁决（契约 DTO 刻意不带 validation），
     * 本工具只透传，失败原因以中文 msg 回传。
     * </p>
     */
    @McpTool(description = "创建维保工单。支持故障维修与预防维护两种类型，创建前应先调用 listEquipmentSensors 获取设备与传感器信息")
    public String createWorkOrder(
            @McpToolParam(description = "工单类型，故障维修或预防维护，必填") String orderType,
            @McpToolParam(description = "设备ID，必填，正整数") Integer equipmentId,
            @McpToolParam(description = "设备名称，必填") String equipmentName,
            @McpToolParam(required = false, description = "传感器ID，可选，预防维护可不填") Integer sensorId,
            @McpToolParam(required = false, description = "传感器名称，可选") String sensorName,
            @McpToolParam(required = false, description = "告警级别 WARNING/IMPORTANT/SEVERE/CRITICAL，可选，默认WARNING") String alertLevel,
            @McpToolParam(description = "工单描述，必填") String description,
            @McpToolParam(required = false, description = "处理人用户ID，可选") Long handler,
            @McpToolParam(required = false, description = "处理人姓名，可选") String handlerName) {
        try {
            WorkOrderCreateDTO cmd = new WorkOrderCreateDTO();
            cmd.setOrderType(orderType);
            cmd.setEquipmentId(equipmentId);
            cmd.setEquipmentName(equipmentName);
            cmd.setSensorId(sensorId);
            cmd.setSensorName(sensorName);
            cmd.setAlertLevel(alertLevel);
            cmd.setDescription(description);
            cmd.setHandler(handler);
            cmd.setHandlerName(handlerName);
            R<WorkOrderDTO> result = remoteWorkOrderService.createWorkOrder(cmd, SecurityConstants.INNER);
            if (R.isError(result)) {
                return fail("创建工单失败：" + result.getMsg());
            }
            WorkOrderDTO order = result.getData();
            JSONObject payload = new JSONObject(new LinkedHashMap<>());
            payload.put("success", true);
            payload.put("orderNo", order.getOrderNo());
            payload.put("id", order.getId());
            payload.put("status", order.getStatus());
            return payload.toJSONString();
        } catch (Exception e) {
            log.warn("MCP工具 createWorkOrder 调用下游异常", e);
            return fail("告警服务调用异常：" + e.getMessage());
        }
    }

    /**
     * 工具三：分页查询工单列表
     * <p>
     * 只回摘要字段（id/orderNo/设备名/传感器名/类型/状态/创建时间）：
     * 全字段会稀释 LLM 注意力；id 必须带上，后续转派/完成/取消都以它为锚点。
     * pageWorkOrders 返回 TableDataInfo 而非 R 包裹，HTTP 层成功即业务成功，
     * 无需再判 code（alert 侧构造该对象时 code 恒为默认值）。
     * </p>
     */
    @McpTool(description = "分页查询维保工单列表，可按状态/类型过滤或按关键词检索，返回工单摘要列表")
    public String queryWorkOrders(
            @McpToolParam(required = false, description = "状态过滤 PENDING/PROCESSING/COMPLETED/CANCELLED") String status,
            @McpToolParam(required = false, description = "工单类型过滤，故障维修或预防维护") String orderType,
            @McpToolParam(required = false, description = "关键词，匹配工单号/设备名/传感器名") String keyword,
            @McpToolParam(required = false, description = "页码，默认 1") Integer page,
            @McpToolParam(required = false, description = "每页条数，默认 10") Integer size) {
        try {
            // LLM 可能不传分页参数或传非法值，收敛到安全默认
            long pageNo = (page == null || page < 1) ? 1 : page;
            long pageSize = (size == null || size < 1) ? 10 : size;
            TableDataInfo result = remoteWorkOrderService.pageWorkOrders(pageNo, pageSize,
                    status, orderType, keyword, SecurityConstants.INNER);
            JSONArray orders = new JSONArray();
            if (result.getRows() != null) {
                for (Object row : result.getRows()) {
                    // Feign 反序列化后 rows 元素是 LinkedHashMap，经 fastjson2 转 JSONObject 取字段
                    JSONObject o = (JSONObject) JSON.toJSON(row);
                    JSONObject brief = new JSONObject(new LinkedHashMap<>());
                    brief.put("id", o.get("id"));
                    brief.put("orderNo", o.get("orderNo"));
                    brief.put("equipmentName", o.get("equipmentName"));
                    brief.put("sensorName", o.get("sensorName"));
                    brief.put("orderType", o.get("orderType"));
                    brief.put("status", o.get("status"));
                    brief.put("createTime", o.get("createTime"));
                    orders.add(brief);
                }
            }
            JSONObject payload = new JSONObject(new LinkedHashMap<>());
            payload.put("success", true);
            payload.put("total", result.getTotal());
            payload.put("orders", orders);
            return payload.toJSONString();
        } catch (Exception e) {
            log.warn("MCP工具 queryWorkOrders 调用下游异常", e);
            return fail("告警服务调用异常：" + e.getMessage());
        }
    }

    /**
     * 工具四：转派工单处理人
     */
    @McpTool(description = "转派维保工单给新的处理人，工单须为待处理或处理中状态")
    public String assignWorkOrder(
            @McpToolParam(description = "工单ID，必填") Long orderId,
            @McpToolParam(description = "新处理人用户ID，必填") Long handler,
            @McpToolParam(description = "处理人姓名，必填") String handlerName) {
        try {
            R<Void> result = remoteWorkOrderService.assignWorkOrder(orderId,
                    Map.of("handler", handler, "handlerName", handlerName), SecurityConstants.INNER);
            if (R.isError(result)) {
                return fail("转派工单失败：" + result.getMsg());
            }
            return success("工单已转派给 " + handlerName);
        } catch (Exception e) {
            log.warn("MCP工具 assignWorkOrder 调用下游异常", e);
            return fail("告警服务调用异常：" + e.getMessage());
        }
    }

    /**
     * 工具五：完成工单（联动设备退化复位）
     * <p>
     * 完成与复位是两个独立成败维度：复位失败时必须把 resetSuccess/resetMessage
     * 透出给 LLM，提示用户人工补一次复位，不能简单报成功。
     * </p>
     */
    @McpTool(description = "完成维保工单并联动下发设备退化复位指令，返回复位结果")
    public String completeWorkOrder(
            @McpToolParam(description = "工单ID，必填") Long orderId,
            @McpToolParam(description = "处理结果说明，必填") String handleRemark) {
        try {
            R<Map<String, Object>> result = remoteWorkOrderService.completeWorkOrder(orderId,
                    Map.of("handleRemark", handleRemark), SecurityConstants.INNER);
            if (R.isError(result)) {
                return fail("完成工单失败：" + result.getMsg());
            }
            Map<String, Object> data = result.getData();
            JSONObject payload = new JSONObject(new LinkedHashMap<>());
            payload.put("success", true);
            payload.put("message", "工单已完成");
            payload.put("resetSuccess", data != null ? data.get("resetSuccess") : null);
            payload.put("resetMessage", data != null ? data.get("resetMessage") : null);
            return payload.toJSONString();
        } catch (Exception e) {
            log.warn("MCP工具 completeWorkOrder 调用下游异常", e);
            return fail("告警服务调用异常：" + e.getMessage());
        }
    }

    /**
     * 工具六：取消工单（不触发设备复位）
     */
    @McpTool(description = "取消维保工单，须填写取消原因，取消不触发设备复位")
    public String cancelWorkOrder(
            @McpToolParam(description = "工单ID，必填") Long orderId,
            @McpToolParam(description = "取消原因，必填") String reason) {
        try {
            R<Void> result = remoteWorkOrderService.cancelWorkOrder(orderId,
                    Map.of("reason", reason), SecurityConstants.INNER);
            if (R.isError(result)) {
                return fail("取消工单失败：" + result.getMsg());
            }
            return success("工单已取消");
        } catch (Exception e) {
            log.warn("MCP工具 cancelWorkOrder 调用下游异常", e);
            return fail("告警服务调用异常：" + e.getMessage());
        }
    }

    /** 成功统一返回体 */
    private String success(String message) {
        JSONObject payload = new JSONObject(new LinkedHashMap<>());
        payload.put("success", true);
        payload.put("message", message);
        return payload.toJSONString();
    }

    /** 失败统一返回体：异常消息截断，防止 Feign 响应体撑爆 LLM 上下文 */
    private String fail(String message) {
        JSONObject payload = new JSONObject(new LinkedHashMap<>());
        payload.put("success", false);
        payload.put("error", truncate(message));
        return payload.toJSONString();
    }

    /** 截断超长错误消息（含 FeignException 内嵌的整段响应体） */
    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        return message.length() <= ERROR_MSG_MAX_LEN ? message
                : message.substring(0, ERROR_MSG_MAX_LEN) + "...";
    }
}
