package com.ruoyi.ai.service.impl;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.ruoyi.ai.service.AiClientFactory;
import com.ruoyi.ai.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * 对话服务实现类
 * <p>
 * 提供基于ChatClient和ReactAgent的对话能力
 * 支持RAG向量检索增强
 * </p>
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private final AiClientFactory clientFactory;
//    private final VectorStore vectorStore;

    public ChatServiceImpl(AiClientFactory clientFactory
//                           VectorStore vectorStore,
                           ) {
        this.clientFactory = clientFactory;
    }


    @Override
    public Flux<String> chatStream(Long modelId, String message) {
        ChatClient chatClient = clientFactory.createChatClient(modelId);

        ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt();
        promptSpec.user(message);
//        promptSpec.advisors(QuestionAnswerAdvisor.builder(vectorStore)
//                .searchRequest(SearchRequest.builder()
//                        .query(message)
//                        .similarityThreshold(0.1d)
//                        .topK(6)
//                        .build())
//                .build());
        return promptSpec
                .stream()
                .content();
    }

    @Override
    public Flux<String> chatStreamWithAgent(Long modelId, String message, String conversationId) {
        log.info("使用ReactAgent进行流式对话，模型ID: {}, 会话ID: {}, 消息长度: {}",
                modelId, conversationId, message.length());

        try {
            log.info("ReactAgent工具注册: DateTimeTools, WebSearchTools, DocumentSearchTool, ApiCallTool(NEW)");

            ChatModel chatModel = clientFactory.createChatModel(modelId);

            ReactAgent agent = ReactAgent.builder()
                    .name("smartartisan-assistant")
                    .model(chatModel)
                    .saver(new MemorySaver())
                    .build();

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(conversationId)
                    .build();

            return agent.stream(message, config)
                    .flatMap(output -> {
                        if (output instanceof StreamingOutput streamingOutput) {
                            OutputType type = streamingOutput.getOutputType();
                            Message msg = streamingOutput.message();

                            if (type == OutputType.AGENT_MODEL_STREAMING) {
                                if (msg instanceof AssistantMessage assistantMessage) {
                                    Object reasoningContent = assistantMessage.getMetadata().get("reasoningContent");
                                    if (reasoningContent != null && !reasoningContent.toString().isEmpty()) {
                                        System.out.print(reasoningContent);
                                        return Flux.just("[Thinking] " + reasoningContent);
                                    } else {
                                        return Flux.just(assistantMessage.getText());
                                    }
                                }
                            }
                        }
                        return Flux.empty();
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume(e -> {
                        log.error("Agent流式对话失败: {}", e.getMessage(), e);
                        return Flux.just("错误: " + e.getMessage());
                    });

        } catch (Exception e) {
            log.error("创建ReactAgent失败: {}", e.getMessage(), e);
            return Flux.just("错误: 创建Agent失败 - " + e.getMessage());
        }
    }
}
