package com.ruoyi.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.constant.ServiceNameConstants;
import com.ruoyi.common.core.domain.R;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

/**
 * 预测结果内部查询客户端（诊断智能体工具①：数据获取）
 * <p>
 * 经 @LoadBalanced RestTemplate 调用 ruoyi-alert 的内部接口
 * GET /inner/predict/result/latest/{sensorCode}，
 * 获取最新 predict_result 快照 + 最近一条 PREDICT 告警摘要，
 * 作为诊断 LLM 的数值证据输入。
 * 端点由 @InnerAuth 保护，须携带 from-source: inner 请求头。
 * 调用失败不吞异常：按用户决策无降级，失败直接抛给诊断链路自然暴露。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictResultClient {

    private final RestTemplate balancedRestTemplate;

    /** Spring 容器 ObjectMapper：默认忽略未知字段，alert 侧响应加字段不致反序列化失败 */
    private final ObjectMapper objectMapper;

    /**
     * 查询传感器最新预测结果与预测告警摘要
     *
     * @param sensorCode 传感器编号（如 TH-001）
     * @return 最新预测结果 VO（result 为最新快照，alert 无告警史时为 null）
     */
    public PredictLatestVO fetchLatest(String sensorCode) {
        String url = "http://" + ServiceNameConstants.ALERT_SERVICE + "/inner/predict/result/latest/" + sensorCode;
        try {
            HttpHeaders headers = new HttpHeaders();
            // @InnerAuth 校验的内部凭证头，缺失会被 alert 侧直接拒绝
            headers.set(SecurityConstants.FROM_SOURCE, SecurityConstants.INNER);
            ResponseEntity<String> response = balancedRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            int code = root.path("code").asInt();
            if (code != R.SUCCESS) {
                // alert 侧无数据走 R.fail(msg)，把 msg 透传给调用方定位问题
                String msg = root.path("msg").asText("查询预测结果失败");
                throw new RuntimeException("查询预测结果失败: " + msg);
            }
            JsonNode data = root.get("data");
            if (data == null || data.isNull()) {
                throw new RuntimeException("该传感器暂无预测结果: " + sensorCode);
            }
            return objectMapper.treeToValue(data, PredictLatestVO.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[诊断] 拉取预测结果失败: url={}, sensorCode={}, error={}", url, sensorCode, e.getMessage());
            throw new RuntimeException("拉取预测结果失败: url=" + url + ", sensorCode=" + sensorCode + ", error=" + e.getMessage(), e);
        }
    }

    /**
     * 最新预测结果响应（结构对齐 alert 模块 InnerPredictController.LatestVO）
     */
    @Data
    public static class PredictLatestVO {

        /** 最新预测快照（含 anomaly_score/rul_earliest/rul_latest/status 等模型推理字段） */
        private PredictResultDTO result;

        /** 最近一条 PREDICT 告警摘要（级别/状态/摘要/建议等；无告警史为 null） */
        private PredictAlertDTO alert;
    }

    /**
     * 预测结果快照（字段对齐 alert 模块 PredictResult 实体，仅取诊断所需）
     */
    @Data
    public static class PredictResultDTO {

        private String sensorCode;

        private Integer equipmentId;

        /** 预测状态: NORMAL/DEGRADING/BREACHED */
        private String status;

        private Double healthScore;

        /** 异常评分（越高越异常） */
        private Double anomalyScore;

        /** RUL 最早失效剩余分钟数 */
        private Long rulEarliest;

        /** RUL 最晚失效剩余分钟数 */
        private Long rulLatest;

        /** 推理模型版本 */
        private String modelVersion;

        /** 预测越限时间 */
        private LocalDateTime predictedBreachTime;

        private LocalDateTime updateTime;
    }

    /**
     * 预测告警摘要（字段对齐 alert 模块 PredictAlert 实体，仅取诊断所需）
     */
    @Data
    public static class PredictAlertDTO {

        private Integer equipmentId;

        private String equipmentName;

        private String sensorCode;

        private String sensorName;

        /** 告警级别: NORMAL/WARNING/IMPORTANT/SEVERE/CRITICAL */
        private String alertLevel;

        /** 告警状态: FIRING/ACKED/RESOLVED */
        private String alertStatus;

        private String summary;

        private String rootCause;

        private String suggestion;

        private Double sensorValue;

        private LocalDateTime triggerTime;

        private LocalDateTime predictedBreachTime;
    }
}
