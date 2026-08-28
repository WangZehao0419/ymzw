package com.ruoyi.equipment.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NDJSON 流式连接池
 * <p>
 * 管理所有客户端的流式长连接（ResponseBodyEmitter），按设备 ID 分组。
 * 当 MQTT 收到传感器数据时，通过 {@link #send(Integer, Object)} 以
 * NDJSON（每行一个 JSON 对象 + 换行符）的形式推送给所有订阅该设备的前端。
 * 相比 SSE/WebSocket，NDJSON 走纯 HTTP 响应体分块传输，无需额外协议栈，
 * 且对代理/网关更友好。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
public class SensorStreamConnectionPool {

    /**
     * 按设备 ID 分组的流式连接列表
     * <p>
     * ConcurrentHashMap 保证并发安全，CopyOnWriteArrayList 适合读多写少的场景。
     * </p>
     */
    private final Map<Integer, List<ResponseBodyEmitter>> pool = new ConcurrentHashMap<>();

    /**
     * 复用 Spring 容器的 Jackson 序列化器，保证 NDJSON 行格式与 REST 接口一致
     */
    private final ObjectMapper objectMapper;

    public SensorStreamConnectionPool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 注册流式连接
     *
     * @param equipmentId 设备 ID
     * @param emitter     流式发射器
     */
    public void add(Integer equipmentId, ResponseBodyEmitter emitter) {
        pool.computeIfAbsent(equipmentId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.info("流式连接已注册: equipmentId={}, 当前该设备连接数={}", equipmentId,
                pool.get(equipmentId).size());

        // 连接生命周期结束时自动移除，防止连接池泄漏
        emitter.onCompletion(() -> remove(equipmentId, emitter));
        emitter.onTimeout(() -> remove(equipmentId, emitter));
        emitter.onError(e -> remove(equipmentId, emitter));
    }

    /**
     * 移除流式连接
     */
    private void remove(Integer equipmentId, ResponseBodyEmitter emitter) {
        List<ResponseBodyEmitter> emitters = pool.get(equipmentId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                pool.remove(equipmentId);
            }
        }
        log.info("流式连接已移除: equipmentId={}, 剩余连接数={}",
                equipmentId, emitters != null ? emitters.size() : 0);
    }

    /**
     * 向所有订阅该设备的前端推送一行 NDJSON 数据
     *
     * @param equipmentId 设备 ID
     * @param vo          监测数据 VO
     */
    public void send(Integer equipmentId, Object vo) {
        List<ResponseBodyEmitter> emitters = pool.get(equipmentId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        String line;
        try {
            // NDJSON 协议：每行一个完整 JSON 对象，以换行符结尾
            line = objectMapper.writeValueAsString(vo) + "\n";
        } catch (Exception e) {
            log.error("NDJSON 序列化失败，放弃本条推送: equipmentId={}, error={}", equipmentId, e.getMessage());
            return;
        }

        for (ResponseBodyEmitter emitter : emitters) {
            writeLine(equipmentId, emitter, line);
        }
    }

    /**
     * 心跳广播：向所有连接发送单行心跳，防止代理/网关因空闲断开长连接
     */
    @Scheduled(fixedRate = 30000)
    public void heartbeat() {
        if (pool.isEmpty()) {
            return;
        }
        String line = "{\"type\":\"heartbeat\"}\n";
        pool.forEach((equipmentId, emitters) -> {
            for (ResponseBodyEmitter emitter : emitters) {
                writeLine(equipmentId, emitter, line);
            }
        });
    }

    /**
     * 向单个连接写一行数据，失败则终止并移除该连接
     */
    private void writeLine(Integer equipmentId, ResponseBodyEmitter emitter, String line) {
        try {
            emitter.send(line, MediaType.TEXT_PLAIN);
        } catch (Exception e) {
            log.warn("流式推送失败，关闭该连接: equipmentId={}, error={}", equipmentId, e.getMessage());
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 连接已失效时 complete 可能再次抛异常，忽略即可
            }
            remove(equipmentId, emitter);
        }
    }

    /**
     * 应用关闭时清理所有连接
     */
    @PreDestroy
    public void cleanup() {
        log.info("流式连接池关闭，清理 {} 个设备的连接", pool.size());
        pool.values().forEach(emitters ->
                emitters.forEach(ResponseBodyEmitter::complete));
        pool.clear();
    }
}
