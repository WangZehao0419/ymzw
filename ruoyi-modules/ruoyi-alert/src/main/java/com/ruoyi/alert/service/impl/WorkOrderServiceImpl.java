package com.ruoyi.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.entity.MaintenancePlan;
import com.ruoyi.alert.entity.WorkOrder;
import com.ruoyi.alert.entity.WorkOrderActionLog;
import com.ruoyi.alert.mapper.AlertEventMapper;
import com.ruoyi.alert.mapper.WorkOrderActionLogMapper;
import com.ruoyi.alert.mapper.WorkOrderMapper;
import com.ruoyi.alert.predict.PredictStateMachine;
import com.ruoyi.alert.service.WorkOrderService;
import com.ruoyi.alert.service.domain.CompleteResult;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 维保工单服务实现
 *
 * @author smartartisan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl implements WorkOrderService {

    /** 工单编号时间戳格式:WO + yyyyMMddHHmmss + 3位随机 */
    private static final DateTimeFormatter ORDER_NO_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WorkOrderMapper workOrderMapper;

    // 复位指令经 Feign 下发设备服务,不在 alert 模块直连设备/MQTT
    private final RemoteEquipmentService remoteEquipmentService;

    // 完成后清预测状态机基线,让下轮检测重新学习维护后的正常态
    private final PredictStateMachine predictStateMachine;

    // 流转留痕旁路
    private final WorkOrderActionLogMapper workOrderActionLogMapper;

    // 完成工单时联动解除该传感器的活动 RULE 告警(维修完成=故障消除,告警须同步收敛)
    private final AlertEventMapper alertEventMapper;

    /** 完成工单是否联动设备退化复位(关闭后完成仅落状态,不下发复位指令) */
    @Value("${workorder.reset-on-complete:true}")
    private boolean resetOnComplete;

    /** 转工单的告警级别白名单:四级告警均可建单(NORMAL 恢复语义无维修动作,不在此列) */
    private static final Set<String> ORDER_LEVELS = Set.of("WARNING", "IMPORTANT", "SEVERE", "CRITICAL");

    @Override
    public WorkOrder createFromAlert(AlertEvent alert, Long handlerId, String handlerName) {
        // 防御性过滤:仅 RULE 建单(split-predict-alert-storage 设计)——PREDICT 告警已分表
        // predict_alert,其 id 指向另一张表的主键,继续快照进 related_id 会造成追溯错乱;
        // 且预测是"将发生"的预警,与工单"实际维修动作"语义不同。STAT 恢复类/NORMAL 无
        // 维修动作,同样不建单。与 WorkOrderCreateListener 预过滤口径一致。
        String type = alert.getAlertType();
        String level = alert.getAlertLevel();
        if (!"RULE".equals(type)) {
            return null;
        }
        if (!ORDER_LEVELS.contains(level)) {
            return null;
        }

        // 去重:同设备+传感器已有未结的故障维修工单则不再生成
        // (模拟器约 20 秒/条告警,不去重会把工单列表刷成同一故障的重复单);
        // source_type 已删,来源判别收敛到 order_type,去重口径随之收窄到故障维修单
        Long exist = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getEquipmentId, alert.getEquipmentId())
                .eq(WorkOrder::getSensorId, alert.getSensorId())
                .eq(WorkOrder::getOrderType, "故障维修")
                .in(WorkOrder::getStatus, "PENDING", "PROCESSING"));
        if (exist != null && exist > 0) {
            log.debug("[WorkOrder] 已有未结工单,跳过生成: equipmentId={}, sensorId={}, orderType=故障维修",
                    alert.getEquipmentId(), alert.getSensorId());
            return null;
        }

        WorkOrder order = new WorkOrder();
        order.setOrderNo(generateOrderNo());
        // 来源判别收敛到 order_type:source_type/alert_event_id 等来源字段已删,
        // 故障维修/预防维护成为唯一来源判别字段并路由 related_id 关联表
        order.setOrderType("故障维修");
        // order_type=故障维修 路由 alert_event:告警事件只留痕不删,
        // 工单快照设备/传感器/级别,保证告警被清理后工单仍可读
        order.setRelatedId(alert.getId());
        order.setEquipmentId(alert.getEquipmentId());
        order.setEquipmentName(alert.getEquipmentName());
        order.setSensorId(alert.getSensorId());
        order.setSensorName(alert.getSensorName());
        order.setAlertLevel(alert.getAlertLevel());
        order.setDescription(buildDescription(alert));
        order.setStatus("PENDING");
        // 处理人可空:设备未绑定负责人时工单先挂起,由调度在列表页人工转派
        order.setHandler(handlerId);
        order.setHandlerName(handlerName);

        try {
            workOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // order_no 唯一键冲突:秒级时间戳+3位随机的组合在并发下仍可能撞号,换号重试一次即可
            order.setOrderNo(generateOrderNo());
            workOrderMapper.insert(order);
        }
        // 建单即留痕:监听器异步生成无用户上下文,operator 统一 system;
        // 处理人有无区分"系统派单"与"待转派"两种派单形态
        String source = "阈值告警触发自动生成";
        writeLog(order, "CREATE", "system", handlerName != null
                ? source + "，处理人 " + handlerName
                : source + "，设备未绑定负责人，待转派");
        return order;
    }

    @Override
    public WorkOrder createFromPlan(MaintenancePlan plan) {
        // 同日幂等:调度任务的"建单"与"next_fire_time 推进"是两个独立写动作、非原子——
        // 建单成功但推进失败的极端场景下,下轮调度会再次命中同一计划,不查重就会
        // 同一天重复建单。related_id 预防维护路由唯一指向本计划(plan_id/source_type
        // 已删,判重口径随之迁移),以当日零点起已有该计划的工单为界判重。
        Long exist = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getRelatedId, plan.getId())
                .ge(WorkOrder::getCreateTime, LocalDate.now().atStartOfDay()));
        if (exist != null && exist > 0) {
            log.debug("[WorkOrder] 该计划今日已生成工单,跳过: planId={}, planNo={}",
                    plan.getId(), plan.getPlanNo());
            return null;
        }

        WorkOrder order = new WorkOrder();
        order.setOrderNo(generateOrderNo());
        // 计划维保是"未发生故障的预防动作",与告警侧"已发生的故障维修"相对
        order.setOrderType("预防维护");
        // 计划是设备级动作,无传感器上下文:sensor 系列字段全部留空(不设即 null),
        // 溯源统一走 related_id(order_type=预防维护 路由 maintenance_plan)
        order.setRelatedId(plan.getId());
        order.setEquipmentId(plan.getEquipmentId());
        order.setEquipmentName(plan.getEquipmentName());
        order.setDescription(buildPlanDescription(plan));
        order.setStatus("PENDING");
        // 计划侧负责人快照到处理人:计划未配置时留空,工单先挂起由调度人工转派
        order.setHandler(plan.getAssigneeId());
        order.setHandlerName(plan.getAssigneeName());

        try {
            workOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // order_no 唯一键冲突:与 createFromAlert 同口径,换号重试一次
            order.setOrderNo(generateOrderNo());
            workOrderMapper.insert(order);
        }
        // 计划建单同样留痕:流转记录覆盖全部建单路径,否则预防维护工单时间线为空;
        // 计划未配处理人与告警侧同口径记"待转派"
        writeLog(order, "CREATE", "system", plan.getAssigneeName() != null
                ? "维护计划 " + plan.getPlanNo() + " 触发自动生成，处理人 " + plan.getAssigneeName()
                : "维护计划 " + plan.getPlanNo() + " 触发自动生成，计划未配置负责人，待转派");
        return order;
    }

    @Override
    public void assign(Long id, Long handlerId, String handlerName, String operator) {
        WorkOrder order = requireOrder(id);
        // 已完成/已取消的工单没有后续维修动作,转派无意义
        if (!"PENDING".equals(order.getStatus()) && !"PROCESSING".equals(order.getStatus())) {
            throw new ServiceException("当前状态不允许指派");
        }
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setHandler(handlerId);
        upd.setHandlerName(handlerName);
        workOrderMapper.updateById(upd);
        // 接单功能删除后处理人唯一入口:首派(原处理人为空)与改派同语义,
        // 统一记"转派处理人",旧名非空时带"旧名 → 新名"方向便于追溯派单链
        writeLog(order, "ASSIGN", operator, order.getHandlerName() == null
                ? "转派处理人：" + handlerName
                : "转派处理人：" + order.getHandlerName() + " → " + handlerName);
    }

    @Override
    public CompleteResult complete(Long id, String handleRemark, String operator) {
        WorkOrder order = requireOrder(id);
        // 接单功能删除后 PENDING 可直接完成(建单→处理→完成一气呵成),
        // PROCESSING 校验保留仅为兼容存量接单数据;终态工单不可再完成
        if (!"PENDING".equals(order.getStatus()) && !"PROCESSING".equals(order.getStatus())) {
            throw new ServiceException("当前状态不允许完成");
        }

        // ①工单状态先行落库:完成是本地事务,复位是跨服务联动,两者解耦
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setStatus("COMPLETED");
        upd.setHandleRemark(handleRemark);
        upd.setFinishTime(LocalDateTime.now());
        // 处理人归属由转派决定,完成操作人仅记流转日志,不再覆写 handler
        workOrderMapper.updateById(upd);

        // ②联动解除告警:维修完成=传感器故障消除,该传感器名下活动 RULE 告警应同步收敛。
        // 独立 try-catch 旁路:解除异常只降级为留痕告警,不阻断工单完成与复位(完成语义已落库,不可逆)
        int resolvedCount;
        try {
            resolvedCount = resolveActiveAlerts(order);
        } catch (Exception e) {
            resolvedCount = 0;
            log.warn("[WorkOrder] 告警解除失败(不影响工单完成与复位): orderId={}, sensorId={}, error={}",
                    id, order.getSensorId(), e.getMessage());
        }

        // 结果变量 + 单一出口:未配置复位/复位失败/复位异常/成功四条路径先统一赋值,
        // 在 return 前集中写 COMPLETE 流转日志,保证完成必留痕且文案含复位结果
        CompleteResult result;
        if (!resetOnComplete) {
            result = new CompleteResult(true, "未配置联动复位");
        } else {
            // ③复位指令经 Feign 下发设备服务(→MQTT maintenance/{equipmentNo}→模拟器退化清零)
            // 失败时不再执行④:数据未回落就重置状态机会令基线被退化数据污染重学
            try {
                R<Void> resetResult = remoteEquipmentService.resetDegradation(
                        order.getEquipmentId(), SecurityConstants.INNER);
                if (resetResult == null || R.FAIL == resetResult.getCode()) {
                    String msg = resetResult == null ? "复位服务无响应" : resetResult.getMsg();
                    log.warn("[WorkOrder] 复位指令下发失败: equipmentId={}, msg={}", order.getEquipmentId(), msg);
                    result = new CompleteResult(false, "复位指令下发失败: " + msg);
                } else {
                    // ④设备级复位预测状态机:模拟器按设备清零全部退化,逐个传感器 reset 清基线
                    // (本步骤仅影响预测侧基线,自身异常不回滚工单完成)
                    try {
                        R<List<SensorMetaDTO>> sensorsResult = remoteEquipmentService.listAllSensors(SecurityConstants.INNER);
                        if (sensorsResult != null && sensorsResult.getData() != null) {
                            sensorsResult.getData().stream()
                                    .filter(s -> order.getEquipmentId().equals(s.getEquipmentId()))
                                    .forEach(s -> predictStateMachine.reset(s.getSensorCode()));
                        }
                    } catch (Exception e) {
                        log.warn("[WorkOrder] 预测状态机复位失败(不影响工单完成): equipmentId={}, error={}",
                                order.getEquipmentId(), e.getMessage());
                    }
                    result = new CompleteResult(true, "复位指令已下发,预测基线已重置");
                }
            } catch (Exception e) {
                log.warn("[WorkOrder] 复位指令下发异常: equipmentId={}, error={}", order.getEquipmentId(), e.getMessage());
                result = new CompleteResult(false, "复位指令下发异常: " + e.getMessage());
            }
        }
        // 处理说明/告警解除/复位结果同句留痕:完成记录既要有"做了什么",也要有
        // 告警是否收敛与联动复位是否成功,三者同句便于单条日志追溯完成全貌
        writeLog(order, "COMPLETE", operator,
                "处理完成：" + (handleRemark == null ? "" : handleRemark)
                        + "；解除告警 " + resolvedCount + " 条；" + result.getResetMessage());
        return result;
    }

    @Override
    public void cancel(Long id, String reason, String operator) {
        WorkOrder order = requireOrder(id);
        // 已完结工单的历史事实不可取消;取消不触发复位(未维修的设备劣化仍在)
        if (!"PENDING".equals(order.getStatus()) && !"PROCESSING".equals(order.getStatus())) {
            throw new ServiceException("当前状态不允许取消");
        }
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setStatus("CANCELLED");
        upd.setCancelReason(reason);
        workOrderMapper.updateById(upd);
        log.info("[WorkOrder] 工单已取消: id={}, operator={}, reason={}", id, operator, reason);
        writeLog(order, "CANCEL", operator, "取消工单，原因：" + (reason == null ? "" : reason));
    }

    /**
     * 解除工单关联传感器名下全部活动 RULE 告警
     * <p>
     * 解除范围语义:维修完成=该传感器故障已消除,而同一故障在维修前往往已累积
     * 多条 FIRING/ACKED 告警,故按 sensor_id 维度一次性解除全部,而非只解除触发
     * 建单的那一条;PLAN 工单无传感器上下文(设备级预防动作,无故障语义),跳过。
     * </p>
     * <p>
     * 为什么独立于复位逻辑:维修完成语义先于复位成功——复位失败只代表指令未送达
     * 设备,"故障已修好"的事实不变,告警该解除照解除,两者互不回滚。
     * </p>
     *
     * @return 实际解除的告警条数
     */
    private int resolveActiveAlerts(WorkOrder order) {
        if (order.getSensorId() == null) {
            return 0;
        }
        // 单条批量 UPDATE:set 状态 RESOLVED+解除时间,条件=同传感器+RULE+仍活动(FIRING/ACKED),
        // 避免 N+1 逐条更新;update 首参传 null 是因为 set 已全部由 wrapper 承载
        LambdaUpdateWrapper<AlertEvent> wrapper = new LambdaUpdateWrapper<AlertEvent>()
                .set(AlertEvent::getAlertStatus, "RESOLVED")
                .set(AlertEvent::getResolveTime, LocalDateTime.now())
                .eq(AlertEvent::getSensorId, order.getSensorId())
                .eq(AlertEvent::getAlertType, "RULE")
                .in(AlertEvent::getAlertStatus, "FIRING", "ACKED");
        return alertEventMapper.update(null, wrapper);
    }

    /**
     * 生成工单描述:仅 RULE 阈值告警分支
     * (方法开头已过滤仅 RULE 建单,原 PREDICT"预计越界/性能异常"两分支为死代码,已清理)
     */
    private String buildDescription(AlertEvent alert) {
        String equipmentName = alert.getEquipmentName();
        String sensorName = alert.getSensorName();
        return "【阈值告警】" + equipmentName + "-" + sensorName
                + " 当前值 " + alert.getSensorValue() + " 超限，请检修";
    }

    /**
     * 计划工单描述:【维护计划】+ 设备名 + 保养类型 + 内容(可空,为空时省略":内容"段) + 计划编号
     */
    private String buildPlanDescription(MaintenancePlan plan) {
        // 内容可空:为空时省略"：内容"段,避免出现冒号后悬空直接接计划编号的残缺文案
        String contentPart = (plan.getContent() == null || plan.getContent().isBlank())
                ? "" : "：" + plan.getContent();
        return "【维护计划】" + plan.getEquipmentName() + " " + plan.getMaintenanceType()
                + contentPart + "（计划 " + plan.getPlanNo() + "）";
    }

    /**
     * 生成工单编号:WO + yyyyMMddHHmmss + 3位随机数字
     */
    private String generateOrderNo() {
        return "WO" + LocalDateTime.now().format(ORDER_NO_TS)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    /**
     * 写工单流转日志
     * <p>
     * 为什么吞异常:日志是状态流转的审计旁路,若 insert 失败向上抛,
     * 会造成"工单状态已变更、接口却报错"的不一致;审计记录缺失可接受,
     * 主流程报错不可接受。
     * </p>
     */
    private void writeLog(WorkOrder order, String action, String operator, String detail) {
        try {
            WorkOrderActionLog actionLog = new WorkOrderActionLog();
            actionLog.setOrderId(order.getId());
            // order_no 冗余存档:日志直查免联工单表
            actionLog.setOrderNo(order.getOrderNo());
            actionLog.setAction(action);
            actionLog.setOperator(operator);
            actionLog.setDetail(detail);
            workOrderActionLogMapper.insert(actionLog);
        } catch (Exception e) {
            log.warn("[WorkOrder] 流转日志写入失败(不影响主流程): orderId={}, action={}, error={}",
                    order.getId(), action, e.getMessage());
        }
    }

    /**
     * 加载工单,不存在时统一抛 ServiceException 由 Controller 转 AjaxResult.error
     */
    private WorkOrder requireOrder(Long id) {
        WorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException("工单不存在");
        }
        return order;
    }
}
