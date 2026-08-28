package com.ruoyi.ai.service;

import com.ruoyi.ai.entity.vo.AiAgentVO;
import reactor.core.publisher.Flux;

import java.util.stream.DoubleStream;

/**
 * AI对话服务接口
 * <p>
 * 提供基于ChatClient和ReactAgent的对话能力
 * 支持普通对话、流式对话和Agent智能对话
 * </p>
 */
public interface ChatService {

    /**
     * 使用指定模型进行流式对话（传统方式）
     *
     * @param modelId 模型ID
     * @param message 用户消息
     * @return 流式响应
     */
    Flux<String> chatStream(Long modelId, String message);

    /**
     * 使用默认模型进行流式对话（传统方式）
     *
     * @param message 用户消息
     * @return 流式响应
     */
    default Flux<String> chatStream(String message) {
        throw new RuntimeException("未实现");
    }

    /**
     * 使用ReactAgent进行流式对话
     * <p>
     * 利用Agent的智能推理和工具调用能力处理用户消息
     * </p>
     *
     * @param modelId 模型ID
     * @param message 用户消息
     * @param conversationId 会话ID，用于保持对话上下文
     * @return 流式响应
     */
    Flux<String> chatStreamWithAgent(Long modelId, String message, String conversationId);

    /**
     * 使用默认模型和ReactAgent进行流式对话
     *
     * @param message 用户消息
     * @param conversationId 会话ID，用于保持对话上下文
     * @return 流式响应
     */
    default Flux<String> chatStreamWithAgent(String message, String conversationId) {
        throw new RuntimeException("未实现");
    }

    String predictiveAlarm(AiAgentVO agent, Integer equipmentId, Integer sensorId, Double sensorValue);
}
