package com.ruoyi.inspection.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chroma 向量库同步服务
 * <p>
 * 直接以 Chroma 向量库作为检测标准的数据源：
 * 1. 从 Chroma 读取检测标准知识块并解析为结构化数据
 * 2. 编辑后重新生成文档文本和嵌入向量，回写到 Chroma
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
public class ChromaVectorSyncService {

    @Value("${chroma.host:http://106.53.26.28}")
    private String chromaHost;

    @Value("${chroma.port:8000}")
    private int chromaPort;

    @Value("${chroma.collection-name:ai_knowledge_base}")
    private String collectionName;

    @Value("${dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${dashscope.embedding-url:https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings}")
    private String embeddingUrl;

    @Value("${dashscope.embedding-model:text-embedding-v3}")
    private String embeddingModel;

    private final RestTemplate restTemplate = new RestTemplate();

    private String collectionId;

    private static final Map<String, String> PART_TYPE_NAMES = new LinkedHashMap<>();
    private static final Map<String, String> LABEL_TO_KEY = new LinkedHashMap<>();

    static {
        PART_TYPE_NAMES.put("TITANIUM", "钛合金");
        PART_TYPE_NAMES.put("STEEL", "不锈钢");
        PART_TYPE_NAMES.put("ALUMINUM", "铝合金");
        PART_TYPE_NAMES.put("COPPER", "铜合金");
        PART_TYPE_NAMES.put("COMPOSITE", "复合材料");
        PART_TYPE_NAMES.put("HIGH_TEMP_ALLOY", "高温合金");
        PART_TYPE_NAMES.put("MAGNESIUM", "镁合金");
        PART_TYPE_NAMES.put("PLASTIC", "工程塑料");
        PART_TYPE_NAMES.put("CERAMIC", "陶瓷");
        PART_TYPE_NAMES.put("TUNGSTEN", "钨合金");

        LABEL_TO_KEY.put("硬度", "hardness");
        LABEL_TO_KEY.put("公差", "tolerance");
        LABEL_TO_KEY.put("伸长率", "elongation");
        LABEL_TO_KEY.put("屈服强度", "yield_strength");
        LABEL_TO_KEY.put("抗拉强度", "tensile_strength");
        LABEL_TO_KEY.put("疲劳强度", "fatigue_strength");
        LABEL_TO_KEY.put("表面粗糙度", "surface_roughness");
        LABEL_TO_KEY.put("尺寸精度", "dimension_accuracy");
        LABEL_TO_KEY.put("密度", "density");
        LABEL_TO_KEY.put("导热系数", "thermal_conductivity");
        LABEL_TO_KEY.put("导电率", "electrical_conductivity");
        LABEL_TO_KEY.put("耐腐蚀性", "corrosion_resistance");
        LABEL_TO_KEY.put("蠕变强度", "creep_strength");
        LABEL_TO_KEY.put("耐热温度", "heat_resistance");
        LABEL_TO_KEY.put("抗氧化性", "oxidation_resistance");
        LABEL_TO_KEY.put("弯曲强度", "flexural_strength");
        LABEL_TO_KEY.put("层间剪切强度", "interlaminar_shear_strength");
        LABEL_TO_KEY.put("热膨胀系数", "thermal_expansion_coefficient");
        LABEL_TO_KEY.put("阻燃等级", "flame_retardancy");
        LABEL_TO_KEY.put("介电强度", "dielectric_strength");
        LABEL_TO_KEY.put("热变形温度", "heat_deflection_temp");
        LABEL_TO_KEY.put("耐磨性", "wear_resistance");
        LABEL_TO_KEY.put("断裂韧性", "fracture_toughness");
        LABEL_TO_KEY.put("介电常数", "dielectric_constant");
        LABEL_TO_KEY.put("抗压强度", "compressive_strength");
        LABEL_TO_KEY.put("弹性模量", "elastic_modulus");
        LABEL_TO_KEY.put("辐射屏蔽", "radiation_shielding");
    }

    private static final Map<String, String> KEY_TO_LABEL = new LinkedHashMap<>();

    static {
        LABEL_TO_KEY.forEach((label, key) -> KEY_TO_LABEL.put(key, label));
    }

    @PostConstruct
    public void init() {
        try {
            this.collectionId = fetchCollectionId();
            if (collectionId != null) {
                log.info("Chroma向量库连接成功，集合ID: {}", collectionId);
            } else {
                log.warn("Chroma向量库集合 {} 不存在，向量同步将不可用", collectionName);
            }
        } catch (Exception e) {
            log.warn("Chroma向量库连接失败，向量同步将不可用: {}", e.getMessage());
        }
    }

