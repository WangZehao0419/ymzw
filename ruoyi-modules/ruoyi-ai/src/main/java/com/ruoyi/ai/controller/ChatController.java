package com.ruoyi.ai.controller;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.ai.service.WorkOrderChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * 维保工单智能助手对话控制器
 * <p>
 * 前端 POST /ai/chat，请求体 {message, conversationId}，
 * 响应 application/x-ndjson：每行一个 JSON 对象——
 * {"type":"delta","content":"增量文本"} / {"type":"error","message":"..."} / {"type":"done"}。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "维保工单对话", description = "MCP 工具驱动的维保工单助手流式对话（NDJSON）")
public class ChatController {

    private final WorkOrderChatService workOrderChatService;

    /**
     * 为什么用 StreamingResponseBody 而不是直接返回 Flux&lt;String&gt;：
     * 项目内没有 Flux 返回值的 Controller 先例（既有用法都是 collectList+block 整体返回），
     * StreamingResponseBody 由 Spring MVC 异步线程执行回调，逐行 write+flush，
     * NDJSON 行格式与换行符完全由本端控制，流式行为最确定；
     * toIterable() 在异步线程里逐元素阻塞等待（而非 collectList 聚合），保证 token 级到达前端。
     */
    @PostMapping(value = "/chat", produces = "application/x-ndjson")
    @Operation(summary = "维保工单流式对话", description = "NDJSON 流式响应：delta 增量文本 / error 错误 / done 结束")
    public StreamingResponseBody chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        String conversationId = body.get("conversationId");

        // conversationId 缺失时自动生成，宽容处理（前端未传也能完成本次对话，只是无多轮记忆）
        if (!StringUtils.hasText(conversationId)) {
            conversationId = UUID.randomUUID().toString();
            log.info("[工单对话] conversationId 缺失，已自动生成: {}", conversationId);
        }

        // message 缺失属调用方错误：直接以 error + done 两行收尾，不进入 Agent 链路
        if (!StringUtils.hasText(message)) {
            log.warn("[工单对话] message 参数缺失, conversationId: {}", conversationId);
            return errorResponse("message 参数缺失");
        }

        log.info("[工单对话] 收到请求: conversationId={}, 消息长度: {}", conversationId, message.length());
        Flux<String> lines = workOrderChatService.chat(message, conversationId);
        return outputStream -> {
            for (String line : lines.toIterable()) {
                // 每个元素已是完整一行 JSON，补换行符构成 NDJSON；逐行 flush 保证流式到达
                outputStream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
        };
    }

    /** 参数错误响应：error 行 + done 行（与正常流的结构保持一致，前端统一按行解析） */
    private StreamingResponseBody errorResponse(String message) {
        return outputStream -> {
            outputStream.write((JSON.toJSONString(Map.of("type", "error", "message", message)) + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            outputStream.write((JSON.toJSONString(Map.of("type", "done")) + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        };
    }
}
