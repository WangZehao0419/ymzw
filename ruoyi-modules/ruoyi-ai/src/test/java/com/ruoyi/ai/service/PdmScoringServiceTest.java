package com.ruoyi.ai.service;

import com.ruoyi.ai.service.PdmServerClient.PdmPredictResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PdM 业务评分单元测试（纯函数,不起 Spring）
 * <p>
 * 覆盖:holdout 残差比(正常/尖峰/带宽 0 边界/尾段对齐)、漂移分(平稳/线性爬升/
 * 正弦周期免疫/恒值)、评分组合与异常边界、健康分 clip、RUL 分钟换算、horizon 校验。
 * </p>
 *
 * @author smartartisan
 */
class PdmScoringServiceTest {

    // ============================== residualRatio ==============================

    @Test
    @DisplayName("残差比-正常:q50 贴实测且带宽大,比值 <1")
    void residualRatioNormal() {
        // 窗口尾段实测 [50,51,52],预测 q50 偏差 0.1,带宽 2.0 → 0.05
        PdmPredictResult pred = pred(List.of(49.0, 50.0, 51.0), List.of(50.1, 51.1, 52.1), List.of(51.0, 52.0, 53.0));
        List<Double> window = constantThen(7, 40.0, List.of(50.0, 51.0, 52.0));
        double ratio = PdmScoringService.residualRatio(window, 3, pred);
        assertEquals(0.05, ratio, 1e-9);
    }

    @Test
    @DisplayName("残差比-尖峰:实测偏离 q50 超带宽,比值 >1")
    void residualRatioSpike() {
        // 残差 5.0,带宽 2.0 → 2.5
        PdmPredictResult pred = pred(List.of(50.0, 50.0, 50.0), List.of(50.0, 50.0, 50.0), List.of(52.0, 52.0, 52.0));
        List<Double> window = constantThen(7, 40.0, List.of(55.0, 55.0, 55.0));
        assertEquals(2.5, PdmScoringService.residualRatio(window, 3, pred), 1e-9);
    }

    @Test
    @DisplayName("残差比-尾段对齐:holdout 预测与窗口最后 horizon 点逐点对应")
    void residualRatioTailAlignment() {
        // 尾段 [100,200,300],q50=[100,200,300] → 残差 0(前段 40 不参与)
        PdmPredictResult pred = pred(List.of(0.0, 0.0, 0.0), List.of(100.0, 200.0, 300.0), List.of(1.0, 1.0, 1.0));
        List<Double> window = constantThen(7, 40.0, List.of(100.0, 200.0, 300.0));
        assertEquals(0.0, PdmScoringService.residualRatio(window, 3, pred), 1e-9);
    }

    @Test
    @DisplayName("残差比-带宽 0 边界:模型完全确信时,残差 0 归 0、残差>0 归无穷")
    void residualRatioZeroBand() {
        PdmPredictResult zeroResidual = pred(List.of(50.0, 50.0), List.of(50.0, 50.0), List.of(50.0, 50.0));
        List<Double> window = constantThen(3, 40.0, List.of(50.0, 50.0));
        assertEquals(0.0, PdmScoringService.residualRatio(window, 2, zeroResidual), 1e-9);

        PdmPredictResult nonzeroResidual = pred(List.of(50.0, 50.0), List.of(50.0, 50.0), List.of(50.0, 50.0));
        List<Double> spiked = constantThen(3, 40.0, List.of(60.0, 60.0));
        assertEquals(Double.POSITIVE_INFINITY, PdmScoringService.residualRatio(spiked, 2, nonzeroResidual));
    }

    // ============================== driftScore ==============================

    @Test
    @DisplayName("漂移分-平稳:常数+微噪声,远低于 0.5σ 门限")
    void driftScoreStationary() {
        List<Double> window = new ArrayList<>(128);
        for (int i = 0; i < 128; i++) {
            window.add(40.0 + (i % 2 == 0 ? 0.1 : -0.1));
        }
        assertTrue(PdmScoringService.driftScore(window) < PdmScoringService.DRIFT_GATE);
    }

    @Test
    @DisplayName("漂移分-线性爬升:模拟器 0.08/点形态显著超门限")
    void driftScoreLinearRamp() {
        List<Double> window = new ArrayList<>(128);
        for (int i = 0; i < 128; i++) {
            window.add(40.0 + 0.08 * i);
        }
        assertTrue(PdmScoringService.driftScore(window) > PdmScoringService.DRIFT_GATE,
                "线性爬升前后半均值差应 ≈1.7σ,超过 0.5σ 门限");
    }

    @Test
    @DisplayName("漂移分-正弦免疫:周期 60 点 < 半窗 64 点,前后半各含完整周期")
    void driftScoreSineImmune() {
        List<Double> window = new ArrayList<>(128);
        for (int i = 0; i < 128; i++) {
            window.add(40.0 + Math.sin(2.0 * Math.PI * i / 60.0));
        }
        assertTrue(PdmScoringService.driftScore(window) < PdmScoringService.DRIFT_GATE,
                "正弦周期 60 < 半窗 64,前后半均值差应自然抵消");
    }

