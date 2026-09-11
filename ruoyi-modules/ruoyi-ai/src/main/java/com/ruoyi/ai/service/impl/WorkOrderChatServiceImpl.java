package com.ruoyi.ai.service.impl;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.ai.entity.vo.AiAgentVO;
import com.ruoyi.ai.enums.AgentTypeEnum;
import com.ruoyi.ai.service.AiAgentService;
import com.ruoyi.ai.service.WorkOrderChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * 维保工单智能助手流式对话实现
 * <p>
 * 构建带 MCP 工具的 ReactAgent：模型走"CHAT 智能体优先 + 默认 ChatModel 兜底"，
 * 工具来自 MCP client starter 自动配置的 ToolCallbackProvider（指向 ruoyi-mcp 的
 * 6 个维保工单工具），多轮记忆由共享 MemorySaver 按 threadId(conversationId) 隔离。
 * 输出统一包装为前端约定的 NDJSON 行（delta/error/done）。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
public class WorkOrderChatServiceImpl implements WorkOrderChatService {

    /**
     * 工单助手系统提示词：约束工具调用纪律——填参前先查清单、创建/完成/转派/取消前先向用户确认、
     * 工具失败如实转告不编造，这是答辩演示"可追溯工单操作"的关键约束。
     */
    private static final String SYSTEM_PROMPT = "你是云眸智维平台的维保工单智能助手。你可以通过工具管理维保工单的全生命周期。\n"
            + "工作规则：\n"
            + "1. 用户要求创建工单时，先调用 list_equipment_sensors 查询设备与传感器清单，用返回的准确 ID 和名称填参；若用户描述的设备或传感器在清单中不存在，告知用户可用清单并请其确认。\n"
            + "2. 调用 create_work_order 前，先向用户复述关键信息（设备、传感器、工单类型、描述、级别）并等待确认；用户确认后再创建。\n"
            + "3. 创建成功后，把工单号（WO 开头）明确告诉用户。\n"
            + "4. 查询工单用 query_work_orders；完成、转派、取消工单前同样先向用户确认。\n"
            + "5. 工具返回 success=false 时，把 error 原因转告用户，不要编造结果。\n"
            + "6. 与工单无关的问题礼貌说明自己的职责范围。回复保持简洁中文。";

    /** 流结束标记行（常量提前序列化，避免每请求重复构造） */
    private static final String DONE_LINE = JSON.toJSONString(Map.of("type", "done"));

    private final AiAgentService aiAgentService;

    /** 默认 ChatModel：由 spring-ai-starter-model-openai 按 Nacos 配置自动装配（qwen-plus） */
    private final ChatModel defaultChatModel;

    /**
     * MCP 工具回调提供者：spring-ai-starter-mcp-client 自动配置的 Bean（含 ruoyi-mcp 的工单工具）。
     * @Lazy 注入配合 McpClientLazyConfig，把 MCP 连接推迟到首次对话——
     * ruoyi-mcp 不在线时本服务仍可正常启动。
     */
    private final ToolCallbackProvider toolCallbackProvider;

    /** 共享会话记忆（AgentSaverConfig 单例），按 threadId(conversationId) 隔离多轮上下文 */
    private final MemorySaver chatMemorySaver;

    public WorkOrderChatServiceImpl(AiAgentService aiAgentService,
                                    ChatModel defaultChatModel,
                                    @Lazy ToolCallbackProvider toolCallbackProvider,
                                    MemorySaver chatMemorySaver) {
        this.aiAgentService = aiAgentService;
        this.defaultChatModel = defaultChatModel;
        this.toolCallbackProvider = toolCallbackProvider;
        this.chatMemorySaver = chatMemorySaver;
    }

    @Override
    public Flux<String> chat(String message, String conversationId) {
        log.info("[系统智能对话] 开始: conversationId={}, 消息长度: {}", conversationId, message.length());
        try {
            ReactAgent agent = ReactAgent.builder()
                    .name("work-order-assistant")
                    .model(resolveChatModel())
                    .systemPrompt(SYSTEM_PROMPT)
                    .toolCallbackProviders(toolCallbackProvider)
                    .saver(chatMemorySaver)
                    .build();

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(conversationId)
                    .build();

            // 正文增量逐段包装为 delta 行；异常降级为 error 行；无论成败末尾统一补 done 行。
            // concat 保证 done 一定在流末尾（error 后也会发出，前端可据此安全关闭流）。
            return Flux.concat(
                            agent.stream(message, config)
                                    .flatMap(output -> {
                                        // 只取模型正文流式增量（AGENT_MODEL_STREAMING），
                                        // reasoningContent 思考过程不向前端输出
                                        if (output instanceof StreamingOutput streamingOutput
                                                && streamingOutput.getOutputType() == OutputType.AGENT_MODEL_STREAMING
                                                && streamingOutput.message() instanceof AssistantMessage assistantMessage) {
                                            String text = assistantMessage.getText();
                                            if (StringUtils.hasText(text)) {
                                                return Flux.just(deltaLine(text));
                                            }
                                        }
                                        return Flux.empty();
                                    })
                                    // MCP Server 不在线/工具调用失败等一切异常统一转 error 行，
                                    // 直接使用异常 message（连接类异常本身即含 MCP 工具服务不可用语义）
                                    .onErrorResume(e -> {
                                        log.error("[工单对话] 流式处理失败: {}", e.getMessage(), e);
                                        return Flux.just(errorLine(e.getMessage()));
                                    }),
                            Flux.just(DONE_LINE))
                    // agent 图构建与 LLM 阻塞调用切到弹性线程池，不占用 Web 容器请求线程
                    .subscribeOn(Schedulers.boundedElastic());

        } catch (Exception e) {
            // Agent 构建/工具装配失败（如 MCP Bean 首次创建即连接失败）同样以 error + done 收尾
            log.error("[工单对话] 创建 Agent 失败: {}", e.getMessage(), e);
            return Flux.just(errorLine(e.getMessage()), DONE_LINE);
        }
    }

    /**
     * 解析对话用 ChatModel：CHAT 类型智能体优先，未配置则默认模型兜底。
     * <p>
     * 为什么不用 AiClientFactory：工厂的 createChatModel(Long modelId) 需要先查模型 ID，
     * 而 getEnabledAgentByType 直接给出 VO（含 endpoint/key/模型标识），照 DiagnosisServiceImpl
     * 的既有模式按 OpenAI 兼容方式自行组装，不改动工厂既有行为。
     * </p>
     */
    private ChatModel resolveChatModel() {
        AiAgentVO agent = aiAgentService.getEnabledAgentByType(AgentTypeEnum.CHAT.getCode());
        if (agent == null) {
            // 比赛演示开箱即用兜底：未配置 CHAT 智能体时用 Nacos 配置自动装配的 qwen-plus
            log.info("[工单对话] 使用默认 ChatModel（qwen-plus）");
            return defaultChatModel;
        }
        log.info("[工单对话] 使用 CHAT 智能体: {} (ID: {})", agent.getAgentName(), agent.getId());
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(agent.getApiEndpoint())
                .apiKey(agent.getApiKey())
                .build();
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(agent.getModelIdentifier());
        if (agent.getTemperature() != null) {
            options.temperature(agent.getTemperature());
        }
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options.build())
                .build();
    }

    /** 增量文本行（fastjson2 负责 JSON 转义，换行/引号等特殊字符安全） */
    private String deltaLine(String text) {
        return JSON.toJSONString(Map.of("type", "delta", "content", text));
    }

    /** 错误行 */
    private String errorLine(String message) {
        return JSON.toJSONString(Map.of("type", "error", "message", message));
    }
}
