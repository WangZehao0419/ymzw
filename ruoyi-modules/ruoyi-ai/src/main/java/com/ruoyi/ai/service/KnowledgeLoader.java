package com.ruoyi.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 维保知识语料装载器（启动时执行）
 * <p>
 * 启动时检测 Qdrant collection pdm_knowledge 是否为空：
 * 为空则读取 classpath:knowledge/*.md，按条目分隔符分块后
 * 写入 VectorStore（写入时由 QdrantVectorStore 内置的 EmbeddingModel
 * 即 text-embedding-v3 生成向量，与检索侧使用同一模型，保证向量空间一致）。
 * <p>
 * VectorStore 使用 @Lazy 注入且 QdrantVectorStore bean 被标记为懒初始化
 * （见 QdrantVectorStoreLazyConfig）：Qdrant 不可达时首次调用才触发 bean 创建，
 * 异常在这里被捕获仅 WARN，不阻断服务启动——按用户决策不做降级，
 * 连接问题推迟到诊断接口运行时自然暴露。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
public class KnowledgeLoader implements ApplicationRunner {

    /** 语料文件位置（classpath 下） */
    private static final String KNOWLEDGE_PATTERN = "classpath:knowledge/*.md";

    /** 知识条目分隔符（Markdown 水平分割线，独占一行） */
    private static final String ENTRY_SEPARATOR_REGEX = "(?m)^---\\s*$";

    /** 条目必含的结构化字段标记：文件级标题块等非条目内容不参与检索 */
    private static final String ENTRY_FIELD_MARKER = "适用设备";

    private final VectorStore vectorStore;

    /**
     * 强制重载开关（默认关闭）。
     * 为什么需要：语料更新后 Qdrant 中已有旧数据、非空检测会跳过装载，
     * 置 true 可先清空本装载器写入的条目再全量重写。
     */
    @Value("${ai.knowledge.force-reload:false}")
    private boolean forceReload;

    public KnowledgeLoader(@Lazy VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!forceReload && hasKnowledge()) {
                log.info("[知识装载] Qdrant 已有语料，跳过装载（更新语料后可设 ai.knowledge.force-reload=true 重载）");
                return;
            }
            List<Document> documents = buildKnowledgeDocuments();
            if (documents.isEmpty()) {
                log.warn("[知识装载] classpath:knowledge 下未找到知识条目，诊断 RAG 将无知识可用");
                return;
            }
            // 先删后写保证幂等：条目 ID 按文件名+序号确定性生成，重复装载不产生重复向量
            vectorStore.delete(documents.stream().map(Document::getId).toList());
            vectorStore.add(documents);
            log.info("[知识装载] 完成，共写入 {} 条维保知识条目", documents.size());
        } catch (Exception e) {
            // Qdrant 不可达只告警不阻断启动：诊断链路运行时再暴露连接问题
            log.warn("[知识装载] 知识语料装载失败（Qdrant 可能未启动，诊断 RAG 将无知识可用）: {}", e.getMessage());
        }
    }

    /**
     * 检测 Qdrant collection 是否已有语料
     * <p>
     * 用一次 topK=1 的相似度检索探测（VectorStore 接口未暴露 count 接口），
     * 该调用同时触发懒加载 bean 的创建与 collection 的自动初始化
     * </p>
     */
    private boolean hasKnowledge() {
        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query("机床 传感器 故障 维护")
                .topK(1)
                .build());
        return hits != null && !hits.isEmpty();
    }

    /**
     * 读取 classpath:knowledge/*.md 并按条目分块为 Document
     */
    private List<Document> buildKnowledgeDocuments() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(KNOWLEDGE_PATTERN);
        List<Document> documents = new ArrayList<>();
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String[] chunks = content.split(ENTRY_SEPARATOR_REGEX);
            int index = 0;
            for (String chunk : chunks) {
                String text = chunk.strip();
                // 跳过文件级标题等非结构化条目，只装载含"适用设备"字段的知识块
                if (text.isEmpty() || !text.contains(ENTRY_FIELD_MARKER)) {
                    continue;
                }
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", filename);
                metadata.put("title", extractTitle(text));
                metadata.put("origin", "knowledge-loader");
                // 确定性 ID：同一语料反复装载时可先删后写，避免向量重复堆积
                documents.add(new Document("knowledge::" + filename + "::" + index, text, metadata));
                index++;
            }
        }
        return documents;
    }

    /**
     * 提取条目标题（首个 ## 行），供 metadata 记录引用来源
     */
    private String extractTitle(String text) {
        return text.lines()
                .filter(line -> line.startsWith("## "))
                .findFirst()
                .map(line -> line.substring(3).strip())
                .orElse("未命名条目");
    }
}