    @Test
    @DisplayName("漂移分-恒值窗口(std=0)返回 0(无漂移语义)")
    void driftScoreConstant() {
        List<Double> window = new ArrayList<>(64);
        for (int i = 0; i < 64; i++) {
            window.add(40.0);
        }
        assertEquals(0.0, PdmScoringService.driftScore(window), 1e-9);
    }

    // ============================== 评分组合与判定 ==============================

    @Test
    @DisplayName("评分组合:两信号取 max,漂移按门限归一(residual=0.3,drift=0.4 → 0.8)")
    void anomalyScoreCombination() {
        assertEquals(0.8, PdmScoringService.anomalyScore(0.3, 0.4), 1e-9);
        // 残差主导:residual=1.5, drift=0.2 → max(1.5, 0.4)=1.5
        assertEquals(1.5, PdmScoringService.anomalyScore(1.5, 0.2), 1e-9);
    }

    @Test
    @DisplayName("异常边界:=1 恰在阈值不判异常,>1 判异常")
    void isAnomalyBoundary() {
        assertFalse(PdmScoringService.isAnomaly(1.0));
        assertFalse(PdmScoringService.isAnomaly(0.999));
        assertTrue(PdmScoringService.isAnomaly(1.0001));
        assertTrue(PdmScoringService.isAnomaly(Double.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("健康分:阈值处 50 分,归零点 0 分,超界 clip 到 0")
    void healthScoreMapping() {
        assertEquals(100.0, PdmScoringService.healthScore(0.0), 1e-9);
        assertEquals(50.0, PdmScoringService.healthScore(1.0), 1e-9);
        assertEquals(0.0, PdmScoringService.healthScore(PdmScoringService.HEALTH_ZERO), 1e-9);
        assertEquals(0.0, PdmScoringService.healthScore(3.0), 1e-9);
    }

    // ============================== rulMinutes ==============================

    @Test
    @DisplayName("RUL-首触换算:第 3 点触线 ×30s/60=1.5 分钟,四舍五入 2")
    void rulMinutesFirstCross() {
        assertEquals(2L, PdmScoringService.rulMinutes(List.of(10.0, 20.0, 30.0), 25.0, 30.0));
    }

    @Test
    @DisplayName("RUL-不触线返回 null")
    void rulMinutesNoCross() {
        assertNull(PdmScoringService.rulMinutes(List.of(10.0, 20.0, 30.0), 85.0, 30.0));
    }

    @Test
    @DisplayName("RUL-第 1 点即触线:interval/60 分钟")
    void rulMinutesImmediateCross() {
        assertEquals(1L, PdmScoringService.rulMinutes(List.of(90.0, 95.0), 85.0, 60.0));
    }

    @Test
    @DisplayName("RUL-亚分钟换算:0.5s 间隔第 2 点触线 → 0.0167 分钟四舍五入 0")
    void rulMinutesSubMinute() {
        assertEquals(0L, PdmScoringService.rulMinutes(List.of(10.0, 20.0), 15.0, 0.5));
    }

    // ============================== validateHorizon ==============================

    @Test
    @DisplayName("horizon 校验:下界/上界/holdout 上下文下限")
    void validateHorizonBoundaries() {
        assertNotNullMsg(PdmScoringService.validateHorizon(0, 128), "horizon < 1 应拒绝");
        assertNotNullMsg(PdmScoringService.validateHorizon(257, 128), "horizon > 256 应拒绝");
        assertNotNullMsg(PdmScoringService.validateHorizon(90, 100), "horizon > 窗口-16 应拒绝");
        assertNull(PdmScoringService.validateHorizon(84, 100), "horizon = 窗口-16 应通过");
        assertNull(PdmScoringService.validateHorizon(48, 128), "常规 48/128 应通过");
    }

    // ============================== 辅助构造 ==============================

    /** 构造 holdout 预测结果 */
    private PdmPredictResult pred(List<Double> q10, List<Double> q50, List<Double> q90) {
        PdmPredictResult r = new PdmPredictResult();
        r.setQ10(q10);
        r.setQ50(q50);
        r.setQ90(q90);
        r.setModelVersion("amazon/chronos-2");
        r.setInferenceMs(100L);
        return r;
    }

    /** 前段填 pad 个常数值,尾接 tail(模拟完整窗口,尾段即 holdout 实测) */
    private List<Double> constantThen(int pad, double value, List<Double> tail) {
        List<Double> window = new ArrayList<>(pad + tail.size());
        for (int i = 0; i < pad; i++) {
            window.add(value);
        }
        window.addAll(tail);
        return window;
    }

    private void assertNotNullMsg(String actual, String message) {
        assertTrue(actual != null && !actual.isEmpty(), message + ",实际: " + actual);
    }
}
