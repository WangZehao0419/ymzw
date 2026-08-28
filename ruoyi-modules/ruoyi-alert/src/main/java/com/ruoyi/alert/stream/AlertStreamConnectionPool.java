package com.ruoyi.alert.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 告警流式推送连接池
 * <p>
 * 管理 /api/alert-events/stream 端点的全部长连接(ResponseBodyEmitter)。
 * 采用全局广播模式(不分组):所有在线客户端均收到全量告警,
 * 消息格式为 NDJSON——每条消息是一行 JSON 加换行符。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertStreamConnectionPool {

    private final ObjectMapper objectMapper;

    /** 读多写少(广播遍历频繁、连接增删偶发),用 COW 列表保证遍历期间并发增删安全 */
    private final List<ResponseBodyEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * 注册新连接
     * <p>
     * 必须挂接生命周期回调:连接无论正常完成、超时还是异常断开,都要从池中移除,
     * 否则失效连接会持续累积,造成内存泄漏与无效发送。
     * </p>
     */
    public void add(ResponseBodyEmitter emitter) {
        emitters.add(emitter);
        // 正常完成(客户端断开或服务端主动 complete):移除
        emitter.onCompletion(() -> emitters.remove(emitter));
        // 超时:主动结束响应以触发 onCompletion,完成移除;complete 也失败时兜底移除
        emitter.onTimeout(() -> {
            try {
                emitter.complete();
            } catch (Exception ignore) {
                emitters.remove(emitter);
            }
        });
        // IO 异常(客户端异常断开):直接移除;此时不再调 complete,避免对已断连的响应二次抛错
        emitter.onError(t -> {
            emitters.remove(emitter);
            log.debug("[Stream] 连接异常已移除, 剩余连接数={}, error={}", emitters.size(), t.getMessage());
        });
    }

    /**
     * 向所有在线连接广播一条 NDJSON 消息(单行 JSON + 换行符)
     * <p>
     * 单个连接发送失败不影响其他连接:失败的连接 complete 并移除,仅记日志。
     * </p>
     */
    public void broadcast(Object data) {
        if (emitters.isEmpty()) {
            return;
        }
        String line;
        try {
            line = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            // 序列化失败是服务端数据问题,与具体连接无关,放弃本次广播即可
            log.error("[Stream] 广播数据序列化失败: {}", e.getMessage());
            return;
        }
        for (ResponseBodyEmitter emitter : emitters) {
            try {
                // NDJSON 规范:一行一个 JSON 对象;指定 MediaType 保证按文本原样下发不被转码
                emitter.send(line + "\n", MediaType.TEXT_PLAIN);
            } catch (Exception e) {
                try {
                    emitter.complete();
                } catch (Exception ignore) {
                    // 连接已失效时 complete 可能再抛异常,忽略
                }
                emitters.remove(emitter);
                log.warn("[Stream] 连接发送失败已移除, 剩余连接数={}, error={}", emitters.size(), e.getMessage());
            }
        }
    }

    /**
     * 30 秒心跳
     * <p>
     * 为什么需要心跳:Nginx/网关等中间件默认会因连接空闲超时掐断长连接,
     * 定期下发一行心跳既保活,也供客户端探活判断连接可用性。
     * </p>
     */
    @Scheduled(fixedRate = 30000)
    public void heartbeat() {
        broadcast(Collections.singletonMap("type", "heartbeat"));
    }

    /**
     * 应用关闭时主动结束所有连接,避免客户端悬挂等待直到自身超时
     */
    @PreDestroy
    public void shutdown() {
        for (ResponseBodyEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // 关闭阶段尽力而为,单个连接结束失败不影响其余清理
            }
        }
        emitters.clear();
    }
}
