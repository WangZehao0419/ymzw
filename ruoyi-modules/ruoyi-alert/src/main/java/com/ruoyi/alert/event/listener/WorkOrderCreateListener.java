package com.ruoyi.alert.event.listener;

import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.entity.WorkOrder;
import com.ruoyi.alert.event.AlertTriggeredEvent;
import com.ruoyi.alert.service.WorkOrderService;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import com.ruoyi.equipment.api.domain.EquipmentMetaDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 维保工单生成监听器
 * <p>
 * 消费 AlertTriggeredEvent,把 RULE 类预警/严重告警自动转为维保工单,
 * 处理人预填设备责任人(equipment.equipment_user_id)。工单生成是旁路动作:
 * 与 Push(10)/Notify(20) 互不影响,任何失败只记日志,绝不影响落库与检测主链路。
 * </p>
 * <p>
 * 执行时序说明:@Order(30) 排在 Notify(20) 之后;配合 @Async 在独立线程执行,
 * 为"等待告警 id 回填"留出与落库并发的时间窗口。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderCreateListener {

    /** 等待落库回填 id 的上限(ms):内网 MySQL insert 通常毫秒级完成 */
    private static final long ID_BACKFILL_TIMEOUT_MS = 1000L;

    /** 等待 id 回填的轮询间隔(ms) */
    private static final long ID_BACKFILL_POLL_MS = 20L;

    private final WorkOrderService workOrderService;

    // 设备责任人元数据经 Feign 获取,不在 alert 模块直连 equipment 跨模块表
    private final RemoteEquipmentService remoteEquipmentService;

    /** 工单生成总开关:关闭后告警只通知不生成工单 */
    @Value("${workorder.enabled:true}")
    private boolean workOrderEnabled;

    /** 转工单的告警级别白名单:四级告警均可建单(NORMAL 恢复语义无维修动作,不在此列) */
    private static final Set<String> ORDER_LEVELS = Set.of("WARNING", "IMPORTANT", "SEVERE", "CRITICAL");

    @Async
    @EventListener
    @Order(30)
    public void onAlertTriggered(AlertTriggeredEvent event) {
        try {
            if (!workOrderEnabled) {
                log.debug("[WorkOrder] 工单生成开关关闭,跳过");
                return;
            }
            AlertEvent alert = event.getAlertEvent();

            // 类型/级别过滤:与 createFromAlert 的防御口径一致,
            // 监听器先挡一层可以省掉无谓的 Feign 负责人查询。
            // PREDICT 不建工单:预测是"将发生"的预警,工单是实际维修动作,两者语义不同;
            // 且分表后 PREDICT 的告警 id 指向 predict_alert,继续快照进工单的
            // alert_event_id 字段会造成追溯错乱(指向另一张表的主键)
            String type = alert.getAlertType();
            String level = alert.getAlertLevel();
            if (!"RULE".equals(type)) {
                return;
            }
            if (!ORDER_LEVELS.contains(level)) {
                return;
            }

            waitForIdBackfill(alert);

            // 查设备责任人作工单默认处理人;Feign 失败/未配责任人时置空继续,
            // 工单绝不丢(后续可人工转派),与通知监听器"宁可不定人不可不建单"取舍不同
            Long handlerId = null;
            String handlerName = null;
            try {
                R<EquipmentMetaDTO> equipmentResult = remoteEquipmentService.getEquipmentMeta(
                        alert.getEquipmentId(), SecurityConstants.INNER);
                if (equipmentResult != null && R.FAIL != equipmentResult.getCode()
                        && equipmentResult.getData() != null
                        && equipmentResult.getData().getEquipmentUserId() != null) {
                    // equipmentUserId 为 Integer 需转 Long 对齐工单处理人字段
                    handlerId = equipmentResult.getData().getEquipmentUserId().longValue();
                    handlerName = equipmentResult.getData().getEquipmentUserName();
                } else {
                    log.debug("[WorkOrder] 设备未配责任人或设备服务不可用,工单不带处理人: equipmentId={}",
                            alert.getEquipmentId());
                }
            } catch (Exception e) {
                log.warn("[WorkOrder] 责任人查询失败,工单不带处理人: equipmentId={}, error={}",
                        alert.getEquipmentId(), e.getMessage());
            }

            WorkOrder order = workOrderService.createFromAlert(alert, handlerId, handlerName);
            if (order != null) {
                log.info("[WorkOrder] 工单已生成: orderNo={}, orderType={}, equipment={}, sensor={}, handler={}",
                        order.getOrderNo(), order.getOrderType(), order.getEquipmentName(),
                        order.getSensorName(), handlerName);
            } else {
                log.debug("[WorkOrder] 工单跳过(重复告警或状态不满足): sensorCode={}, type={}, level={}",
                        alert.getSensorCode(), type, level);
            }
        } catch (Exception e) {
            // 整体旁路容错:工单生成失败不影响落库/推送/通知主链路
            log.error("[WorkOrder] 工单生成失败: {}", e.getMessage());
        }
    }

    /**
     * 有界等待落库监听器把自增 id 回填到同一 AlertEvent 引用
     * <p>
     * 为什么需要等待:工单要快照 related_id 作关联追溯(order_type=故障维修 路由
     * alert_event),而本监听器与落库监听器并发执行,立即读取大概率拿不到 id,
     * 故短暂轮询等待;超时(如落库异常/极慢)则带 null 快照继续建单,不无限阻塞。
     * </p>
     */
    private void waitForIdBackfill(AlertEvent alert) {
        long deadline = System.currentTimeMillis() + ID_BACKFILL_TIMEOUT_MS;
        while (alert.getId() == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(ID_BACKFILL_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (alert.getId() == null) {
            log.warn("[WorkOrder] 等待告警 id 回填超时, 工单将缺少告警关联: code={}", alert.getSensorCode());
        }
    }
}
