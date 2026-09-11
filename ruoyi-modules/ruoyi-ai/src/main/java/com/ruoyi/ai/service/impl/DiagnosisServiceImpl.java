package com.ruoyi.ai.service.impl;

import com.ruoyi.ai.entity.DiagnosisRecord;
import com.ruoyi.ai.entity.vo.AiAgentVO;
import com.ruoyi.ai.entity.vo.DiagnosisResultVO;
import com.ruoyi.ai.enums.AgentTypeEnum;
import com.ruoyi.ai.service.AiAgentService;
import com.ruoyi.ai.service.DiagnosisRecordService;
import com.ruoyi.ai.service.DiagnosisService;
import com.ruoyi.ai.service.PredictResultClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备诊断服务实现（诊断智能体核心链路）
 * <p>
 * 流程：预测数据获取（工具①）→ Qdrant RAG 知识检索（工具②）→ LLM 生成 Markdown 报告 → 落库。
 * 所有失败均直接抛出，由 Controller 统一转为错误响应——按用户决策不做降级。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
public class DiagnosisServiceImpl implements DiagnosisService {

    /** RAG 检索条数：topK 过大稀释重点、过小丢知识，5 条覆盖典型故障模式的匹配面 */
    private static final int RAG_TOP_K = 5;

    /**
     * 诊断系统提示词：约束 LLM 只引用给定证据、禁止编造数值、固定 Markdown 报告结构。
     * 数值必须来自预测系统、知识必须来自 RAG 片段——这是答辩演示"可追溯诊断"的关键约束。
     */
    private static final String SYSTEM_PROMPT = """
            你是机床预测性维护诊断专家，根据预测性维护系统的数值证据和维保知识库给出设备故障诊断。

            【证据纪律（最高优先级）】
            1. 只能引用"数值证据"与"维保知识片段"中明确出现的数值与结论，禁止编造任何数值、阈值或结论。
            2. 当"维保知识片段"与当前故障无关时，必须明确声明"未找到相关维保知识"，不得虚构知识依据。
            3. 数值证据与知识片段冲突时，以数值证据为准，并在报告中指出矛盾之处。

            【输出要求】
            使用 Markdown 输出诊断报告，章节固定为：
            # 诊断结论
            一句话结论，并给出紧急程度（正常/关注/紧急）。
            # 数值证据
            逐条引用预测结果与预测告警的关键数值（预测状态/健康度/异常评分/RUL/告警级别等）。
            # 可能故障模式
            按可能性排序，每条注明判断依据（来自数值证据还是知识片段）。
            # 处置建议
            具体可执行的检修动作。
            # 知识来源
            列出引用的知识条目标题；未引用任何知识时写"未找到相关维保知识"。
            """;

    private final PredictResultClient predictResultClient;
    private final DiagnosisRecordService diagnosisRecordService;
    private final AiAgentService aiAgentService;

    /** 默认 ChatModel：由 spring-ai-starter-model-openai 按 application.yml 自动装配（qwen-plus） */
    private final ChatModel defaultChatModel;

    /** Qdrant 向量库：@Lazy 注入配合 QdrantVectorStoreLazyConfig，避免 Qdrant 不可达时阻断启动 */
    private final VectorStore vectorStore;

    public DiagnosisServiceImpl(PredictResultClient predictResultClient,
                                DiagnosisRecordService diagnosisRecordService,
                                AiAgentService aiAgentService,
                                ChatModel defaultChatModel,
                                @Lazy VectorStore vectorStore) {
        this.predictResultClient = predictResultClient;
        this.diagnosisRecordService = diagnosisRecordService;
        this.aiAgentService = aiAgentService;
        this.defaultChatModel = defaultChatModel;
        this.vectorStore = vectorStore;
    }

