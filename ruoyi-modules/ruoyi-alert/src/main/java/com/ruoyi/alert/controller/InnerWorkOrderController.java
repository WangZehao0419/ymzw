package com.ruoyi.alert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.alert.api.domain.WorkOrderCreateDTO;
import com.ruoyi.alert.api.domain.WorkOrderDTO;
import com.ruoyi.alert.entity.WorkOrder;
import com.ruoyi.alert.mapper.WorkOrderMapper;
import com.ruoyi.alert.service.WorkOrderService;
import com.ruoyi.alert.service.domain.CompleteResult;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.security.annotation.InnerAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 维保工单内部接口 Controller（内部服务调用）
 * <p>
 * 供 ruoyi-mcp（独立 MCP 服务）经 OpenFeign 手动建单、分页查询与流转工单，
 * 端点由 @InnerAuth 保护，仅限服务间携带内部凭证的调用，不对网关外暴露。
 * 仿 InnerPredictController 模式；分页/解析逻辑与对外接口 WorkOrderController
 * 同构，仅返回体收敛为 Feign 契约侧 WorkOrderDTO，避免实体演进击穿契约。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@RestController
@RequestMapping("/inner/work-orders")
@RequiredArgsConstructor
public class InnerWorkOrderController {

    private final WorkOrderMapper workOrderMapper;

    private final WorkOrderService workOrderService;

    /**
     * MCP 调用链固定操作人：内部链路无登录用户上下文，统一以 ai-assistant 留痕，
     * 与自动建单的 system、页面操作的真实用户名在流转日志中区分来源
     */
    private static final String OPERATOR = "ai-assistant";

    /**
     * 手动创建维保工单（内部服务调用，@InnerAuth 保护）
     *
     * @param cmd    建单命令（orderType/equipmentId/equipmentName/description 必填）
     * @param source 请求来源
     * @return 创建成功的工单（含 id/orderNo）
     */
    @InnerAuth
    @PostMapping
    public R<WorkOrderDTO> create(@RequestBody WorkOrderCreateDTO cmd,
                                   @RequestHeader(SecurityConstants.FROM_SOURCE) String source) {
        try {
            WorkOrder order = workOrderService.createManual(cmd, OPERATOR);
            return R.ok(toDTO(order));
        } catch (ServiceException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 分页查询工单（内部服务调用，@InnerAuth 保护）
     * <p>
     * 过滤与排序逻辑与对外接口 WorkOrderController.page 完全同构，
     * 记录转 WorkOrderDTO 后包 TableDataInfo，保持契约字段可控。
     * </p>
     */
    @InnerAuth
    @GetMapping("/page")
    public TableDataInfo page(@RequestParam(defaultValue = "1") long page,
                              @RequestParam(defaultValue = "10") long size,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String orderType,
                              @RequestParam(required = false) String keyword,
                              @RequestHeader(SecurityConstants.FROM_SOURCE) String source) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<WorkOrder>()
                .eq(StringUtils.hasText(status), WorkOrder::getStatus, status)
                .eq(StringUtils.hasText(orderType), WorkOrder::getOrderType, orderType)
                // keyword 是 OR 语义组合条件,须用 and(...) 包成一组括号,
                // 否则 OR 会击穿前面的 eq 精确过滤条件
                .and(StringUtils.hasText(keyword), w -> w
                        .like(WorkOrder::getOrderNo, keyword)
                        .or().like(WorkOrder::getEquipmentName, keyword)
                        .or().like(WorkOrder::getSensorName, keyword))
                // 工单列表默认关心最新工单,按创建时间倒序
                .orderByDesc(WorkOrder::getCreateTime);
        Page<WorkOrder> p = workOrderMapper.selectPage(new Page<>(page, size), wrapper);
        List<WorkOrderDTO> rows = p.getRecords().stream().map(this::toDTO).toList();
        return new TableDataInfo(rows, p.getTotal());
    }

    /**
     * 转派处理人（内部服务调用，@InnerAuth 保护）
     *
     * @param id     工单ID
     * @param body   请求体（handler/handlerName 键）
     * @param source 请求来源
     * @return 结果
     */
    @InnerAuth
    @PostMapping("/{id}/assign")
    public R<Void> assign(@PathVariable("id") Long id,
                          @RequestBody Map<String, Object> body,
                          @RequestHeader(SecurityConstants.FROM_SOURCE) String source) {
        try {
            workOrderService.assign(id, parseLong(body.get("handler")),
                    parseString(body.get("handlerName")), OPERATOR);
            return R.ok();
        } catch (ServiceException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 完成工单（内部服务调用，@InnerAuth 保护）
     * <p>
     * 返回体携带复位结果：工单完成与联动复位是两个独立成败维度，复位失败时
     * MCP 侧须提示用户人工补一次复位。CompleteResult 是 alert 内部类不宜进
     * Feign 契约，字段简单（resetSuccess/resetMessage）故以 Map 透传。
     * </p>
     *
     * @param id     工单ID
     * @param body   请求体（handleRemark 键）
     * @param source 请求来源
     * @return 复位结果（resetSuccess/resetMessage）
     */
    @InnerAuth
    @PostMapping("/{id}/complete")
    public R<Map<String, Object>> complete(@PathVariable("id") Long id,
                                           @RequestBody Map<String, Object> body,
                                           @RequestHeader(SecurityConstants.FROM_SOURCE) String source) {
        try {
            CompleteResult result = workOrderService.complete(id,
                    parseString(body.get("handleRemark")), OPERATOR);
            Map<String, Object> data = new HashMap<>();
            data.put("resetSuccess", result.isResetSuccess());
            data.put("resetMessage", result.getResetMessage());
            return R.ok(data);
        } catch (ServiceException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 取消工单（内部服务调用，@InnerAuth 保护）
     *
     * @param id     工单ID
     * @param body   请求体（reason 键）
     * @param source 请求来源
     * @return 结果
     */
    @InnerAuth
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable("id") Long id,
                          @RequestBody Map<String, Object> body,
                          @RequestHeader(SecurityConstants.FROM_SOURCE) String source) {
        try {
            workOrderService.cancel(id, parseString(body.get("reason")), OPERATOR);
            return R.ok();
        } catch (ServiceException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * WorkOrder 实体转契约 DTO：逐字段拷贝，隔离实体演进与跨服务契约
     */
    private WorkOrderDTO toDTO(WorkOrder o) {
        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setId(o.getId());
        dto.setOrderNo(o.getOrderNo());
        dto.setOrderType(o.getOrderType());
        dto.setRelatedId(o.getRelatedId());
        dto.setEquipmentId(o.getEquipmentId());
        dto.setEquipmentName(o.getEquipmentName());
        dto.setSensorId(o.getSensorId());
        dto.setSensorName(o.getSensorName());
        dto.setAlertLevel(o.getAlertLevel());
        dto.setDescription(o.getDescription());
        dto.setStatus(o.getStatus());
        dto.setHandler(o.getHandler());
        dto.setHandlerName(o.getHandlerName());
        dto.setHandleRemark(o.getHandleRemark());
        dto.setCancelReason(o.getCancelReason());
        dto.setFinishTime(o.getFinishTime());
        dto.setCreateTime(o.getCreateTime());
        return dto;
    }

    /** Map 请求体字段转 Long(MCP 侧可能传数字或字符串两种形态) */
    private Long parseLong(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }

    /** Map 请求体字段转 String */
    private String parseString(Object value) {
        return value == null ? null : value.toString();
    }
}
