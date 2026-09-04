package com.ruoyi.alert.predict;

import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.predict.domain.Baseline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 健康度评分单元测试(纯函数,不依赖 Spring)
 * <p>
 * 数值场景对齐 spec:add-maintenance-plan"传感器健康度评分"的 Scenario
 * (TEMP-002 漂移:阈值 75/基线中位数 25;VIB-002 噪声增益:R 约 3)。
 * </p>
 *
 * @author smartartisan
 */
class HealthScoreServiceTest {

    /** 对齐 application.yml 的 predict.mad-ratio-threshold(2026-08-31 由 2.0 调 3.0) */
    private final PredictProperties props = new PredictProperties();

    /**
     * 构造基线(median 为健康度满分参照位置,其余量对公式无影响)
     */
    private Baseline baselineWithMedian(double median) {
        return new Baseline(median, 1D, 5D, 1D, LocalDateTime.now());
    }

    /**
     * 构造上限规则
     */
    private AlertRule upperRule(double upper) {
        AlertRule rule = new AlertRule();
        rule.setUpperLimit(upper);
        return rule;
    }

    /**
     * 构造下限规则
     */
    private AlertRule lowerRule(double lower) {
        AlertRule rule = new AlertRule();
        rule.setLowerLimit(lower);
        return rule;
    }

    @Test
    @DisplayName("上限规则:平滑值在基线中位数处得满分 100")
    void upperRuleBaselinePositionFullHealth() {
        // TEMP-002 语义:阈值 75、基线中位数 25,无漂移时分子分母相等 → 100
        Baseline baseline = baselineWithMedian(25D);
        AlertRule rule = upperRule(75D);

        double health = HealthScoreService.compute(baseline, 25D, rule, null, props);

        assertEquals(100.0, health, 0.001, "平滑值等于基线中位数应得满分");
    }

    @Test
    @DisplayName("上限规则:漂移推进平滑值上移,健康度单调下降")
    void upperRuleDriftMonotonicDecrease() {
        Baseline baseline = baselineWithMedian(25D);
        AlertRule rule = upperRule(75D);

        // 模拟 TEMP-002 漂移推进:平滑值 25→45→65,裕度 1→0.6→0.2
        double h1 = HealthScoreService.compute(baseline, 25D, rule, null, props);
        double h2 = HealthScoreService.compute(baseline, 45D, rule, null, props);
        double h3 = HealthScoreService.compute(baseline, 65D, rule, null, props);

        assertTrue(h1 > h2 && h2 > h3, "健康度应随平滑值上移单调下降: " + h1 + ">" + h2 + ">" + h3);
        assertEquals(60.0, h2, 0.001, "平滑值到区间中点应得 60");
        assertEquals(20.0, h3, 0.001, "平滑值到 (阈值−基线) 的 1/5 处应得 20");
    }

    @Test
    @DisplayName("上限规则:平滑值到达/超过阈值 clamp 到 0")
    void upperRuleBreachClampZero() {
        Baseline baseline = baselineWithMedian(25D);
        AlertRule rule = upperRule(75D);

        assertEquals(0.0, HealthScoreService.compute(baseline, 75D, rule, null, props),
                0.001, "平滑值恰好触线应得 0(检测阈值与健康度归零同一时刻)");
        assertEquals(0.0, HealthScoreService.compute(baseline, 80D, rule, null, props),
                0.001, "平滑值越过阈值应 clamp 到 0 而非负分");
    }

    @Test
    @DisplayName("下限规则:方向镜像——低于下限=0,回到中位数=100")
    void lowerRuleDirection() {
        Baseline baseline = baselineWithMedian(25D);
        AlertRule rule = lowerRule(5D);

        assertEquals(0.0, HealthScoreService.compute(baseline, 3D, rule, null, props),
                0.001, "平滑值低于下限应 clamp 到 0");
        assertEquals(0.0, HealthScoreService.compute(baseline, 5D, rule, null, props),
                0.001, "平滑值恰在下限应得 0");
        assertEquals(50.0, HealthScoreService.compute(baseline, 15D, rule, null, props),
                0.001, "平滑值在区间中点应得 50");
        assertEquals(100.0, HealthScoreService.compute(baseline, 25D, rule, null, props),
                0.001, "平滑值回到基线中位数应得满分");
    }