    @Override
    public DiagnosisResultVO diagnose(Integer equipmentId, String sensorCode) {
        log.info("[诊断] 开始: equipmentId={}, sensorCode={}", equipmentId, sensorCode);

        // 工具①数据获取：拉取最新预测结果与预测告警（失败抛出，无预测数据的传感器无法诊断）
        PredictResultClient.PredictLatestVO latest = predictResultClient.fetchLatest(sensorCode);
        PredictResultClient.PredictResultDTO result = latest.getResult();

        // 工具②RAG 检索：查询词由设备/传感器/类型/预测状态/告警摘要拼接，命中相关维保知识
        List<Document> knowledgeDocs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(buildRagQuery(equipmentId, sensorCode, result, latest.getAlert()))
                .topK(RAG_TOP_K)
                .build());

        // LLM 生成：优先用 DIAGNOSIS 智能体配置，未配置则默认模型兜底
        AiAgentVO agent = aiAgentService.getEnabledAgentByType(AgentTypeEnum.DIAGNOSIS.getCode());
        ChatModel chatModel = resolveChatModel(agent);
        String report = ChatClient.builder(chatModel).build()
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(buildUserMessage(equipmentId, sensorCode, latest, knowledgeDocs))
                .call()
                .content();

        // 报告落库（追溯每一次诊断的完整内容）
        DiagnosisRecord record = diagnosisRecordService.saveReport(equipmentId, sensorCode, report);

        DiagnosisResultVO vo = new DiagnosisResultVO();
        if (agent != null) {
            vo.setAgentId(agent.getId());
            vo.setAgentName(agent.getAgentName());
            vo.setDefaultModel(false);
        } else {
            vo.setAgentName("默认模型");
            vo.setDefaultModel(true);
        }
        vo.setEquipmentId(equipmentId);
        vo.setSensorCode(sensorCode);
        vo.setPredictStatus(result.getStatus());
        vo.setReport(report);
        vo.setRecordId(record.getId());
        vo.setSourceCount(knowledgeDocs.size());
        vo.setSources(knowledgeDocs.stream()
                .map(doc -> String.valueOf(doc.getMetadata().getOrDefault("title", "未命名条目")))
                .toList());

