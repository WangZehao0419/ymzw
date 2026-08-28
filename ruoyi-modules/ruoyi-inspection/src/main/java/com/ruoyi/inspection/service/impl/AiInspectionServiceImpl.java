package com.ruoyi.inspection.service.impl;

import com.ruoyi.inspection.entity.dto.PartInspectionDTO;
import com.ruoyi.inspection.entity.vo.PartInspectionResultVO;
import com.ruoyi.inspection.service.AiInspectionService;
import com.ruoyi.inspection.service.PartService;
import com.ruoyi.inspection.service.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI检测服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInspectionServiceImpl implements AiInspectionService {

    private static final int STATUS_FAIL = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_NOT_FOUND = 2;

    private static final Set<String> UPPER_BOUND_KEYS = Set.of(
            "tolerance", "surface_roughness", "dimension_accuracy", "thermal_expansion_coefficient"
    );

    private static final Set<String> LOWER_BOUND_KEYS = Set.of(
            "elongation", "yield_strength", "tensile_strength", "fatigue_strength",
            "compressive_strength", "flexural_strength", "interlaminar_shear_strength",
            "heat_resistance", "creep_strength", "dielectric_strength", "wear_resistance",
            "radiation_shielding", "flame_retardancy",
            "electrical_conductivity", "thermal_conductivity", "corrosion_resistance",
            "fracture_toughness", "elastic_modulus", "heat_deflection_temp",
            "impact_strength"
    );

    private static final Pattern RANGE_PATTERN =
            Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*(?:-|~|到|至)\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final double EPSILON = 1e-9;

    private final VectorSearchService vectorSearchService;
    private final PartService partService;

    @Override
    public PartInspectionResultVO inspect(PartInspectionDTO request) {
        if (request == null) {
            return persistAndBuild(
                    null,
                    STATUS_FAIL,
                    "请求数据为空，无法进行检测",
                    "请检查提交参数后重试"
            );
        }

        if (!vectorSearchService.existsInspectionMethodology(request.getPartType())) {
            return persistAndBuild(
                    request.getPartId(),
                    STATUS_NOT_FOUND,
                    "向量库中未找到该零件类型对应的检测方法论",
                    "请先补充该零件类型的检测方法论文档"
            );
        }

        CompareResult compareResult = comparePartData(request.getPartData(), request.getStandardData());
        return persistAndBuild(
                request.getPartId(),
                compareResult.passed ? STATUS_PASS : STATUS_FAIL,
                compareResult.details,
                compareResult.suggestion
        );
    }

    private CompareResult comparePartData(Map<String, Object> partData, Map<String, Object> standardData) {
        if (standardData == null || standardData.isEmpty()) {
            return CompareResult.fail("检测标准为空，无法进行对比", "请先维护该零件的检测标准");
        }

        Map<String, Object> actualData = partData == null ? Collections.emptyMap() : partData;
        List<String> missingKeys = new ArrayList<>();
        List<String> mismatchItems = new ArrayList<>();
        List<String> mismatchKeys = new ArrayList<>();

        for (Map.Entry<String, Object> entry : standardData.entrySet()) {
            String key = entry.getKey();
            Object expectedValue = entry.getValue();
            Object actualValue = actualData.get(key);

            if (actualValue == null || !StringUtils.hasText(String.valueOf(actualValue))) {
                missingKeys.add(key);
                continue;
            }

            if (!isMatched(key, actualValue, expectedValue)) {
                mismatchItems.add(formatMismatch(key, actualValue, expectedValue));
                mismatchKeys.add(key);
            }
        }

        if (missingKeys.isEmpty() && mismatchItems.isEmpty()) {
            return CompareResult.pass("所有参数符合标准要求", "检测通过");
        }

        List<String> detailParts = new ArrayList<>();
        if (!missingKeys.isEmpty()) {
            detailParts.add("缺失参数: " + String.join(", ", missingKeys));
        }
        if (!mismatchItems.isEmpty()) {
            detailParts.add("不符合项: " + String.join("; ", mismatchItems));
        }

        String details = String.join("；", detailParts);

        // AI智能建议：根据不合格参数类型生成针对性工艺调整建议
        String suggestion = generateAiSuggestion(missingKeys, mismatchKeys, actualData, standardData);

        return CompareResult.fail(details, suggestion);
    }

    /**
     * AI智能建议生成：根据不合格参数类型，结合工艺知识库生成针对性建议
     * 采用规则引擎 + 参数分析的混合智能架构
     */
    private String generateAiSuggestion(List<String> missingKeys, List<String> mismatchKeys,
                                         Map<String, Object> actualData, Map<String, Object> standardData) {
        if (!missingKeys.isEmpty()) {
            return "补充缺失参数后重新检测：" + String.join("、", missingKeys);
        }

        List<String> suggestions = new ArrayList<>();

        for (String key : mismatchKeys) {
            String normalizedKey = normalizeKey(key);
            Object actual = actualData.get(key);
            Object expected = standardData.get(key);
            String suggestion = generateParamSuggestion(normalizedKey, actual, expected);
            if (suggestion != null) {
                suggestions.add(suggestion);
            }
        }

        if (suggestions.isEmpty()) {
            return "核查加工工艺参数并复检";
        }

        return String.join("；", suggestions);
    }

    /**
     * 根据具体参数类型生成专业工艺调整建议
     */
    private String generateParamSuggestion(String key, Object actual, Object expected) {
        // 参数建议知识库：简洁的工艺调整建议
        Map<String, String> suggestionMap = new LinkedHashMap<>();

        // 力学性能类
        suggestionMap.put("hardness", "调整热处理工艺（淬火温度/回火时间）");
        suggestionMap.put("tensile_strength", "优化材料配比或调整锻造工艺");
        suggestionMap.put("yield_strength", "检查原材料批次并调整固溶处理参数");
        suggestionMap.put("fatigue_strength", "增加表面强化处理（喷丸/渗氮）");
        suggestionMap.put("elongation", "调整退火温度和保温时间");
        suggestionMap.put("compressive_strength", "优化烧结工艺或增加致密化处理");
        suggestionMap.put("flexural_strength", "检查纤维铺层方向和树脂含量");
        suggestionMap.put("fracture_toughness", "调整晶粒细化工艺或添加增韧相");
        suggestionMap.put("elastic_modulus", "核查材料成分配比");

        // 尺寸精度类
        suggestionMap.put("tolerance", "检查刀具磨损并重新校准机床坐标系");
        suggestionMap.put("dimension_accuracy", "降低进给速度并检查夹具定位精度");

        // 表面质量类
        suggestionMap.put("surface_roughness", "降低切削速度或更换精加工刀具");

        // 热学性能类
        suggestionMap.put("thermal_expansion_coefficient", "核查材料成分或调整热处理制度");
        suggestionMap.put("heat_resistance", "优化合金元素配比或增加高温时效处理");
        suggestionMap.put("thermal_conductivity", "检查材料纯度和微观组织");

        // 其他性能类
        suggestionMap.put("wear_resistance", "增加表面硬化处理（渗碳/镀铬）");
        suggestionMap.put("corrosion_resistance", "优化钝化处理或更换防腐涂层");
        suggestionMap.put("oxidation_resistance", "增加防氧化涂层或调整合金成分");
        suggestionMap.put("electrical_conductivity", "检查材料纯度，排除加工污染");
        suggestionMap.put("creep_strength", "优化固溶时效工艺参数");

        String suggestion = suggestionMap.get(key);
        if (suggestion != null) {
            return suggestion;
        }

        // 未匹配到知识库的参数，给出通用建议
        return String.format("[%s]不达标，核查相关工艺参数", key);
    }

    private boolean isMatched(String key, Object actualValue, Object expectedValue) {
        if (expectedValue == null) {
            return true;
        }

        Double actualNumber = toNumber(actualValue);
        String expectedText = String.valueOf(expectedValue).trim();
        String normalizedKey = normalizeKey(key);

        if (expectedValue instanceof Number && actualNumber != null) {
            return compareByKey(normalizedKey, actualNumber, ((Number) expectedValue).doubleValue());
        }

        if (actualNumber != null) {
            Double[] range = extractRange(expectedText);
            if (range != null) {
                return actualNumber >= range[0] - EPSILON && actualNumber <= range[1] + EPSILON;
            }

            Bound bound = extractBound(expectedText);
            if (bound != null) {
                return bound.lowerBound
                        ? actualNumber >= bound.value - EPSILON
                        : actualNumber <= bound.value + EPSILON;
            }

            Double single = extractSingleNumber(expectedText);
            if (single != null) {
                return compareByKey(normalizedKey, actualNumber, single);
            }
        }

        return normalizeText(actualValue).equalsIgnoreCase(normalizeText(expectedValue));
    }

    private boolean compareByKey(String key, double actual, double expected) {
        if (UPPER_BOUND_KEYS.contains(key)) {
            return actual <= expected + EPSILON;
        }
        if (LOWER_BOUND_KEYS.contains(key)) {
            return actual >= expected - EPSILON;
        }
        // 未在上下界列表中的参数，使用5%相对容差进行比较
        // 避免因微小偏差导致误判（如 density 1.82 vs 1.8）
        if (expected == 0) {
            return Math.abs(actual) <= 0.01;
        }
        return Math.abs(actual - expected) / Math.abs(expected) <= 0.05;
    }

    private Double[] extractRange(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = RANGE_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }

        double first = Double.parseDouble(matcher.group(1));
        double second = Double.parseDouble(matcher.group(2));
        return new Double[]{Math.min(first, second), Math.max(first, second)};
    }

    private Bound extractBound(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.replace(" ", "");
        Double number = extractSingleNumber(text);
        if (number == null) {
            return null;
        }

        if (text.startsWith(">=") || text.contains("≥")
                || text.contains("大于等于") || text.contains("不小于")
                || text.contains("不少于") || text.contains("不低于")) {
            return new Bound(true, number);
        }
        if (text.startsWith("<=") || text.contains("≤")
                || text.contains("小于等于") || text.contains("不大于")
                || text.contains("不高于")) {
            return new Bound(false, number);
        }
        if (text.startsWith(">")) {
            return new Bound(true, number);
        }
        if (text.startsWith("<")) {
            return new Bound(false, number);
        }
        return null;
    }

    private Double extractSingleNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(value);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group());
        }
        return null;
    }

    private Double toNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return extractSingleNumber(String.valueOf(value));
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).replaceAll("\\s+", "").trim();
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private String formatMismatch(String key, Object actual, Object expected) {
        return key + "(实测:" + actual + ", 标准:" + expected + ")";
    }

    private PartInspectionResultVO persistAndBuild(Long partId, int status, String details, String suggestion) {
        if (partId != null) {
            try {
                boolean qualified = status == STATUS_PASS;
                String statusText = switch (status) {
                    case STATUS_PASS -> "合格";
                    case STATUS_NOT_FOUND -> "不存在";
                    default -> "不合格";
                };

                partService.updateInspectionResult(
                        partId,
                        qualified,
                        "检测结果：" + statusText + "\n检测详情：" + details,
                        "建议：" + suggestion
                );
            } catch (Exception e) {
                log.error("回写零件检测结果失败, partId={}", partId, e);
            }
        }

        return new PartInspectionResultVO(status, details, suggestion, partId);
    }

    private record Bound(boolean lowerBound, double value) {
    }

    private record CompareResult(boolean passed, String details, String suggestion) {

        private static CompareResult pass(String details, String suggestion) {
            return new CompareResult(true, details, suggestion);
        }

        private static CompareResult fail(String details, String suggestion) {
            return new CompareResult(false, details, suggestion);
        }
    }
}
