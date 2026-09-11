package com.ruoyi.ai.service;

import reactor.core.publisher.Flux;

/**
 * 维保工单智能助手流式对话服务
 * <p>
 * 基于 ReactAgent + MCP 工具（ruoyi-mcp 维保工单 Server）的对话能力，
 * 返回 NDJSON 行流：每行形如 {"type":"delta","content":"增量文本"} /
 * {"type":"error","message":"..."} / {"type":"done"}
 * </p>
 *
 * @author smartartisan
 */
public interface WorkOrderChatService {

    /**
     * 维保工单流式对话
     *
     * @param message        用户消息
     * @param conversationId 会话 ID（同时作为 ReactAgent 的 threadId，用于多轮记忆隔离）
     * @return 每个元素为完整一行 NDJSON JSON 字符串（不含换行符，由 Controller 拼行写出）
     */
    Flux<String> chat(String message, String conversationId);
}
