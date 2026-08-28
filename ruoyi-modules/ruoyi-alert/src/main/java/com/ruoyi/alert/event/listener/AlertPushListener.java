package com.ruoyi.alert.event.listener;

import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.event.AlertTriggeredEvent;
import com.ruoyi.alert.stream.AlertStreamConnectionPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 告警流式推送监听器
 * <p>
 * 消费 AlertTriggeredEvent,把告警事件实时广播到所有流式长连接。
 * 广播是纯旁路动作,任何失败只记日志,绝不影响落库与检测主链路。
 * </p>
 * <p>
 * 执行时序说明:落库监听器未标注 @Order(Spring 对 @EventListener 的默认顺序是
 * 最低优先级,即同步分发中最后执行),而本监听器 Order(10) 会先被回调;
 * 因此配合 @Async 在独立线程执行——既让推送 IO 不阻塞 MQ 消费与落库,
 * 也为下方"等待 id 回填"留出与落库并发的时间窗口。
 * (模块无 alertPushExecutor 线程池 Bean,Async 使用默认线程池即可。)
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertPushListener {

    /** 等待落库回填 id 的上限(ms):内网 MySQL insert 通常毫秒级完成 */
    private static final long ID_BACKFILL_TIMEOUT_MS = 1000L;

    /** 等待 id 回填的轮询间隔(ms) */
    private static final long ID_BACKFILL_POLL_MS = 20L;

    private final AlertStreamConnectionPool connectionPool;

    @Async
    @EventListener
    @Order(10)
    public void onAlertTriggered(AlertTriggeredEvent event) {
        try {
            AlertEvent alert = event.getAlertEvent();
            waitForIdBackfill(alert);
            // 事件对象与落库监听器操作的是同一引用,等待后序列化即"落库后"的最新内容
            connectionPool.broadcast(alert);
            log.info("[Push] 告警已推送: id={}, code={}, level={}",
                    alert.getId(), alert.getSensorCode(), alert.getAlertLevel());
        } catch (Exception e) {
            // 推送失败不影响主链路(落库/检测),仅记日志
            log.error("[Push] 告警推送失败: {}", e.getMessage());
        }
    }

    /**
     * 有界等待落库监听器把自增 id 回填到同一 AlertEvent 引用
     * <p>
     * 为什么需要等待:AlertTriggeredEvent 在全链路传递的是同一个 AlertEvent 对象,
     * MyBatis-Plus insert(IdType.AUTO)完成后 id 才回填到该对象;本监听器先于落库被回调,
     * 异步线程立即序列化大概率拿不到 id,故短暂轮询等待。
     * 超时(如落库异常/极慢)则降级推送缺少 id 的内容,不无限阻塞。
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
            log.warn("[Push] 等待告警 id 回填超时, 推送内容将缺少 id: code={}", alert.getSensorCode());
        }
    }
}
