package com.ruoyi.alert.event.listener;

import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.event.AlertEscalatedEvent;
import com.ruoyi.alert.event.AlertTriggeredEvent;
import com.ruoyi.alert.service.MailNotifyService;
import com.ruoyi.alert.service.VoiceCallService;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import com.ruoyi.equipment.api.domain.EquipmentMetaDTO;
import com.ruoyi.system.api.RemoteUserService;
import com.ruoyi.system.api.domain.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警邮件/电话通知监听器
 * <p>
 * 消费 AlertTriggeredEvent,按设备责任人(equipment.equipment_user_id → sys_user)
 * 推送邮件与阿里云语音外呼。两者均为旁路触达渠道,与 NDJSON 流推送互不影响:
 * 任何失败只记日志,绝不影响落库与检测主链路。
 * 节流防模拟器防抖后约 20 秒/条告警的通知风暴(邮件轰炸/重复外呼骚扰)。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotifyListener {

    // 设备/责任人元数据经 Feign 获取,不再直连 equipment/sys_user 跨模块表
    private final RemoteEquipmentService remoteEquipmentService;
    private final RemoteUserService remoteUserService;
    private final MailNotifyService mailNotifyService;
    private final VoiceCallService voiceCallService;

    /** 触达责任人的告警级别白名单:四级告警全放行(NORMAL 为恢复语义,不在此列) */
    private static final Set<String> NOTIFY_LEVELS = Set.of("WARNING", "IMPORTANT", "SEVERE", "CRITICAL");

    /** 通知冷却: key=设备:传感器:级别 -> 上次放行时间戳(内存态,与检测防抖同模式,重启失效可接受) */
    private final Map<String, Long> notifyThrottle = new ConcurrentHashMap<>();

    /** 通知冷却时长(ms),默认 10 分钟 */
    @Value("${alert.notify.throttle-ms:600000}")
    private long throttleMs;

    @Async
    @EventListener
    @Order(20)
    public void onAlertTriggered(AlertTriggeredEvent event) {
        // 预测告警是"将发生"而非已发生,仅页面/铃铛提示,不邮件不外呼(D4)
        if ("PREDICT".equals(event.getAlertEvent().getAlertType())) {
            return;
        }
        handleNotify(event.getAlertEvent());
    }

    /**
     * 升级后等级变化,按新等级重新走责任人通知(严重告警值得再次触达);
     * 节流 key 含级别,SEVERE 升级不会被 WARNING 的冷却挡住
     */
    @Async
    @EventListener
    @Order(20)
    public void onAlertEscalated(AlertEscalatedEvent event) {
        // 预测告警是"将发生"而非已发生,仅页面/铃铛提示,不邮件不外呼(D4)
        if ("PREDICT".equals(event.getAlertEvent().getAlertType())) {
            return;
        }
        handleNotify(event.getAlertEvent());
    }

    /**
     * 通知主逻辑:级别门槛 → 冷却节流 → 查责任人 → 邮件/电话旁路触达
     */
    private void handleNotify(AlertEvent alert) {
        try {
            // 级别门槛:四级告警全放行,NORMAL 为恢复语义不触达责任人(null/未知一并拦截,防脏数据骚扰)
            String level = alert.getAlertLevel();
            if (!NOTIFY_LEVELS.contains(level)) {
                log.debug("[Notify] 非告警级别(含 NORMAL 恢复),跳过通知: level={}", level);
                return;
            }

            // 通知冷却:同 设备:传感器:级别 在冷却期内只触达一次
            String key = alert.getEquipmentId() + ":" + alert.getSensorCode() + ":" + alert.getAlertLevel();
            if (!tryAcquire(key)) {
                log.debug("[Notify] 冷却期内,跳过外呼通知: key={}", key);
                return;
            }

            // 查设备元数据取责任人(经 Feign,设备服务不可用时降级跳过通知)
            R<EquipmentMetaDTO> equipmentResult = remoteEquipmentService.getEquipmentMeta(
                    alert.getEquipmentId(), SecurityConstants.INNER);
            if (equipmentResult == null || R.FAIL == equipmentResult.getCode() || equipmentResult.getData() == null) {
                log.warn("[Notify] 设备不存在或设备服务不可用,跳过通知: equipmentId={}", alert.getEquipmentId());
                return;
            }
            EquipmentMetaDTO equipment = equipmentResult.getData();
            if (equipment.getEquipmentUserId() == null) {
                log.warn("[Notify] 设备未配责任人,跳过通知: equipmentId={}", alert.getEquipmentId());
                return;
            }
            // 查责任人联系方式(经 Feign,equipmentUserId 为 Integer 需转 Long 对齐用户主键)
            R<SysUser> userResult = remoteUserService.getUserContact(
                    equipment.getEquipmentUserId().longValue(), SecurityConstants.INNER);
            if (userResult == null || R.FAIL == userResult.getCode() || userResult.getData() == null) {
                log.warn("[Notify] 责任人查询失败,跳过通知: userId={}", equipment.getEquipmentUserId());
                return;
            }
            SysUser user = userResult.getData();

            // 邮件/电话各自旁路容错:一个渠道失败不影响另一个
            try {
                mailNotifyService.sendAlertMail(alert, user);
            } catch (Exception e) {
                log.error("[Notify] 邮件推送异常: {}", e.getMessage());
            }
            try {
                voiceCallService.callAlert(alert, user);
            } catch (Exception e) {
                log.error("[Notify] 电话外呼异常: {}", e.getMessage());
            }
        } catch (Exception e) {
            // 整体旁路容错:通知失败绝不影响落库/流推送主链路
            log.error("[Notify] 告警通知处理失败: {}", e.getMessage());
        }
    }

    /**
     * 原子抢占通知放行权:冷却期内返回 false,过期/首次写入时间戳并返回 true
     * <p>
     * 用 compute 而非 get+put:check-then-put 两步在并发下可能双放行,
     * compute 把判断与写入合并为原子操作,杜绝同一 key 重复通知。
     * </p>
     */
    private boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Long mapped = notifyThrottle.compute(key, (k, last) ->
                last != null && now - last < throttleMs ? last : now);
        return mapped == now;
    }
}
