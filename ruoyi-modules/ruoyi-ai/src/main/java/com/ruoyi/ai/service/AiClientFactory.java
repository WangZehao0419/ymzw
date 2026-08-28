package com.ruoyi.ai.service;

import com.ruoyi.ai.entity.AiAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * AI客户端工厂接口
 * <p>
 * 提供根据智能体配置动态创建AI客户端的能力
 * </p>
 *
 * @author smartartisan
 */
public interface AiClientFactory {

    ChatClient createChatClient(Long modelId);

    ChatClient createChatClient(AiAgent agent);

    EmbeddingModel createEmbeddingModel(Long modelId);

    EmbeddingModel createEmbeddingModel(AiAgent agent);

    ChatModel createChatModel(Long modelId);

    ChatModel createChatModel(AiAgent agent);
}
