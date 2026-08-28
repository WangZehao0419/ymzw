package com.ruoyi.ai.service.impl;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.ruoyi.ai.entity.AiAgent;
import com.ruoyi.ai.entity.vo.AiAgentVO;
import com.ruoyi.ai.service.AiClientFactory;
import com.ruoyi.ai.service.AlarmMemoryService;
import com.ruoyi.ai.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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
    private final AlarmMemoryService alarmMemoryService;

    public ChatServiceImpl(AiClientFactory clientFactory,
//                           VectorStore vectorStore,
                           AlarmMemoryService alarmMemoryService) {
        this.clientFactory = clientFactory;
//        this.vectorStore = vectorStore;
        this.alarmMemoryService = alarmMemoryService;
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

    @Override
    public String predictiveAlarm(AiAgentVO agent, Integer equipmentId, Integer sensorId, Double sensorValue) {
        log.info("预测性告警: 设备={}, 传感器={}, 数值={}", equipmentId, sensorId, sensorValue);

        // 1. 构建传感器读数 Map，供短期记忆存储
        Map<String, Object> reading = new HashMap<>();
        reading.put("equipmentId", equipmentId);
        reading.put("sensorId", sensorId);
        reading.put("value", sensorValue);
        reading.put("ts", LocalDateTime.now().toString());

        // 2. 读取短期记忆，构建上下文（AlarmMemoryService 内部使用 String 构建 Redis key）
        String memoryContext = alarmMemoryService.buildMemoryContext(
                String.valueOf(equipmentId), String.valueOf(sensorId));

        // 3. 构建增强版系统提示词，注入时序记忆
        String systemPrompt = """
                你是工业设备预测性告警助手。
                根据传感器历史时序数据和当前读数，判断是否存在异常趋势，
                给出预测性告警建议。

                %s

                请基于以上历史数据，分析当前传感器读数（值为 %s）的趋势变化，
                判断是否存在：
                - 瞬时异常（突刺/突降）
                - 趋势漂移（缓慢上升或下降）
                - 周期性波动异常
                输出简洁的预测结论和告警级别（正常/注意/警告/严重）。
                """.formatted(memoryContext, sensorValue);

        try {
            // 4. 构建 ReactAgent
            AiAgent aiAgent = new AiAgent();
            BeanUtils.copyProperties(agent, aiAgent);
            ChatModel chatModel = clientFactory.createChatModel(aiAgent);

            ReactAgent reactAgent = ReactAgent.builder()
                    .name("predictiveAlarm-assistant")
                    .model(chatModel)
                    .systemPrompt(systemPrompt)
                    .saver(new MemorySaver())
                    .build();

            // 5. 执行推理
            String userMessage = "设备 %s 传感器 %s 当前读数: %s".formatted(equipmentId, sensorId, sensorValue);
            String result = reactAgent.call(userMessage, RunnableConfig.builder().build())
                    .getText();

            // 6. 写入短期记忆滑动窗口
            alarmMemoryService.pushShortTerm(
                    String.valueOf(equipmentId), String.valueOf(sensorId), reading);

            log.info("预测性告警完成: 设备={}, 传感器={}", equipmentId, sensorId);
            return result;

        } catch (Exception e) {
            log.error("预测性告警执行失败: {}", e.getMessage(), e);
            return "错误: 预测性告警执行失败 - " + e.getMessage();
        }
    }
}
