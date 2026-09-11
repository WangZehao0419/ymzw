package com.ruoyi.ai.controller;

import com.ruoyi.ai.api.domain.AiPredictRequestDTO;
import com.ruoyi.ai.api.domain.AiPredictResultDTO;
import com.ruoyi.ai.service.PdmScoringService;
import com.ruoyi.ai.service.PdmServerClient;
import com.ruoyi.ai.service.PdmServerClient.PdmPredictRequest;
import com.ruoyi.ai.service.PdmServerClient.PdmPredictResult;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import com.ruoyi.equipment.api.domain.SensorPointDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * AI推理内部接口 Controller（内部服务调用,数据获取编排与业务评分）
 * <p>
 * 供告警等模块经 OpenFeign 发起单传感器异常检测与RUL预测,
 * 端点由 @InnerAuth 保护,仅限服务间携带内部凭证的调用,不对网关外暴露。
 * 架构定位(用户决策):pdm-server 只做模型推理(Python 纯模型服务),
 * 本服务承担全部业务——拉窗 → 两次模型调用(holdout+前向) → 评分/RUL(PdmScoringService)
 * → 组装结果,形成 alert → ruoyi-ai(数据+业务) → pdm-server(纯模型)链路。
 * </p>
 *
 * @author smartartisan
 */
@RestController
@RequestMapping("/inner/ai")
@RequiredArgsConstructor
public class PredictInnerController {

    /**
     * 推理窗口长度:与 pdm-server 窗口校验对齐(须 ≥16 且 > horizon),
     * 少于该值 pdm-server 会拒绝,此处不重复校验交由其判定
     */
    private static final int PDM_WINDOW_POINTS = 128;

    private final RemoteEquipmentService remoteEquipmentService;
    private final PdmServerClient pdmServerClient;

    /**
     * 单传感器推理编排（内部服务调用，@InnerAuth 保护）
     * <p>
     * 编排流程:拉历史窗口(Feign) → 非空校验 → 中位采样间隔 → horizon 业务校验 →
     * holdout 调用(前段上下文预测,算残差比) + 前向调用(全窗预测,产出展示 q 带) →
     * 评分与 RUL(PdmScoringService) → 组装 AiPredictResultDTO(含 window/windowTs 填充)。
     * 取数失败返回明确失败(调用方按本轮跳过处理);转发失败由 PdmServerClient 抛
     * RuntimeException 冒泡:无降级容错决策,调用方直接感知失败。
     * </p>
     *
     * @param request 推理请求（传感器编码/阈值/预测步长,窗口由本服务拉取）
     * @param source  请求来源
     * @return 推理结果（业务评分 + 模型分位带 + 窗口回显）
     */
    @InnerAuth
    @PostMapping("/predict")
    public R<AiPredictResultDTO> predict(@RequestBody AiPredictRequestDTO request,
                                         @RequestHeader(SecurityConstants.FROM_SOURCE) String source) {
        if (request == null || request.getSensorCode() == null || request.getSensorCode().isEmpty()) {
            return R.fail("传感器编码不能为空");
        }
        // 数据获取上收:窗口在本服务拉取,调用方不再传窗口
        // endTimeTs 传 null:推理窗口始终取最新数据(触发前时间上界仅预测告警证据功能使用)
        R<List<SensorPointDTO>> history = remoteEquipmentService.getSensorHistory(
                request.getSensorCode(), PDM_WINDOW_POINTS, null, SecurityConstants.INNER);
        if (history == null || R.FAIL == history.getCode()
                || history.getData() == null || history.getData().isEmpty()) {
            return R.fail("传感器无历史数据");
        }

        List<Double> window = new ArrayList<>(history.getData().size());
        List<Long> windowTs = new ArrayList<>(history.getData().size());
        for (SensorPointDTO point : history.getData()) {
            // 脏点(时间戳/数值缺失)直接剔除:null 混入窗口会让 pdm-server 反序列化整单失败
            if (point == null || point.getTs() == null || point.getVal() == null) {
                continue;
            }
            window.add(point.getVal());
            windowTs.add(point.getTs());
        }
        if (window.isEmpty()) {
            return R.fail("传感器无历史数据");
        }

        // horizon 业务校验(holdout 上下文下限在 Java 侧,pdm-server 只做模型可用性校验)
        int horizon = request.getHorizon() == null ? PdmScoringService.HORIZON_DEFAULT : request.getHorizon();
        String horizonError = PdmScoringService.validateHorizon(horizon, window.size());
        if (horizonError != null) {
            return R.fail(horizonError);
        }
        double intervalSeconds = medianIntervalSeconds(windowTs);

        // 两次模型调用:holdout(前段上下文)用于残差比,前向(全窗)用于展示 q 带与 RUL 求交
        PdmPredictResult holdout = pdmServerClient.predict(request.getSensorCode(),
                buildRequest(window.subList(0, window.size() - horizon), horizon, intervalSeconds));
        PdmPredictResult forward = pdmServerClient.predict(request.getSensorCode(),
                buildRequest(window, horizon, intervalSeconds));

        // 业务评分(全部 Java 侧,无量纲比值 >1 即异常)
        double residualRatio = PdmScoringService.residualRatio(window, horizon, holdout);
        double driftScore = PdmScoringService.driftScore(window);
        double score = PdmScoringService.anomalyScore(residualRatio, driftScore);

        // RUL:上行退化假设下 q90 最早触线 / q50 中位 / q10 最晚
        Long rulPoint = null;
        Long rulEarliest = null;
        Long rulLatest = null;
        if (request.getThreshold() != null) {
            rulPoint = PdmScoringService.rulMinutes(forward.getQ50(), request.getThreshold(), intervalSeconds);
            rulEarliest = PdmScoringService.rulMinutes(forward.getQ90(), request.getThreshold(), intervalSeconds);
            rulLatest = PdmScoringService.rulMinutes(forward.getQ10(), request.getThreshold(), intervalSeconds);
        }

        // 组装结果:业务字段(Java 算) + 模型字段(前向) + 窗口回显(本层填充)
        AiPredictResultDTO result = new AiPredictResultDTO();
        result.setAnomalyScore(round6(score));
        result.setIsAnomaly(PdmScoringService.isAnomaly(score));
        result.setHealthScore(Math.round(PdmScoringService.healthScore(score) * 100D) / 100D);
        result.setQ10(forward.getQ10());
        result.setQ50(forward.getQ50());
        result.setQ90(forward.getQ90());
        result.setRulPoint(rulPoint);
        result.setRulEarliest(rulEarliest);
        result.setRulLatest(rulLatest);
        result.setModelVersion(forward.getModelVersion());
        result.setInferenceMs(sumInferenceMs(holdout, forward));
        result.setWindow(window);
        result.setWindowTs(windowTs);
        return R.ok(result);
    }

