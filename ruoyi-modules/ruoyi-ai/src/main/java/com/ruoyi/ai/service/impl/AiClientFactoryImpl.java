package com.ruoyi.ai.service.impl;

import com.ruoyi.ai.entity.AiAgent;
import com.ruoyi.ai.enums.AgentTypeEnum;
import com.ruoyi.ai.service.AiClientFactory;
import com.ruoyi.ai.service.AiAgentService;
import com.ruoyi.ai.enums.ModelStatusEnum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.stereotype.Service;

/**
 * AI客户端工厂实现类
 * <p>
 * 根据数据库中的智能体配置动态创建ChatClient和EmbeddingModel
 * 支持OpenAI兼容的API（如DeepSeek、通义千问等）
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiClientFactoryImpl implements AiClientFactory {

    private final AiAgentService aiAgentService;

    @Override
    public ChatClient createChatClient(Long modelId) {
        if (modelId == null) {
            throw new IllegalArgumentException("智能体ID不能为空");
        }
        AiAgent agent = getEnabledAgent(modelId);
        if (agent == null) {
            throw new IllegalArgumentException("智能体不存在或已禁用，智能体ID: " + modelId);
        }
        log.info("创建ChatClient，智能体ID: {}, 智能体名称: {}", modelId, agent.getAgentName());
        return createChatClient(agent);
    }

    @Override
    public ChatClient createChatClient(AiAgent agent) {
        validateAgent(agent, AgentTypeEnum.CHAT);

        log.info("创建ChatClient - 智能体: {}, API地址: {}", agent.getAgentName(), agent.getApiEndpoint());

        // 创建OpenAiApi实例
        OpenAiApi openAiApi = createOpenAiApi(agent);

        // 构建ChatOptions
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(agent.getModelIdentifier());

        // 设置温度参数
        if (agent.getTemperature() != null) {
            optionsBuilder.temperature(agent.getTemperature());
        }

        // 创建OpenAiChatModel
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();

        // 构建并返回ChatClient
        return ChatClient.builder(chatModel).build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(Long modelId) {
        if (modelId == null) {
            throw new IllegalArgumentException("智能体ID不能为空");
        }
        AiAgent agent = getEnabledAgent(modelId);
        if (agent == null) {
            throw new IllegalArgumentException("智能体不存在或已禁用，智能体ID: " + modelId);
        }
        log.info("创建EmbeddingModel，智能体ID: {}, 智能体名称: {}", modelId, agent.getAgentName());
        return createEmbeddingModel(agent);
    }

    @Override
    public EmbeddingModel createEmbeddingModel(AiAgent agent) {
        validateAgent(agent, AgentTypeEnum.EMBEDDING);

        log.info("创建EmbeddingModel - 智能体: {}, API地址: {}", agent.getAgentName(), agent.getApiEndpoint());

        // 创建OpenAiApi实例
        OpenAiApi openAiApi = createOpenAiApi(agent);

        // 构建EmbeddingOptions
        OpenAiEmbeddingOptions.Builder optionsBuilder = OpenAiEmbeddingOptions.builder()
                .model(agent.getModelIdentifier());

        // 创建OpenAiEmbeddingModel
        return new OpenAiEmbeddingModel(
                openAiApi,
                org.springframework.ai.document.MetadataMode.EMBED,
                optionsBuilder.build(),
                RetryUtils.DEFAULT_RETRY_TEMPLATE
        );
    }

    @Override
    public ChatModel createChatModel(Long modelId) {
        if (modelId == null) {
            throw new IllegalArgumentException("智能体ID不能为空");
        }
        AiAgent agent = getEnabledAgent(modelId);
        if (agent == null) {
            throw new IllegalArgumentException("智能体不存在或已禁用，智能体ID: " + modelId);
        }
        log.info("创建ChatModel，智能体ID: {}, 智能体名称: {}", modelId, agent.getAgentName());
        return createChatModel(agent);
    }

    @Override
    public ChatModel createChatModel(AiAgent agent) {
        validateAgent(agent, AgentTypeEnum.CHAT);

        log.info("创建ChatModel - 智能体: {}, API地址: {}", agent.getAgentName(), agent.getApiEndpoint());

        // 创建OpenAiApi实例
        OpenAiApi openAiApi = createOpenAiApi(agent);

        // 构建ChatOptions
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(agent.getModelIdentifier());

        // 设置温度参数
        if (agent.getTemperature() != null) {
            optionsBuilder.temperature(agent.getTemperature());
        }

        // 创建并返回OpenAiChatModel
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    /**
     * 创建OpenAiApi实例
     * <p>
     * 根据智能体配置创建OpenAI兼容的API客户端
     * 支持自定义API地址（如DeepSeek、通义千问等）
     * </p>
     *
     * @param agent 智能体配置
     * @return OpenAiApi实例
     */
    private OpenAiApi createOpenAiApi(AiAgent agent) {
        return OpenAiApi.builder()
                .baseUrl(agent.getApiEndpoint())
                .apiKey(agent.getApiKey())
                .build();
    }

    /**
     * 验证智能体配置
     *
     * @param agent        智能体配置
     * @param expectedType 期望的智能体类型
     * @throws IllegalArgumentException 验证失败时抛出
     */
    private void validateAgent(AiAgent agent, AgentTypeEnum expectedType) {
        if (agent == null) {
            throw new IllegalArgumentException("智能体配置不能为空");
        }

        if (agent.getApiEndpoint() == null || agent.getApiEndpoint().isBlank()) {
            throw new IllegalArgumentException("智能体API地址不能为空");
        }

        if (agent.getApiKey() == null || agent.getApiKey().isBlank()) {
            throw new IllegalArgumentException("智能体API密钥不能为空");
        }

        if (agent.getModelIdentifier() == null || agent.getModelIdentifier().isBlank()) {
            throw new IllegalArgumentException("智能体标识符不能为空");
        }

        // 验证智能体类型
        if (!expectedType.getCode().equals(agent.getAgentType())) {
            throw new IllegalArgumentException(
                    String.format("智能体类型不匹配，期望: %s，实际: %s", expectedType.getCode(), agent.getAgentType())
            );
        }
    }

    private AiAgent getEnabledAgent(Long agentId) {
        if (agentId == null) return null;
        AiAgent agent = aiAgentService.getById(agentId);
        if (agent != null && ModelStatusEnum.ENABLED.getCode().equals(agent.getStatus())) {
            return agent;
        }
        return null;
    }
}