package com.ruoyi.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 告警记忆服务
 * <p>
 * 短期记忆：Redis List 滑动窗口，每个传感器独立窗口，固定 600 条，无过期时间
 * </p>
 */
@Slf4j
@Service
public class AlarmMemoryService {

    /** 滑动窗口大小：600 条 = 10 分钟（1 次/秒采样） */
    private static final int WINDOW_SIZE = 600;
    private static final String KEY_PREFIX = "predictive:stm:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public AlarmMemoryService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 将传感器读数推入短期记忆滑动窗口
     *
     * @param equipmentId 设备 ID
     * @param sensorCode  传感器编码
     * @param reading     传感器读数，包含 ts/value/anomalyScore
     */
    public void pushShortTerm(String equipmentId, String sensorCode, Map<String, Object> reading) {
        try {
            String key = buildKey(equipmentId, sensorCode);
            if (!reading.containsKey("ts")) {
                reading.put("ts", LocalDateTime.now().toString());
            }
            String json = objectMapper.writeValueAsString(reading);

            // LPUSH 新数据 + LTRIM 维持滑动窗口
            stringRedisTemplate.opsForList().leftPush(key, json);
            stringRedisTemplate.opsForList().trim(key, 0, WINDOW_SIZE - 1);

            log.debug("短期记忆写入: key={}, size={}", key, WINDOW_SIZE);
        } catch (JsonProcessingException e) {
            log.error("序列化传感器读数失败: {}", e.getMessage());
        }
    }

    /**
     * 获取短期记忆滑动窗口的全部数据
     *
     * @param equipmentId 设备 ID
     * @param sensorCode  传感器编码
     * @return 窗口内的时序数据列表（最新在前），读取失败返回空列表
     */
    public List<Map<String, Object>> getShortTermWindow(String equipmentId, String sensorCode) {
        try {
            String key = buildKey(equipmentId, sensorCode);
            List<String> rawList = stringRedisTemplate.opsForList().range(key, 0, -1);
            if (rawList == null || rawList.isEmpty()) {
                return Collections.emptyList();
            }
            return rawList.stream()
                    .map(this::parseJson)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("读取短期记忆失败: equipmentId={}, sensorCode={}, error={}",
                    equipmentId, sensorCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 构建可注入 ReactAgent system prompt 的记忆上下文
     * <p>
     * 将滑动窗口数据格式化为自然语言表格，供 LLM 进行趋势分析
     * </p>
     *
     * @param equipmentId 设备 ID
     * @param sensorCode  传感器编码
     * @return 格式化的上下文文本，无数据时返回提示信息
     */
    public String buildMemoryContext(String equipmentId, String sensorCode) {
        List<Map<String, Object>> window = getShortTermWindow(equipmentId, sensorCode);

        if (window.isEmpty()) {
            return "【短期记忆】暂无历史数据，这是该传感器的首次读数。";
        }

        // 构建表格形式的上下文
        StringBuilder sb = new StringBuilder();
        sb.append("【短期记忆 — 最近 ").append(window.size())
                .append(" 次传感器读数】(滑动窗口)\n");
        sb.append("时间                    数值        异常分数\n");

        // 列表顺序为最新在前，显示时保留此顺序
        for (Map<String, Object> reading : window) {
            sb.append(String.format("%-24s %-12s %s\n",
                    reading.getOrDefault("ts", "-"),
                    reading.getOrDefault("value", "-"),
                    reading.getOrDefault("anomalyScore", "-")));
        }

        return sb.toString();
    }

    /**
     * 构建 Redis Key
     */
    private String buildKey(String equipmentId, String sensorCode) {
        return KEY_PREFIX + equipmentId + ":" + sensorCode;
    }

    /**
     * JSON 反序列化为 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.error("反序列化传感器读数失败: {}", e.getMessage());
            return Collections.singletonMap("error", "parse_failed");
        }
    }
}