        log.info("[诊断] 完成: sensorCode={}, recordId={}, 知识引用 {} 条", sensorCode, record.getId(), knowledgeDocs.size());
        return vo;
    }

    /**
     * 解析诊断用 ChatModel
     * <p>
     * 为什么不用 AiClientFactory：工厂的 createChatModel 强校验智能体类型为 CHAT，
     * 诊断智能体属 DIAGNOSIS 类型会被拒；这里按工厂同款构建方式自行组装 OpenAI 兼容客户端，
     * 不改动工厂既有校验行为。
     * </p>
     */
    private ChatModel resolveChatModel(AiAgentVO agent) {
        if (agent == null) {
            // 比赛演示开箱即用兜底：未配置 DIAGNOSIS 智能体时用 application.yml
            // 自动装配的默认 ChatModel（qwen-plus），避免演示现场因未建智能体而无法体验诊断
            return defaultChatModel;
        }
        log.info("[诊断] 使用诊断智能体: {} (ID: {})", agent.getAgentName(), agent.getId());
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

    /**
     * 构建 RAG 检索查询词：设备 + 传感器 + 类型 + 预测状态 + 告警信息
     */
    private String buildRagQuery(Integer equipmentId, String sensorCode,
                                  PredictResultClient.PredictResultDTO result,
                                  PredictResultClient.PredictAlertDTO alert) {
        StringBuilder query = new StringBuilder();
        query.append("机床 预测性维护 设备").append(equipmentId)
                .append(" 传感器").append(sensorCode);
        String typeDesc = sensorTypeDesc(sensorCode);
        if (typeDesc != null) {
            query.append(" ").append(typeDesc);
        }
        query.append(" 故障 诊断 维护");
        if (result != null && StringUtils.hasText(result.getStatus())) {
            query.append(" ").append(result.getStatus());
        }
        if (alert != null) {
            if (StringUtils.hasText(alert.getAlertLevel())) {
                query.append(" ").append(alert.getAlertLevel());
            }
            if (StringUtils.hasText(alert.getSummary())) {
                query.append(" ").append(alert.getSummary());
            }
        }
        return query.toString();
    }

    /**
     * 构建 user 消息：数值证据 + RAG 检索片段
     */
    private String buildUserMessage(Integer equipmentId, String sensorCode,
                                    PredictResultClient.PredictLatestVO latest,
                                    List<Document> knowledgeDocs) {
        PredictResultClient.PredictResultDTO result = latest.getResult();
        PredictResultClient.PredictAlertDTO alert = latest.getAlert();

        StringBuilder sb = new StringBuilder();
        sb.append("## 数值证据（来自预测性维护系统，诊断的唯一事实依据）\n");
        sb.append("- 设备: equipmentId=").append(equipmentId)
                .append(", 传感器: ").append(sensorCode);
        String typeDesc = sensorTypeDesc(sensorCode);
        if (typeDesc != null) {
            sb.append("（").append(typeDesc).append("传感器）");
        }
        sb.append("\n");
        sb.append("- 预测状态: ").append(fmt(result.getStatus())).append("\n");
        sb.append("- 健康度得分: ").append(fmt(result.getHealthScore())).append("\n");
        sb.append("- 异常评分: ").append(fmt(result.getAnomalyScore())).append("\n");
        sb.append("- 剩余使用寿命(RUL): 最早失效 ").append(fmt(result.getRulEarliest()))
                .append(" 分钟 / 最晚失效 ").append(fmt(result.getRulLatest())).append(" 分钟\n");
        sb.append("- 预测越限时间: ").append(fmt(result.getPredictedBreachTime())).append("\n");
        sb.append("- 推理模型版本: ").append(fmt(result.getModelVersion())).append("\n");
        sb.append("- 数据更新时间: ").append(fmt(result.getUpdateTime())).append("\n");
        if (alert != null) {
            sb.append("- 最近预测告警: 级别 ").append(fmt(alert.getAlertLevel()))
                    .append(", 状态 ").append(fmt(alert.getAlertStatus()))
                    .append(", 触发时间 ").append(fmt(alert.getTriggerTime())).append("\n");
            sb.append("  - 摘要: ").append(fmt(alert.getSummary())).append("\n");
            sb.append("  - 根因: ").append(fmt(alert.getRootCause())).append("\n");
            sb.append("  - 处置建议: ").append(fmt(alert.getSuggestion())).append("\n");
        } else {
            sb.append("- 最近预测告警: 无\n");
        }

        sb.append("\n## 维保知识片段（Qdrant 向量检索结果）\n");
        if (knowledgeDocs == null || knowledgeDocs.isEmpty()) {
            sb.append("未检索到相关维保知识片段。\n");
        } else {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < knowledgeDocs.size(); i++) {
                Document doc = knowledgeDocs.get(i);
                parts.add("【片段" + (i + 1) + "】" + doc.getMetadata().getOrDefault("title", "未命名条目")
                        + "（来源: " + doc.getMetadata().getOrDefault("source", "unknown") + "）\n"
                        + doc.getText());
            }
            sb.append(String.join("\n\n", parts)).append("\n");
        }
        return sb.toString();
    }

    /**
     * 按传感器编码前缀推导类型描述（TH/TEMP-温度、VIB/VB-振动、HUM/HU-湿度），
     * 用于 RAG 查询词与证据描述；未知前缀返回 null 不影响主流程
     */
    private String sensorTypeDesc(String sensorCode) {
        if (!StringUtils.hasText(sensorCode)) {
            return null;
        }
        String upper = sensorCode.toUpperCase();
        if (upper.startsWith("TEMP") || upper.startsWith("TH")) {
            return "温度";
        }
        if (upper.startsWith("VIB") || upper.startsWith("VB")) {
            return "振动";
        }
        if (upper.startsWith("HUM") || upper.startsWith("HU")) {
            return "湿度";
        }
        return null;
    }

    /** null 值统一显示为"未提供"，避免 LLM 对缺失字段自行臆测 */
    private String fmt(Object value) {
        return value == null ? "未提供" : String.valueOf(value);
    }
}
