package com.ruoyi.alert.predict;

import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.predict.domain.Baseline;

/**
 * 传感器健康度评分(0-100,Task 11)
 * <p>
 * 健康度是"连续量",与状态机(NORMAL/DEGRADING/BREACHED)正交:状态机回答
 * "是否告警/何时越限",健康度回答"离坏还有多远"——所有状态(含 NORMAL)
 * 每轮都计算落库,因此其落库生命周期与趋势预测字段不同(回 NORMAL 清
 * 趋势残留,健康度仍更新为本轮值)。
 * 公式按"是否有启用阈值规则"分派:
 * 1) 有规则(上限/下限):平滑值在"基线中位数 → 检测阈值"区间内的位置
 *    线性映射到 100→0。分母(threshold − 基线中位数)与检测阈值联动,
 *    保证"平滑值触达阈值"与"健康度归零"是同一时刻,即 PHM 的相对裕度
 *    (margin)语义;平滑值取 smoothLast 与告警判定同口径,评分依据与
 *    告警依据不会两条口径;
 * 2) 无规则:按 MAD 体制比值 R 相对 madRatioThreshold 的占用率折算,
 *    分母同样联动检测阈值配置——R 达到体制变化触发阈值时健康度恰好
 *    归零,"检测触发"与"评分为 0"语义一致。
 * 异常基线(分母≤0/非有限,如 median≥upper)兜底返回 100 不抛异常:
 * 评分是检测主链路的附属产物,不能因评分失败中断整轮检测。
 * </p>
 *
 * @author smartartisan
 */
public final class HealthScoreService {

    private HealthScoreService() {
    }

    /**
     * 计算健康度评分
     *
     * @param baseline 正常态基线(median 为满分参照位置)
     * @param smoothed 最新平滑值(与告警判定同口径的 smoothLast)
     * @param rule     该传感器启用的阈值规则(可为 null;上下限都空视同无规则)
     * @param mad      本轮 MAD 检测结果(无规则时取其体制比值;null 按 R=0 满健康)
     * @param props    配置(madRatioThreshold 为无规则公式的折算分母)
     * @return 0-100 的健康度,保留 1 位小数;异常输入兜底 100 不抛异常
     */
    public static double compute(Baseline baseline, double smoothed, AlertRule rule,
                                 MadDetector.Result mad, PredictProperties props) {
        Double upper = rule == null ? null : rule.getUpperLimit();
        Double lower = rule == null ? null : rule.getLowerLimit();
        if (upper != null) {
            // 上限规则:裕度=(上限−平滑值)/(上限−基线中位数),基线位置=100,触线=0;
            // 上下限同时配置时优先上限(与 extrapolateTrend 同语义,退化场景为上漂越上限)
            double denom = upper - baseline.getMedian();
            if (denom <= 0 || !Double.isFinite(denom)) {
                return 100D;
            }
            return finalizeScore(clamp01((upper - smoothed) / denom) * 100D);
        }
        if (lower != null) {
            // 下限规则:方向镜像,裕度=(平滑值−下限)/(基线中位数−下限)
            double denom = baseline.getMedian() - lower;
            if (denom <= 0 || !Double.isFinite(denom)) {
                return 100D;
            }
            return finalizeScore(clamp01((smoothed - lower) / denom) * 100D);
        }
        // 无规则(rule 为 null 或上下限都空):R 占用检测阈值的比例折算,
        // R 达到 madRatioThreshold(体制变化触发阈值)时健康度恰好归零;
        // mad 为 null(理论不会)或比值非有限按 R=0 满健康处理
        double ratio = (mad == null || !Double.isFinite(mad.getRatio()))
                ? 0D : Math.max(0D, mad.getRatio());
        double threshold = props.getMadRatioThreshold();
        if (!Double.isFinite(threshold) || threshold <= 0) {
            // 阈值配置异常(除零/NaN 风险):与异常基线同策略,兜底满健康不抛异常
            return 100D;
        }
        return finalizeScore((1D - Math.min(1D, ratio / threshold)) * 100D);
    }

    /**
     * clamp 到 [0,1]:平滑值越过阈值时裕度为负直接归 0(已越限无健康可言);
     * 输入为 NaN 时保持 NaN,交由 finalizeScore 兜底
     */
    private static double clamp01(double v) {
        return Math.max(0D, Math.min(1D, v));
    }

    /**
     * 收尾:非有限值兜底 100(异常输入不抛异常),clamp 到 [0,100],
     * 四舍五入保留 1 位小数(前端三态变色只需粗粒度,减少展示抖动)
     */
    private static double finalizeScore(double v) {
        if (!Double.isFinite(v)) {
            return 100D;
        }
        return Math.round(Math.max(0D, Math.min(100D, v)) * 10D) / 10D;
    }
}
