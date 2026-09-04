package com.ruoyi.alert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.alert.entity.WorkOrder;
import com.ruoyi.alert.entity.WorkOrderActionLog;
import com.ruoyi.alert.mapper.WorkOrderActionLogMapper;
import com.ruoyi.alert.mapper.WorkOrderMapper;
import com.ruoyi.alert.service.WorkOrderService;
import com.ruoyi.alert.service.domain.CompleteResult;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 维保工单接口
 * <p>
 * 查询侧直接注入 WorkOrderMapper(与 AlertEventController 的用法一致,
 * 仅有单表分页查询,无需再引一层 Service);
 * 流转侧(转派/完成/取消)走 WorkOrderService,含状态校验与复位联动。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderMapper workOrderMapper;

    private final WorkOrderService workOrderService;

    // 流转日志直查旁路:单表查询无需再引 Service(与工单分页查询同口径)
    private final WorkOrderActionLogMapper workOrderActionLogMapper;

    /**
     * 分页查询工单
     * <p>
     * 筛选参数均可选,仅在前端实际传值时才拼接条件;
     * keyword 为模糊检索,同时匹配工单号/设备名/传感器名。
     * </p>
     */
    @GetMapping("/page")
    public TableDataInfo page(@RequestParam(defaultValue = "1") long page,
                              @RequestParam(defaultValue = "10") long size,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String orderType,
                              @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<WorkOrder>()
                .eq(StringUtils.hasText(status), WorkOrder::getStatus, status)
                // source_type 已删,order_type 成为唯一来源判别字段,筛选口径随之迁移
                .eq(StringUtils.hasText(orderType), WorkOrder::getOrderType, orderType)
                // keyword 是 OR 语义组合条件,须用 and(...) 包成一组括号,
                // 否则 OR 会击穿前面的 eq 精确过滤条件
                // (sensor_code 已删,模糊检索保留工单号/设备名/传感器名三项)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(WorkOrder::getOrderNo, keyword)
                        .or().like(WorkOrder::getEquipmentName, keyword)
                        .or().like(WorkOrder::getSensorName, keyword))
                // 工单列表默认关心最新工单,按创建时间倒序
                .orderByDesc(WorkOrder::getCreateTime);
        Page<WorkOrder> p = workOrderMapper.selectPage(new Page<>(page, size), wrapper);
        return new TableDataInfo(p.getRecords(), p.getTotal());
    }

    /**
     * 单工单流转日志
     * <p>
     * 单工单日志量小不分页,按时间正序返回完整流转轨迹;
     * 工单不存在时自然返回空列表,不单独做存在性校验。
     * </p>
     */
    @GetMapping("/{id}/logs")
    public AjaxResult logs(@PathVariable Long id) {
        List<WorkOrderActionLog> list = workOrderActionLogMapper.selectList(
                new LambdaQueryWrapper<WorkOrderActionLog>()
                        .eq(WorkOrderActionLog::getOrderId, id)
                        .orderByAsc(WorkOrderActionLog::getCreateTime));
        return AjaxResult.success(list);
    }

    /**
     * 转派处理人
     */
    @PostMapping("/{id}/assign")
    public AjaxResult assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            workOrderService.assign(id, parseLong(body.get("handler")),
                    parseString(body.get("handlerName")), currentOperator());
            return AjaxResult.success();
        } catch (ServiceException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 完成工单(联动设备退化复位)
     * <p>
     * 返回体携带复位结果:工单完成与复位是两个成败维度,
     * 复位失败时前端需提示人工补一次复位。
     * </p>
     */
    @PostMapping("/{id}/complete")
    public AjaxResult complete(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            CompleteResult result = workOrderService.complete(id,
                    parseString(body.get("handleRemark")), currentOperator());
            return AjaxResult.success(result);
        } catch (ServiceException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 取消工单(不触发复位)
     */
    @PostMapping("/{id}/cancel")
    public AjaxResult cancel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            workOrderService.cancel(id, parseString(body.get("reason")), currentOperator());
            return AjaxResult.success();
        } catch (ServiceException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 取当前登录用户作操作人;取不到(如网关直连调试/内部调用无用户上下文)兜底 system
     */
    private String currentOperator() {
        try {
            String username = SecurityUtils.getUsername();
            return StringUtils.hasText(username) ? username : "system";
        } catch (Exception e) {
            return "system";
        }
    }

    /** Map 请求体字段转 Long(前端可能传数字或字符串两种形态) */
    private Long parseLong(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }

    /** Map 请求体字段转 String */
    private String parseString(Object value) {
        return value == null ? null : value.toString();
    }
}
