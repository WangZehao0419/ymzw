package com.ruoyi.alert.predict;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.entity.PredictResult;
import com.ruoyi.alert.predict.domain.Baseline;
import com.ruoyi.alert.predict.domain.SensorWindow;
import com.ruoyi.alert.service.PredictResultService;
import com.ruoyi.alert.service.RuleService;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;
import com.ruoyi.equipment.api.domain.SensorPointDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 预测性维护主任务
 * <p>
 * 每轮调度:Feign 拉全量传感器列表 → 逐传感器拉历史窗口 → 构建 SensorWindow →
 * 基线缺失时学习 → L2 突变检测(MAD 体制比值 + CUSUM 漂移) →
 * L3 趋势外推(有启用阈值规则才做,输出 t1/预测带) → 状态机推进
 * (内部处理 PREDICT 告警发出/升级/恢复) → 落 predict_result 快照。
 * 单传感器失败只跳过该传感器本轮,单行 warn 无堆栈,不让异常中断整轮。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PredictTask {

    private final PredictProperties props;
    private final RemoteEquipmentService remoteEquipmentService;
    private final BaselineRegistry baselineRegistry;
    private final PredictResultService predictResultService;
    private final RuleService ruleService;
    private final PredictStateMachine stateMachine;
    private final ObjectMapper objectMapper;

    /**
     * 预测性维护主循环(间隔 predict.interval-ms,上轮结束后起算)
     */
    @Scheduled(fixedDelayString = "${predict.interval-ms:30000}")
    public void run() {
        // 总开关:默认关闭,演示/联调时在 application.yml 置 true
        if (!props.isEnabled()) {
            return;
        }
        R<List<SensorMetaDTO>> sensorsResult = remoteEquipmentService.listAllSensors(SecurityConstants.INNER);
        if (sensorsResult == null || R.FAIL == sensorsResult.getCode()
                || sensorsResult.getData() == null || sensorsResult.getData().isEmpty()) {
            // 全量列表失败/为空:本轮直接放弃(逐传感器无从谈起),降级时 fallback 返回空列表
            log.warn("[PREDICT] 传感器全量列表获取失败或为空,跳过本轮");
            return;
        }
        for (SensorMetaDTO sensor : sensorsResult.getData()) {
            processSensor(sensor);
        }
    }

    /**
     * 单传感器取数与三层检测推进
     */
    private void processSensor(SensorMetaDTO sensor) {
        String sensorCode = sensor.getSensorCode();
        try {
            R<List<SensorPointDTO>> history = remoteEquipmentService.getSensorHistory(
                    sensorCode, props.getWindowPoints(), SecurityConstants.INNER);
            if (history == null || R.FAIL == history.getCode()
                    || history.getData() == null || history.getData().isEmpty()) {
                log.warn("[PREDICT] 历史窗口获取失败或为空,跳过本轮: sensorCode={}", sensorCode);
                return;
            }
            SensorWindow window = SensorWindow.of(sensorCode, history.getData(), props.getWindowPoints());
            if (window == null) {
                // 新注册传感器/时序断档:窗口未满八成,本轮跳过(等窗口攒够自然恢复)
                log.warn("[PREDICT] 历史窗口数据量不足,跳过本轮: sensorCode={}", sensorCode);
                return;
            }
            Baseline baseline = baselineRegistry.get(sensorCode);
            if (baseline == null) {
                baseline = baselineRegistry.learn(sensorCode, window);
            }

            // L2 突变检测:噪声体制变化(MAD 比值) + 缓变漂移(CUSUM,onset 供趋势拟合截段)
            MadDetector.Result mad = MadDetector.detect(window, baseline, props);
            CusumDetector.Result cusum = CusumDetector.detect(window, baseline, props);
            // L3 趋势外推:无启用阈值规则的传感器(如 VIB-001)只走 L2,不外推 t1
            TrendExtrapolator.Result trend = extrapolateTrend(sensor, window, baseline, cusum);
            double smoothed = TrendExtrapolator.smoothLast(window, props);

            // 状态机推进:内部处理 PREDICT 告警发出/升级/幽灵退出,返回最新状态
            String status = stateMachine.advance(sensor, mad, cusum, trend, smoothed);

            upsertSnapshot(sensor, status, cusum, trend);
        } catch (Exception e) {
            // 单传感器异常只跳过本轮该传感器,单行 warn 不带堆栈
            log.warn("[PREDICT] 传感器处理异常,跳过本轮: sensorCode={}, error={}", sensorCode, e.getMessage());
        }
    }

    /**
     * L3 趋势外推:查该传感器启用的阈值规则,有上限/下限才做外推
     * <p>
     * 上限/下限同时配置时优先上限(模拟器退化场景为上漂越上限);
     * 拟合段起点取 CUSUM onset(劣化前数据混入会稀释劣化斜率)。
     * </p>
     */
    private TrendExtrapolator.Result extrapolateTrend(SensorMetaDTO sensor, SensorWindow window,
                                                      Baseline baseline, CusumDetector.Result cusum) {
        AlertRule rule = ruleService.lambdaQuery()
                .eq(AlertRule::getSensorId, sensor.getId())
                .eq(AlertRule::getEnabled, 1)
                .one();
        if (rule == null || (rule.getUpperLimit() == null && rule.getLowerLimit() == null)) {
            return null;
        }
        Long onsetTs = cusum.isDrift() ? cusum.getOnsetTs() : null;
        if (rule.getUpperLimit() != null) {
            return TrendExtrapolator.extrapolate(
                    window, baseline, rule.getUpperLimit(), true, onsetTs, props);
        }
        return TrendExtrapolator.extrapolate(
                window, baseline, rule.getLowerLimit(), false, onsetTs, props);
    }

    /**
     * 落本轮快照(趋势相关字段无规则/未过显著性门时为 null,MP 非空更新保留旧值)
     */
    private void upsertSnapshot(SensorMetaDTO sensor, String status,
                                CusumDetector.Result cusum, TrendExtrapolator.Result trend) {
        PredictResult result = new PredictResult();
        result.setSensorCode(sensor.getSensorCode());
        result.setEquipmentId(sensor.getEquipmentId());
        result.setStatus(status);
        result.setSlope(trend == null ? null : trend.getB());
        result.setT1Points(trend == null ? null : trend.getT1Points());
        result.setPredictedBreachTime(trend != null && trend.getT1Points() != null
                ? toLocalDateTime(trend.getPredictedBreachTimeMs()) : null);
        result.setOnsetTime(cusum.isDrift() ? toLocalDateTime(cusum.getOnsetTs()) : null);
        result.setBandJson(toBandJson(trend));
        // 显式赋值;MyMetaObjectHandler 的 strict 填充不会覆盖非空值
        result.setUpdateTime(LocalDateTime.now());
        predictResultService.upsert(result);
    }

    /**
     * 预测带序列化:[[tsEpochMillis,low,mid,high],...];null 结果(不输出带)返回 null
     */
    private String toBandJson(TrendExtrapolator.Result trend) {
        if (trend == null || trend.getBand() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(trend.getBand());
        } catch (Exception e) {
            // 序列化失败仅丢失快照的带字段,不影响检测与告警主链路
            log.warn("[PREDICT] 预测带序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs),
                java.time.ZoneId.systemDefault());
    }
}
