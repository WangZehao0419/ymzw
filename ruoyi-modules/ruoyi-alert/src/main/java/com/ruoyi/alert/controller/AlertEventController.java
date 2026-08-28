package com.ruoyi.alert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.mapper.AlertEventMapper;
import com.ruoyi.alert.stream.AlertStreamConnectionPool;
import com.ruoyi.common.core.web.page.TableDataInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.concurrent.CompletableFuture;

/**
 * 告警事件查询与流式推送接口
 * <p>
 * 查询侧直接注入 AlertEventMapper(与 AlertPersistenceListener 的用法一致,
 * 仅有单表分页查询,无需再引一层 Service);
 * 推送侧通过 AlertStreamConnectionPool 以 NDJSON 长连接广播告警。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@RestController
@RequestMapping("/api/alert-events")
@RequiredArgsConstructor
public class AlertEventController {

    private final AlertEventMapper alertEventMapper;

    private final AlertStreamConnectionPool connectionPool;

    /**
     * 分页查询告警事件
     * <p>
     * 筛选参数均可选,仅在前端实际传值时才拼接条件,避免无意义的全表扫描条件。
     * </p>
     */
    @GetMapping("/page")
    public TableDataInfo page(@RequestParam(defaultValue = "1") long page,
                              @RequestParam(defaultValue = "10") long size,
                              @RequestParam(required = false) String sensorCode,
                              @RequestParam(required = false) String alertLevel,
                              @RequestParam(required = false) String alertStatus) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<AlertEvent>()
                .eq(StringUtils.hasText(sensorCode), AlertEvent::getSensorCode, sensorCode)
                .eq(StringUtils.hasText(alertLevel), AlertEvent::getAlertLevel, alertLevel)
                .eq(StringUtils.hasText(alertStatus), AlertEvent::getAlertStatus, alertStatus)
                // 告警看板默认关心最新告警,按触发时间倒序
                .orderByDesc(AlertEvent::getTriggerTime);
        Page<AlertEvent> p = alertEventMapper.selectPage(new Page<>(page, size), wrapper);
        return new TableDataInfo(p.getRecords(), p.getTotal());
    }

    /**
     * 告警流式推送(NDJSON 长连接)
     * <p>
     * 超时传 0 表示永不超时,连接存活完全交给心跳保活与客户端断开感知;
     * 鉴权由 StreamAuthInterceptor 在进入本方法前完成。
     * </p>
     */
    @GetMapping(value = "/stream", produces = "application/x-ndjson")
    public ResponseBodyEmitter stream(HttpServletResponse response) {
        // 告警是逐行实时下发,必须关闭 Nginx 等反向代理的响应缓冲,否则消息会被攒批延迟
        response.setHeader("X-Accel-Buffering", "no");
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);
        connectionPool.add(emitter);
        // 初始心跳须异步发送:emitter 的 send 要等方法返回、由 Spring MVC 接管异步上下文后才真正生效
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send("{\"type\":\"heartbeat\"}\n", MediaType.TEXT_PLAIN);
            } catch (Exception e) {
                log.debug("[Stream] 初始心跳发送失败(客户端可能已断开): {}", e.getMessage());
            }
        });
        return emitter;
    }
}
