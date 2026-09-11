package com.ruoyi.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * pdm-server 纯模型服务 HTTP 客户端
 * <p>
 * pdm-server 只做"模型即服务"(传窗口返回三分位带),无业务概念;
 * 评分/异常判定/RUL 等业务在 PdmScoringService(Java)完成。
 * 编排层(PredictInnerController)按需发起两次调用:holdout(前段上下文)+ 前向(全窗)。
 * HTTP 客户端跟随项目既有先例(VoiceCallService 等)使用 RestTemplate。
 * 超时 connect 3s / read 10s:推理耗时主导,读超时须显著大于连接超时。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmServerClient {

    /**
     * pdm-server 推理服务地址（独立进程,默认本机 8900）
     */
    @Value("${predict-server.url:http://localhost:8900}")
    private String predictServerUrl;

    private final ObjectMapper objectMapper;

    // 独立构造而非注入:超时是转发链路的硬约束,防止 pdm-server 无响应挂死调用线程
    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }

    /**
     * 调用 pdm-server 模型推理并反序列化结果
     * <p>
     * 显式用 Jackson 序列化/反序列化:复用 Spring 容器的 ObjectMapper,
     * 其默认忽略未知字段,响应新增字段不致反序列化失败。
     * 转发失败不吞异常:无降级容错决策下,调用方须直接感知失败并自行处置。
     * </p>
     *
     * @param sensorCode 传感器编码（仅日志定位用,模型服务不感知）
     * @param request    模型推理请求（窗口/步长/采样间隔）
     * @return 三分位预测带（物理量纲）+ 模型版本 + 推理耗时
     */
    public PdmPredictResult predict(String sensorCode, PdmPredictRequest request) {
        String url = predictServerUrl + "/predict";
        try {
            String body = objectMapper.writeValueAsString(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            return objectMapper.readValue(response.getBody(), PdmPredictResult.class);
        } catch (Exception e) {
            // 带上下文抛出而非静默降级:调用方(告警链路)与日志均能定位到具体传感器与目标地址
            log.error("[PDM] 模型推理转发失败: url={}, sensorCode={}, error={}", url, sensorCode, e.getMessage());
            throw new RuntimeException("pdm-server 推理转发失败: url=" + url + ", sensorCode=" + sensorCode + ", error=" + e.getMessage(), e);
        }
    }

    /**
     * pdm-server 模型推理请求体（纯模型契约,无业务字段）
     */
    @Data
    public static class PdmPredictRequest {

        /** 上下文序列（升序,最新在末尾;长度 ≥16） */
        private List<Double> window;

        /** 预测步数（1..256） */
        private Integer horizon;

        /** 采样间隔（秒;模型时间戳构造用） */
        private Double intervalSeconds;
    }

    /**
     * pdm-server 模型推理响应（与 Python PredictResponse 同构）
     */
    @Data
    public static class PdmPredictResult {

        /** P10 分位预测序列（物理量纲,horizon 步） */
        private List<Double> q10;

        /** P50 分位预测序列（中位数） */
        private List<Double> q50;

        /** P90 分位预测序列 */
        private List<Double> q90;

        /** 模型版本（运行模型名） */
        private String modelVersion;

        /** 推理耗时（毫秒） */
        private Long inferenceMs;
    }
}
