package com.ruoyi.alert.api;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.ruoyi.alert.api.domain.WorkOrderCreateDTO;
import com.ruoyi.alert.api.domain.WorkOrderDTO;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.constant.ServiceNameConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.page.TableDataInfo;

/**
 * 维保工单内部服务
 * <p>
 * 供 ruoyi-mcp（独立 MCP 服务）经 OpenFeign 手动建单与流转工单，
 * 端点由 alert 侧 @InnerAuth 保护，仅限携带内部凭证的服务间调用。
 * </p>
 * <p>
 * 不配 fallback/fallbackFactory：与 RemoteAiService 同口径——工单创建/流转是
 * 强结果动作，调用失败须让 MCP 侧直接感知并告知用户；若降级返回假成功，
 * 会掩盖建单失败造成"用户以为建了单、实际没有"的静默丢失。
 * </p>
 *
 * @author smartartisan
 */
@FeignClient(contextId = "remoteWorkOrderService", value = ServiceNameConstants.ALERT_SERVICE)
public interface RemoteWorkOrderService {

    /**
     * 手动创建维保工单
     *
     * @param cmd    建单命令（orderType/equipmentId/equipmentName/description 必填，其余可选）
     * @param source 请求来源
     * @return 创建成功的工单（含 id/orderNo）
     */
    @PostMapping("/inner/work-orders")
    R<WorkOrderDTO> createWorkOrder(@RequestBody WorkOrderCreateDTO cmd, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    /**
     * 分页查询工单
     * <p>
     * 返回 TableDataInfo 而非 R 包裹，与 alert 对外分页接口同构，
     * MCP 侧直接消费 rows/total 结构。
     * </p>
     *
     * @param page      页码（从 1 起）
     * @param size      页大小
     * @param status    状态过滤（可选）
     * @param orderType 工单类型过滤（可选）
     * @param keyword   工单号/设备名/传感器名模糊检索（可选）
     * @param source    请求来源
     * @return 分页结果
     */
    @GetMapping("/inner/work-orders/page")
    TableDataInfo pageWorkOrders(@RequestParam("page") long page,
                                 @RequestParam("size") long size,
                                 @RequestParam(value = "status", required = false) String status,
                                 @RequestParam(value = "orderType", required = false) String orderType,
                                 @RequestParam(value = "keyword", required = false) String keyword,
                                 @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    /**
     * 转派工单处理人
     *
     * @param id     工单ID
     * @param body   请求体（handler/handlerName 键）
     * @param source 请求来源
     * @return 结果
     */
    @PostMapping("/inner/work-orders/{id}/assign")
    R<Void> assignWorkOrder(@PathVariable("id") Long id, @RequestBody Map<String, Object> body, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    /**
     * 完成工单（联动设备退化复位）
     * <p>
     * 返回 Map 而非 WorkOrderDTO：完成动作对调用方的核心增量信息是复位结果
     * （resetSuccess/resetMessage），CompleteResult 是 alert 内部类不宜暴露进
     * 契约，且字段简单，故以 Map 透传。
     * </p>
     *
     * @param id     工单ID
     * @param body   请求体（handleRemark 键）
     * @param source 请求来源
     * @return 复位结果（resetSuccess/resetMessage）
     */
    @PostMapping("/inner/work-orders/{id}/complete")
    R<Map<String, Object>> completeWorkOrder(@PathVariable("id") Long id, @RequestBody Map<String, Object> body, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    /**
     * 取消工单（不触发复位）
     *
     * @param id     工单ID
     * @param body   请求体（reason 键）
     * @param source 请求来源
     * @return 结果
     */
    @PostMapping("/inner/work-orders/{id}/cancel")
    R<Void> cancelWorkOrder(@PathVariable("id") Long id, @RequestBody Map<String, Object> body, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}