    /**
     * 组装 pdm-server 纯模型请求(窗口/步长/采样间隔,无业务字段)
     */
    private PdmPredictRequest buildRequest(List<Double> window, int horizon, double intervalSeconds) {
        PdmPredictRequest req = new PdmPredictRequest();
        req.setWindow(new ArrayList<>(window));
        req.setHorizon(horizon);
        req.setIntervalSeconds(intervalSeconds);
        return req;
    }

    /**
     * 两次模型调用推理耗时之和(单轮推理总耗时口径)
     */
    private Long sumInferenceMs(PdmPredictResult holdout, PdmPredictResult forward) {
        long sum = 0L;
        if (holdout.getInferenceMs() != null) {
            sum += holdout.getInferenceMs();
        }
        if (forward.getInferenceMs() != null) {
            sum += forward.getInferenceMs();
        }
        return sum;
    }

    private double round6(double v) {
        return Math.round(v * 1_000_000D) / 1_000_000D;
    }

    /**
     * 中位采样间隔(秒):相邻 ts 差取中位数÷1000。
     * 中位数对个别抖动/补写鲁棒;剔除非正差(重复时间戳)防脏值。
     * 单点窗口无差值可算,退回 1.0(模型时间戳构造的兜底口径,pdm-server 校验 >0 即可)。
     */
    private double medianIntervalSeconds(List<Long> windowTs) {
        List<Long> diffs = new ArrayList<>(windowTs.size() - 1);
        for (int i = 1; i < windowTs.size(); i++) {
            long diff = windowTs.get(i) - windowTs.get(i - 1);
            if (diff > 0) {
                diffs.add(diff);
            }
        }
        if (diffs.isEmpty()) {
            return 1.0;
        }
        List<Long> sorted = new ArrayList<>(diffs);
        java.util.Collections.sort(sorted);
        long median = sorted.get(sorted.size() / 2);
        return median / 1000.0;
    }
}
