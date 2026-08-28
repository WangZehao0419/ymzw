package com.ruoyi.inspection.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.inspection.entity.InspectionStandard;
import com.ruoyi.inspection.entity.Part;
import com.ruoyi.inspection.entity.query.PartInspectionQuery;
import com.ruoyi.inspection.entity.vo.InspectionResult;
import com.ruoyi.inspection.entity.vo.PartInspectionVO;
import com.ruoyi.inspection.feign.AiFeignClient;
import com.ruoyi.inspection.service.InspectionStandardService;
import com.ruoyi.inspection.service.PartInspectionService;
import com.ruoyi.inspection.service.PartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 零件检测 Service 实现类
 * <p>
 * 使用 MyBatis-Plus 的 Service 层进行数据操作，
 * 通过远程调用 cloud-ai 服务实现智能零件检测
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartInspectionServiceImpl implements PartInspectionService {

    private final PartService partService;
    private final InspectionStandardService inspectionStandardService;
    private final AiFeignClient aiFeignClient;

    @Override
    public InspectionResult inspectSinglePart(Long partId) {
        // 使用 Service 层的 getById 方法查询零件
        Part part = partService.getById(partId);
        if (part == null) {
            return InspectionResult.fail("零件不存在");
        }

        // 使用 Service 层的 getById 方法查询检测标准
        InspectionStandard standard = inspectionStandardService.getById(part.getStandardId());
        if (standard == null) {
            return InspectionResult.fail("未找到对应的检测标准");
        }

        // 执行检测并更新零件检测结果
        InspectionResult result = inspectWithStandard(part, standard);

        // 更新检测结果
        updateInspectionResult(part.getId(), result);
        return result;
    }

    @Override
    public Map<Long, InspectionResult> inspectBatchParts(List<Long> partIds) {
        // 创建一个固定大小为10的线程池，用于并发执行零件检查任务
        // 可根据实际需求调整线程数，以平衡并发度和系统资源
        ExecutorService executor = Executors.newFixedThreadPool(10); // 自定义线程数
        try {
            // 步骤1：将每个partId转换为一个异步任务（CompletableFuture）
            // 使用partIds.stream()遍历每个零件ID，对每个ID通过CompletableFuture.supplyAsync提交任务
            // supplyAsync会在指定的线程池(executor)中执行inspectSinglePart(part)方法
            // 返回的CompletableFuture代表异步计算的结果，collect将所有Future收集到一个List中
            List<CompletableFuture<InspectionResult>> futures = partIds.stream()
                    .map(part -> CompletableFuture.supplyAsync(() -> inspectSinglePart(part), executor))
                    .collect(Collectors.toList());

            // 步骤2：创建线程安全的Map，用于存放最终的零件ID -> 检查结果
            Map<Long, InspectionResult> results = new ConcurrentHashMap<>();

            // 步骤3：遍历零件ID列表的索引，依次从对应的Future中获取结果
            // IntStream.range(0, partIds.size())生成从0到size-1的索引流
            // forEach对每个索引i执行操作：从futures中取出第i个Future，调用join()等待其完成并获取结果
            // 然后将零件ID和结果放入results中。由于results是ConcurrentHashMap，多线程put安全
            // 注意：虽然此处是顺序遍历索引，但所有异步任务已经在步骤1中并发执行，join()只是等待任务完成
            IntStream.range(0, partIds.size())
                    .forEach(i -> results.put(partIds.get(i), futures.get(i).join()));

            // 返回包含所有零件检查结果的Map
            return results;
        } finally {
            // 确保线程池在方法结束后被关闭，释放资源
            // 注意：shutdown()会平滑关闭线程池，等待已提交任务执行完毕，但不会阻塞等待
            executor.shutdown();
        }
    }

    /**
     * 基于检测标准进行零件智能检测
     * <p>
     * 通过远程调用 cloud-ai 服务，使用默认 LLM 模型进行智能检测
     * </p>
     *
     * @param part     零件信息
     * @param standard 检测标准
     * @return 检测结果
     */
    @Override
    public InspectionResult inspectWithStandard(Part part, InspectionStandard standard) {
        try {
            // 构建智能体检测提示词
            String prompt = buildInspectionPrompt(part, standard);

            // 调用 cloud-ai 服务进行智能检测
            log.info("开始调用AI服务进行零件检测，零件ID: {}, 零件名称: {}", part.getId(), part.getPartName());
            Map<String, Object> aiResponse = aiFeignClient.chat(prompt);
            log.info("AI服务返回结果: {}", aiResponse);

            // 解析AI返回结果
            InspectionResult inspectionResult = parseAiFeignResponse(aiResponse);

            return inspectionResult;
        } catch (Exception e) {
            log.error("调用AI服务失败: {}", e.getMessage(), e);
            return InspectionResult.fail("AI服务调用失败: " + e.getMessage());
        }
    }

    /**
     * 构建智能检测提示词
     * <p>
     * 将零件信息和检测标准转换为结构化的提示词，
     * 要求AI按照JSON格式返回检测结果，便于解析
     * </p>
     *
     * @param part     零件信息
     * @param standard 检测标准
     * @return 构建好的提示词
     */
    private String buildInspectionPrompt(Part part, InspectionStandard standard) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一名专业的零件检测工程师。请根据以下信息对零件进行检测，并严格按照指定的JSON格式返回结果。\n\n");

        // 零件信息部分
        prompt.append("## 零件信息\n");
        prompt.append("- 零件名称：").append(part.getPartName()).append("\n");
        prompt.append("- 零件代码：").append(part.getPartCode()).append("\n");
        prompt.append("- 零件参数：").append(part.getParameters() != null ? part.getParameters() : "无").append("\n\n");

        // 检测标准部分
        prompt.append("## 检测标准\n");
        prompt.append("- 标准名称：").append(standard.getStandardName()).append("\n");
        prompt.append("- 标准参数：").append(standard.getStandardParameters() != null ? standard.getStandardParameters() : "无").append("\n\n");

        // 输出格式要求
        prompt.append("## 输出要求\n");
        prompt.append("请严格按照以下JSON格式返回检测结果，不要添加任何其他内容：\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"passed\": true或false,\n");
        prompt.append("  \"details\": \"检测详情说明（简练，不超过100字）\",\n");
        prompt.append("  \"suggestion\": \"改进建议（如果合格可填'无'，简练，不超过100字）\"\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        prompt.append("注意：passed字段为布尔值true或false，不要使用字符串。现在请开始检测并返回JSON结果。");

        return prompt.toString();
    }

    /**
     * 解析 AI 服务返回的响应
     * <p>
     * 使用多种解析策略处理 AI 响应：
     * 1. 优先尝试 JSON 格式解析
     * 2. 备选使用正则表达式解析文本格式
     * 3. 解析失败时返回默认失败结果
     * </p>
     *
     * @param map AI 服务返回的 Map 结构响应
     * @return 检测结果对象
     */
    private InspectionResult parseAiFeignResponse(Map<String, Object> map) {
        // 检查响应是否成功
        Object successObj = map.get("success");
        if (successObj != null && Boolean.FALSE.equals(successObj)) {
            String errorMsg = map.get("error") != null ? map.get("error").toString() : "AI服务返回失败";
            log.error("AI服务返回失败: {}", errorMsg);
            throw new RuntimeException(errorMsg);
        }

        // 获取响应内容
        Object responseObj = map.get("response");
        if (responseObj == null) {
            log.warn("AI响应内容为空，返回默认失败结果");
            return new InspectionResult(false, "AI响应内容为空", "请重新检测");
        }

        String response = responseObj.toString();
        log.debug("开始解析AI响应，响应长度: {}, 内容: {}", response.length(), response);

        // 策略1：尝试 JSON 格式解析
        InspectionResult result = tryParseAsJson(response);
        if (result != null) {
            log.info("JSON格式解析成功");
            return result;
        }

        // 策略2：尝试正则表达式解析文本格式
        result = tryParseAsText(response);
        if (result != null) {
            log.info("文本格式解析成功");
            return result;
        }

        // 策略3：解析失败，返回默认结果
        log.warn("无法解析AI响应格式，返回默认失败结果。响应内容: {}", response);
        return new InspectionResult(false, "无法解析AI响应", response);
    }

    /**
     * 尝试将响应解析为 JSON 格式
     * <p>
     * 从响应文本中提取 JSON 对象并解析
     * </p>
     *
     * @param response AI 响应文本
     * @return 解析成功返回 InspectionResult，失败返回 null
     */
    private InspectionResult tryParseAsJson(String response) {
        try {
            // 提取 JSON 内容（处理可能被 markdown 代码块包裹的情况）
            String jsonContent = extractJsonContent(response);
            if (jsonContent == null) {
                return null;
            }

            // 使用 Jackson 解析 JSON
            ObjectMapper objectMapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonMap = objectMapper.readValue(jsonContent, Map.class);

            // 提取字段值
            boolean passed = extractPassedValue(jsonMap);
            String details = extractStringValue(jsonMap, "details", "无检测详情");
            String suggestion = extractStringValue(jsonMap, "suggestion", "无");

            return new InspectionResult(passed, details, suggestion);

        } catch (Exception e) {
            log.debug("JSON解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从响应文本中提取 JSON 内容
     * <p>
     * 支持多种格式：
     * 1. 纯 JSON 字符串
     * 2. markdown 代码块包裹的 JSON（```json ... ```）
     * 3. 混合文本中包含的 JSON 对象
     * </p>
     *
     * @param response 响应文本
     * @return 提取的 JSON 字符串，提取失败返回 null
     */
    private String extractJsonContent(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        String trimmed = response.trim();

        // 尝试提取 markdown 代码块中的 JSON
        Pattern codeBlockPattern = Pattern.compile("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```", Pattern.CASE_INSENSITIVE);
        Matcher codeBlockMatcher = codeBlockPattern.matcher(trimmed);
        if (codeBlockMatcher.find()) {
            return codeBlockMatcher.group(1).trim();
        }

        // 尝试提取纯 JSON 对象
        int startIndex = trimmed.indexOf('{');
        int endIndex = trimmed.lastIndexOf('}');
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return trimmed.substring(startIndex, endIndex + 1);
        }

        return null;
    }

    /**
     * 从 JSON Map 中提取 passed 布尔值
     * <p>
     * 支持布尔值和字符串类型的 passed 字段
     * </p>
     *
     * @param jsonMap JSON Map 对象
     * @return passed 布尔值
     */
    private boolean extractPassedValue(Map<String, Object> jsonMap) {
        Object passedObj = jsonMap.get("passed");
        if (passedObj == null) {
            return false;
        }
        if (passedObj instanceof Boolean) {
            return (Boolean) passedObj;
        }
        // 处理字符串类型的布尔值
        String passedStr = passedObj.toString().toLowerCase();
        return "true".equals(passedStr) || "合格".equals(passedStr);
    }

    /**
     * 从 JSON Map 中提取字符串值
     *
     * @param jsonMap      JSON Map 对象
     * @param key          字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    private String extractStringValue(Map<String, Object> jsonMap, String key, String defaultValue) {
        Object value = jsonMap.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 尝试使用正则表达式解析文本格式的响应
     * <p>
     * 支持解析以下格式：
     * - 检测结果：合格/不合格
     * - 检测详情：xxx
     * - 建议：xxx
     * </p>
     *
     * @param response AI 响应文本
     * @return 解析成功返回 InspectionResult，失败返回 null
     */
    private InspectionResult tryParseAsText(String response) {
        try {
            // 提取检测结果
            boolean passed = false;
            Pattern resultPattern = Pattern.compile("检测结果[：:](.*?)(?:\\n|$)");
            Matcher resultMatcher = resultPattern.matcher(response);
            if (resultMatcher.find()) {
                String resultText = resultMatcher.group(1).trim();
                passed = resultText.contains("合格") && !resultText.contains("不合格");
            } else {
                // 尝试直接匹配合格/不合格
                if (response.contains("合格") && !response.contains("不合格")) {
                    passed = true;
                }
            }

            // 提取检测详情
            String details = "无检测详情";
            Pattern detailsPattern = Pattern.compile("检测详情[：:](.*?)(?=建议[：:]|$)", Pattern.DOTALL);
            Matcher detailsMatcher = detailsPattern.matcher(response);
            if (detailsMatcher.find()) {
                details = detailsMatcher.group(1).trim();
            }

            // 提取建议
            String suggestion = "无";
            Pattern suggestionPattern = Pattern.compile("建议[：:](.*?)(?:\\n|$)", Pattern.DOTALL);
            Matcher suggestionMatcher = suggestionPattern.matcher(response);
            if (suggestionMatcher.find()) {
                suggestion = suggestionMatcher.group(1).trim();
            }

            // 如果至少提取到了检测结果，则认为解析成功
            if (resultMatcher.find() || response.contains("合格") || response.contains("不合格")) {
                return new InspectionResult(passed, details, suggestion);
            }

            return null;

        } catch (Exception e) {
            log.debug("正则表达式解析失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void updateInspectionResult(Long partId, InspectionResult result) {
        // 使用 Service 层的自定义方法更新检测结果
        partService.updateInspectionResult(partId, result.isPassed(), result.getDetails(), result.getSuggestion());
    }

    @Override
    public Part getPartById(Long partId) {
        // 使用 Service 层的 getById 方法
        return partService.getById(partId);
    }

    @Override
    public IPage<PartInspectionVO> page(PartInspectionQuery query) {

        // 创建分页对象
        Page<PartInspectionVO> page = new Page<>(query.getPage(), query.getPageSize());

        // 使用 MyBatis-Plus 的 page 方法进行分页查询
        return partService.getPartInspectionPage(page, query);
    }
}
