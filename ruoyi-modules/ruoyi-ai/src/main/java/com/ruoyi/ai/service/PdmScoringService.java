package com.ruoyi.ai.service;

import com.ruoyi.ai.service.PdmServerClient.PdmPredictResult;

import java.util.List;

/**
 * PdM 业务评分（无状态纯函数,常量集中）
 * <p>
 * 架构定位(用户决策):pdm-server 只做模型推理,全部业务判定在本类完成。
 * 双信号分层(依据 Classmethod 对 Chronos-2 的实测:残差对尖峰 AUC=0.999、
 * 对缓变漂移 AUC≈0.51——缓变被 in-context 预测当作趋势延伸吸收,残差不响应):
 * - holdout 残差比:测突发尖峰/噪声(实测偏离超模型自身不确定度);
 * - 前后半均值差漂移:测缓变劣化(即文章"moving-average monitoring layer"的最小实现;
 *   模拟器正弦周期 60 点 &lt; 半窗 64 点,前后半各含完整周期,正弦贡献自然抵消)。
 * 评分语义全局唯一:无量纲"×阈值倍数",&gt;1 即异常,跨传感器可比。
 * </p>
 *
 * @author smartartisan
 */
public final class PdmScoringService {

    /** 漂移门限:前后半均值差/全窗 std 超过该值判漂移(平稳序列统计期望 ≈0.25σ,留 2 倍裕量) */
    public static final double DRIFT_GATE = 0.5;

    /** 健康分归零点:anomalyScore(×阈值倍数)达到该值时健康分为 0(阈值处 50 分) */
    public static final double HEALTH_ZERO = 2.0;

    /** horizon 上限(与 pdm-server 校验一致) */
    public static final int HORIZON_MAX = 256;

    /** holdout 上下文下限:horizon 须 ≤ 窗口长度-16,保证前段上下文有效 */
    public static final int MIN_CONTEXT = 16;

    /** horizon 缺省值(与原推理服务口径一致) */
    public static final int HORIZON_DEFAULT = 48;

    private PdmScoringService() {
    }

    /**
     * holdout 残差比:mean|holdoutQ50 - 尾段实测| ÷ mean(holdoutQ90 - holdoutQ10)。
     * <p>
     * holdout 预测的上下文是 window[0..n-horizon),预测步与窗口尾 horizon 个实测点对齐。
     * 带宽(q90-q10)是模型自带的不确定度度量,>1 表示实测平均偏离超过模型自身不确定度。
     * 带宽为 0(模型完全确信)时:残差也为 0 → 0;残差 >0 → 无穷大(必异常)。
     * </p>
     */
    public static double residualRatio(List<Double> window, int horizon, PdmPredictResult holdoutPred) {
        int n = window.size();
        int len = Math.min(horizon, Math.min(n, holdoutPred.getQ50().size()));
        if (len <= 0) {
            return 0.0;
        }
        double residualSum = 0.0;
        double bandSum = 0.0;
        for (int i = 0; i < len; i++) {
            double actual = window.get(n - len + i);
            residualSum += Math.abs(holdoutPred.getQ50().get(i) - actual);
            bandSum += holdoutPred.getQ90().get(i) - holdoutPred.getQ10().get(i);
        }
        double residual = residualSum / len;
        double band = bandSum / len;
        if (band <= 0.0) {
            return residual == 0.0 ? 0.0 : Double.POSITIVE_INFINITY;
        }
        return residual / band;
    }

    /**
     * 漂移分:|mean(后半) - mean(前半)| ÷ std(全窗)。
     * <p>
     * 平稳序列统计期望 ≈ 2σ/√(n/2) ≈ 0.25σ(n=128);线性爬升(如模拟器 0.08/点)
     * 半窗位移 ≈ 2σ+ 显著可分。恒值窗口(std=0)无漂移语义,返回 0。
     * </p>
     */
    public static double driftScore(List<Double> window) {
        int n = window.size();
        if (n < 2) {
            return 0.0;
        }
        double mean = 0.0;
        for (double v : window) {
            mean += v;
        }
        mean /= n;
        double varSum = 0.0;
        double firstHalfSum = 0.0;
        int half = n / 2;
        for (int i = 0; i < n; i++) {
            double d = window.get(i) - mean;
            varSum += d * d;
            if (i < half) {
                firstHalfSum += window.get(i);
            }
        }
        double std = Math.sqrt(varSum / n);
        if (std <= 0.0) {
            return 0.0;
        }
        double firstMean = firstHalfSum / half;
        double secondMean = (mean * n - firstHalfSum) / (n - half);
        return Math.abs(secondMean - firstMean) / std;
    }

    /**
     * 联合评分:两信号取 max(单一信号主导时不被另一信号稀释),无量纲 >1 即异常
     */
    public static double anomalyScore(double residualRatio, double driftScore) {
        return Math.max(residualRatio, driftScore / DRIFT_GATE);
    }

    /**
     * 异常判定:score > 1(=1 恰在阈值上不判,保持与"超过阈值倍数"语义一致)
     */
    public static boolean isAnomaly(double score) {
        return score > 1.0;
    }

    /**
     * 健康分:阈值处(1.0)50 分,HEALTH_ZERO 倍处归 0,线性 clip 映射
     */
    public static double healthScore(double score) {
        return 100.0 * Math.max(0.0, Math.min(1.0, 1.0 - score / HEALTH_ZERO));
    }

    /**
     * RUL(分钟):预测序列首个 ≥threshold 的点,第 i 点(从 1 计)发生在 i×intervalSeconds 秒后。
     * 单调递增退化假设(仅判上行越界);全程不触线返回 null。
     * Java 直接四舍五入产出 Long(消除原 Python float 经 Jackson 截断的隐式行为)。
     */
    public static Long rulMinutes(List<Double> qSeries, double threshold, double intervalSeconds) {
        for (int i = 0; i < qSeries.size(); i++) {
            if (qSeries.get(i) >= threshold) {
                return Math.round((i + 1) * intervalSeconds / 60.0);
            }
        }
        return null;
    }

    /**
     * horizon 业务校验:1..HORIZON_MAX 且 ≤ 窗口长度-MIN_CONTEXT(holdout 上下文下限)。
     *
     * @return null=通过;非 null=失败原因(调用方 R.fail 返回)
     */
    public static String validateHorizon(int horizon, int windowSize) {
        if (horizon < 1) {
            return "horizon 需 ≥ 1,实际 " + horizon;
        }
        if (horizon > HORIZON_MAX) {
            return "horizon 需 ≤ " + HORIZON_MAX + ",实际 " + horizon;
        }
        if (horizon > windowSize - MIN_CONTEXT) {
            return "horizon " + horizon + " 超过窗口长度 " + windowSize + "-" + MIN_CONTEXT
                    + "(holdout 上下文下限),请减小步长或增大窗口";
        }
        return null;
    }
}