    /**
     * 获取 Chroma 集合 ID
     */
    private String fetchCollectionId() {
        try {
            String url = chromaHost + ":" + chromaPort + "/api/v1/collections/" + collectionName;
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JSONObject json = JSON.parseObject(resp.getBody());
                return json.getString("id");
            }
        } catch (Exception e) {
            log.warn("获取Chroma集合ID失败: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 读取：从 Chroma 获取所有检测标准知识块 ====================

    /**
     * 从 Chroma 读取所有检测标准知识块，并解析为结构化数据
     *
     * @return 知识块列表，每项包含 docId, partType, standardName, description, parameters, rawContent, uploadTime
     */
    public List<Map<String, Object>> getAllStandardBlocks() {
        List<Map<String, Object>> result = new ArrayList<>();
        if (collectionId == null) {
            log.warn("Chroma集合未初始化");
            return result;
        }

        try {
            String url = chromaHost + ":" + chromaPort + "/api/v1/collections/" + collectionId + "/get";

            JSONObject body = new JSONObject();
            JSONObject where = new JSONObject();
            where.put("category", "检测标准知识库");
            body.put("where", where);
            body.put("include", Arrays.asList("documents", "metadatas"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(body.toJSONString(), headers), String.class);

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JSONObject data = JSON.parseObject(resp.getBody());
                JSONArray ids = data.getJSONArray("ids");
                JSONArray documents = data.getJSONArray("documents");
                JSONArray metadatas = data.getJSONArray("metadatas");

                if (ids != null) {
                    for (int i = 0; i < ids.size(); i++) {
                        String docId = ids.getString(i);
                        String docContent = (documents != null && i < documents.size()) ? documents.getString(i) : "";
                        JSONObject meta = (metadatas != null && i < metadatas.size()) ? metadatas.getJSONObject(i) : new JSONObject();

                        Map<String, Object> parsed = parseDocumentText(docContent);
                        parsed.put("docId", docId);
                        parsed.put("rawContent", docContent);
                        parsed.put("filename", meta.getString("filename"));
                        parsed.put("uploadTime", meta.getString("upload_time"));
                        parsed.put("category", meta.getString("category"));

                        result.add(parsed);
                    }
                }
            }

            // 按 partType 排序
            result.sort((a, b) -> {
                List<String> order = new ArrayList<>(PART_TYPE_NAMES.keySet());
                int ia = order.indexOf(a.get("partType"));
                int ib = order.indexOf(b.get("partType"));
                return Integer.compare(ia < 0 ? 999 : ia, ib < 0 ? 999 : ib);
            });

            log.info("从Chroma读取到 {} 条检测标准知识块", result.size());
        } catch (Exception e) {
            log.error("从Chroma读取检测标准失败: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * 解析知识块文本内容为结构化字段
     * <p>
     * 解析格式如：
     * 钛合金零件检测标准 (TITANIUM)
     * -------------------------------------------
     * 适用领域: 航空航天领域
     *
     * 检测参数标准:
     * - 硬度: HRC30-35
     * - 公差: ≤0.01mm
     * ...
     *
     * 应用说明:
     * ...
     * </p>
     */
    private Map<String, Object> parseDocumentText(String text) {
        Map<String, Object> result = new HashMap<>();
        Map<String, String> parameters = new LinkedHashMap<>();

        String partType = "";
        String partTypeName = "";
        String description = "";
        String applicationNote = "";

        // 1. 提取零件类型代码，如 (TITANIUM)
        Matcher typeMatcher = Pattern.compile("\\(([A-Z_]+)\\)").matcher(text);
        if (typeMatcher.find()) {
            partType = typeMatcher.group(1);
            partTypeName = PART_TYPE_NAMES.getOrDefault(partType, partType);
        }

        // 2. 按行解析
        String[] lines = text.split("\n");
        boolean inParams = false;
        boolean inApplication = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // 适用领域
            if (trimmed.startsWith("适用领域:") || trimmed.startsWith("适用领域：")) {
                description = trimmed.substring(trimmed.indexOf(":") + 1).trim();
                if (description.isEmpty() && trimmed.indexOf("：") > 0) {
                    description = trimmed.substring(trimmed.indexOf("：") + 1).trim();
                }
                continue;
            }

            // 检测参数标准
            if (trimmed.startsWith("检测参数标准")) {
                inParams = true;
                inApplication = false;
                continue;
            }

            // 应用说明
            if (trimmed.startsWith("应用说明")) {
                inParams = false;
                inApplication = true;
                continue;
            }

            // 解析参数行：- 硬度: HRC30-35
            if (inParams && trimmed.startsWith("- ")) {
                String paramLine = trimmed.substring(2);
                int colonIdx = paramLine.indexOf(":");
                if (colonIdx < 0) colonIdx = paramLine.indexOf("：");
                if (colonIdx > 0) {
                    String label = paramLine.substring(0, colonIdx).trim();
                    String value = paramLine.substring(colonIdx + 1).trim();
                    String key = LABEL_TO_KEY.getOrDefault(label, label);
                    parameters.put(key, value);
                }
            }

            // 应用说明内容
            if (inApplication && !trimmed.isEmpty() && !trimmed.startsWith("---")) {
                applicationNote = trimmed;
            }
        }

        result.put("partType", partType);
        result.put("partTypeName", partTypeName);
        result.put("standardName", partTypeName + "零件检测标准");
        result.put("description", description);
        result.put("applicationNote", applicationNote);
        result.put("parameters", parameters);

        return result;
    }

    // ==================== 写入：更新知识块到 Chroma ====================

    /**
     * 更新指定知识块：删除旧文档 → 用相同 ID 写入新文档
     *
     * @param docId          原有知识块的 UUID
     * @param partType       零件类型代码
     * @param description    适用领域描述
     * @param applicationNote 应用说明
     * @param parameters     检测参数 Map
     * @return 是否成功
     */
    public boolean updateKnowledgeBlock(String docId, String partType, String description,
                                         String applicationNote, Map<String, String> parameters) {
        if (collectionId == null) {
            log.warn("Chroma集合未初始化");
            return false;
        }

        try {
            // 1. 构建新的文档文本
            String document = buildDocumentFromFields(partType, description, applicationNote, parameters);

            // 2. 生成新的嵌入向量
            List<Float> embedding = getEmbedding(document);
            if (embedding == null || embedding.isEmpty()) {
                log.error("生成嵌入向量失败");
                return false;
            }

            // 3. 删除旧文档
            deleteByIds(Collections.singletonList(docId));

            // 4. 用相同 ID 写入新文档
            String partTypeName = PART_TYPE_NAMES.getOrDefault(partType, partType);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "检测标准知识库.txt");
            metadata.put("category", "检测标准知识库");
            metadata.put("filename", partTypeName + "零件检测标准 (" + partType + ")");
            metadata.put("upload_time", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

            upsertToChroma(docId, document, metadata, embedding);

            log.info("知识块已更新: docId={}, partType={}", docId, partType);
            return true;
        } catch (Exception e) {
            log.error("更新知识块失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从向量库删除指定知识块
     *
     * @param docId 知识块 UUID
     * @return 是否成功
     */
    public boolean deleteKnowledgeBlock(String docId) {
        if (collectionId == null) {
            return false;
        }
        try {
            deleteByIds(Collections.singletonList(docId));
            log.info("已从向量库删除知识块: {}", docId);
            return true;
        } catch (Exception e) {
            log.error("删除知识块失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 根据字段构建文档文本（与 检测标准知识库.txt 格式一致）
     */
    private String buildDocumentFromFields(String partType, String description,
                                            String applicationNote, Map<String, String> parameters) {
        String partTypeName = PART_TYPE_NAMES.getOrDefault(partType, partType);

        StringBuilder sb = new StringBuilder();
        sb.append(partTypeName).append("零件检测标准 (").append(partType).append(")\n");
        sb.append("-------------------------------------------\n");
        sb.append("适用领域: ").append(description != null ? description : "").append("\n");
        sb.append("\n检测参数标准:\n");

        if (parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                String label = KEY_TO_LABEL.getOrDefault(entry.getKey(), entry.getKey());
                sb.append("- ").append(label).append(": ").append(entry.getValue()).append("\n");
            }
        }

        sb.append("\n应用说明: \n");
        if (applicationNote != null && !applicationNote.isEmpty()) {
            sb.append(applicationNote).append("\n");
        } else {
            sb.append(partTypeName).append("检测标准\n");
        }

        return sb.toString();
    }

    // ==================== Chroma & Embedding 底层方法 ====================

    private void deleteByIds(List<String> ids) {
        String url = chromaHost + ":" + chromaPort + "/api/v1/collections/" + collectionId + "/delete";
        JSONObject body = new JSONObject();
        body.put("ids", ids);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.postForEntity(url, new HttpEntity<>(body.toJSONString(), headers), String.class);
        } catch (Exception e) {
            log.warn("删除Chroma条目异常: {}", e.getMessage());
        }
    }

    private void upsertToChroma(String docId, String document, Map<String, Object> metadata, List<Float> embedding) {
        String url = chromaHost + ":" + chromaPort + "/api/v1/collections/" + collectionId + "/upsert";
        JSONObject body = new JSONObject();
        body.put("ids", Collections.singletonList(docId));
        body.put("documents", Collections.singletonList(document));
        body.put("metadatas", Collections.singletonList(metadata));
        JSONArray embeddingArray = new JSONArray();
        embeddingArray.add(new JSONArray(embedding));
        body.put("embeddings", embeddingArray);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                url, new HttpEntity<>(body.toJSONString(), headers), String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Chroma upsert 失败: " + resp.getStatusCode());
        }
    }

    private List<Float> getEmbedding(String text) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", embeddingModel);
            body.put("input", Collections.singletonList(text));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + dashscopeApiKey);
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    embeddingUrl, new HttpEntity<>(body.toJSONString(), headers), String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JSONObject result = JSON.parseObject(resp.getBody());
                JSONArray data = result.getJSONArray("data");
                if (data != null && !data.isEmpty()) {
                    return data.getJSONObject(0).getJSONArray("embedding").toJavaList(Float.class);
                }
            }
        } catch (Exception e) {
            log.error("调用 DashScope Embedding API 失败: {}", e.getMessage());
        }
        return null;
    }
}
