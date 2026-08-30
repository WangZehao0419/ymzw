package com.ruoyi.alert.predict;

import com.ruoyi.alert.predict.domain.Baseline;
import com.ruoyi.alert.predict.domain.SensorWindow;

import java.util.Arrays;

/**
 * L2 突变检测器:同相位残差的 MAD 比值法(体制变化检测)
 * <p>
 * 均值/方差类检测有掩蔽效应:少量大偏移点会把标准差抬大,反而检测不出
 * 后续偏移。MAD(中位数绝对偏差)对少数离群点鲁棒——中位数不被拉偏,
 * 因此"近期残差 MAD / 基线残差 MAD"的比值 R 能稳定刻画噪声体制是否
 * 整体放大(轴承磨损初期:振动能量抬升,残差整体变宽,而非单点尖峰)。
 * </p>
 *
 * @author smartartisan
 */
public final class MadDetector {

    /** 分母防零下限:基线残差 MAD 为 0(学习期完美信号)时避免除零,R 走极大值自然触发 */
    private static final double EPS = 1e-9;

    private MadDetector() {
    }

    /**
     * 检测最近 2 个周期的同相位残差体制是否相对基线放大
     *
     * @param window   时序窗口
     * @param baseline 正常态基线(residualMad 为分母基准)
     * @param props    配置(sinePeriod/madRatioThreshold)
     * @return 检测结果(regimeChange=true 表示体制变化)
     */
    public static Result detect(SensorWindow window, Baseline baseline, PredictProperties props) {
        int p = props.getSinePeriod();
        int n = window.size();
        // 残差 r(i)=val(i)-val(i-p) 需 i-p>=0,最近 2p 个残差要求窗口至少 3p 点
        // (窗口成型校验保证 0.8*windowPoints>=3p,此处防御窗口被裁剪的场景)
        if (p <= 0 || n < 3 * p) {
            return new Result(false, 0D);
        }
        double[] residual = new double[2 * p];
        for (int i = n - 2 * p; i < n; i++) {
            residual[i - (n - 2 * p)] = window.getVal()[i] - window.getVal()[i - p];
        }
        double residualMad = medianOfAbsDev(residual);
        double denom = Math.max(baseline.getResidualMad(), EPS);
        double ratio = residualMad / denom;
        return new Result(ratio > props.getMadRatioThreshold(), ratio);
    }

    /**
     * 绝对偏差的中位数:median(|x-median(x)|)
     */
    private static double medianOfAbsDev(double[] data) {
        double median = median(data);
        double[] absDev = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            absDev[i] = Math.abs(data[i] - median);
        }
        return median(absDev);
    }

    private static double median(double[] data) {
        double[] sorted = data.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return (sorted.length & 1) == 1
                ? sorted[mid]
                : (sorted[mid - 1] + sorted[mid]) / 2;
    }

    /**
     * MAD 检测结果
     */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public static class Result {

        /** 体制变化信号:近期残差 MAD 相对基线放大超阈值 */
        private final boolean regimeChange;

        /** 体制比值 R = 近期同相位残差 MAD / 基线残差 MAD */
        private final double ratio;
    }
}
