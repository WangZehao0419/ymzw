package com.ruoyi.ai.config;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ReactAgent 共享会话记忆配置
 * <p>
 * 提供 MemorySaver 全局单例 Bean，供工单助手等 ReactAgent 对话场景共享。
 * </p>
 *
 * @author smartartisan
 */
@Configuration
public class AgentSaverConfig {

    /**
     * 为什么必须是全局单例：MemorySaver 按 threadId（即 conversationId）隔离会话 checkpoint，
     * 若每次请求 new 一个 saver，上一轮对话的状态全部丢失，多轮记忆无从谈起；
     * 单例共享后同一 conversationId 的多轮请求才能读写同一份历史消息，
     * 不同 conversationId 之间互不干扰，天然按会话隔离。
     */
    @Bean
    public MemorySaver chatMemorySaver() {
        return new MemorySaver();
    }
}