    @Test
    @DisplayName("无规则:R 达到 madRatioThreshold 时健康度归 0")
    void noRuleRatioAtThresholdZero() {
        // VIB-002 噪声增益×3 场景:R=3 恰为检测触发阈值 → 健康度 0
        props.setMadRatioThreshold(3.0);
        Baseline baseline = baselineWithMedian(25D);
        MadDetector.Result r3 = new MadDetector.Result(true, 3.0);

        assertEquals(0.0, HealthScoreService.compute(baseline, 25D, null, r3, props),
                0.001, "R 达到检测阈值时健康度应归 0");

        // R 超过阈值继续放大,min(1,·) 截断仍为 0(不会出现负分)
        MadDetector.Result r4 = new MadDetector.Result(true, 4.0);
        assertEquals(0.0, HealthScoreService.compute(baseline, 25D, null, r4, props),
                0.001, "R 超过检测阈值应截断在 0");
    }

    @Test
    @DisplayName("无规则:平稳噪声 R=1 时健康度约 66.7(1−1/3)")
    void noRuleRatioOneAboutTwoThirds() {
        // 平稳噪声的 R 约为 1(近期残差 MAD≈基线残差 MAD),阈值 3.0 → (1−1/3)×100
        props.setMadRatioThreshold(3.0);
        Baseline baseline = baselineWithMedian(25D);
        MadDetector.Result r1 = new MadDetector.Result(false, 1.0);

        double health = HealthScoreService.compute(baseline, 25D, null, r1, props);

        assertEquals(66.7, health, 0.01, "R=1、阈值 3.0 时健康度应约 66.7");
    }

    @Test
    @DisplayName("无规则:mad 为 null 或比值不可用按 R=0 满健康处理")
    void noRuleMadUnavailableFullHealth() {
        props.setMadRatioThreshold(3.0);
        Baseline baseline = baselineWithMedian(25D);

        assertEquals(100.0, HealthScoreService.compute(baseline, 25D, null, null, props),
                0.001, "mad 为 null 应按 R=0 满健康");
        // 比值非有限(NaN/∞,理论不会但防御):同样按 R=0,不抛异常不出 NaN
        MadDetector.Result nan = new MadDetector.Result(false, Double.NaN);
        assertEquals(100.0, HealthScoreService.compute(baseline, 25D, null, nan, props),
                0.001, "比值 NaN 应兜底满健康而非 NaN");
    }

    @Test
    @DisplayName("异常基线:分母为零/负(如 median≥upper)兜底 100 不抛异常")
    void invalidDenominatorFallback100() {
        Baseline baseline = baselineWithMedian(25D);

        // 上限分支:median==upper 分母为 0;median>upper 分母为负
        assertEquals(100.0, HealthScoreService.compute(baseline, 25D, upperRule(25D), null, props),
                0.001, "分母为 0 应兜底 100");
        assertEquals(100.0, HealthScoreService.compute(baseline, 25D, upperRule(20D), null, props),
                0.001, "分母为负(median≥upper)应兜底 100");
        // 下限分支:lower≥median 分母为 0/负
        assertEquals(100.0, HealthScoreService.compute(baseline, 25D, lowerRule(25D), null, props),
                0.001, "下限分支分母为 0 应兜底 100");
        assertEquals(100.0, HealthScoreService.compute(baseline, 25D, lowerRule(30D), null, props),
                0.001, "下限分支分母为负应兜底 100");
        // 无规则分支:阈值配置非正(除零风险)同样兜底 100
        props.setMadRatioThreshold(0.0);
        MadDetector.Result r = new MadDetector.Result(false, 1.0);
        assertEquals(100.0, HealthScoreService.compute(baseline, 25D, null, r, props),
                0.001, "madRatioThreshold≤0 应兜底 100");
    }

    @Test
    @DisplayName("上下限都空的规则视同无规则,走 MAD 公式")
    void emptyLimitRuleFallsToMadFormula() {
        props.setMadRatioThreshold(3.0);
        Baseline baseline = baselineWithMedian(25D);
        AlertRule rule = new AlertRule();
        rule.setEnabled(1);

        // 与"无规则 + R=1"同结果,证明两 limit 都空不会误入有规则分支
        MadDetector.Result r1 = new MadDetector.Result(false, 1.0);
        assertEquals(66.7, HealthScoreService.compute(baseline, 25D, rule, r1, props),
                0.01, "limit 都空应与无规则同口径");
    }
}
