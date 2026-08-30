package com.ruoyi.alert.predict.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 传感器基线(预测性检测的正常态参照)
 * <p>
 * 由窗口最老的前 windowPoints/3 个点学习(避开近期劣化污染基线):
 * 中位数与 MAD 刻画水平噪声,极差/2 刻画幅值;
 * 同相位残差 r(i)=val(i)-val(i-sinePeriod) 把周期项相消,其 MAD 刻画
 * "剔除周期后的残余波动",是后续突变检测(MadDetector)的分母基准。
 * </p>
 *
 * @author smartartisan
 */
@Getter
@RequiredArgsConstructor
public class Baseline {

    /** 基线段中位数(正常水平位置) */
    private final double median;

    /** 基线段 MAD(中位数绝对偏差,水平噪声尺度) */
    private final double mad;

    /** 基线段幅值((max-min)/2,周期振幅尺度) */
    private final double amplitude;

    /** 同相位残差 MAD(剔除周期后的残余波动尺度,突变检测分母) */
    private final double residualMad;

    /** 学习时间 */
    private final LocalDateTime learnedAt;

    /**
     * 从窗口学习基线
     *
     * @param window       时序窗口(非 null,已过充足性校验)
     * @param windowPoints 配置的期望窗口点数(取其 1/3 作基线段)
     * @param sinePeriod   正弦周期点数(同相位残差的对齐步长)
     * @return 基线
     */
    public static Baseline learn(SensorWindow window, int windowPoints, int sinePeriod) {
        // 基线段取窗口最老的前 windowPoints/3 个点;窗口实际不足时按实际点数取
        int n = Math.min(window.size(), Math.max(windowPoints / 3, 1));
        double[] seg = Arrays.copyOfRange(window.getVal(), 0, n);

        double median = median(seg);
        double mad = medianOfAbsDev(seg, median);
        double max = Arrays.stream(seg).max().orElse(0D);
        double min = Arrays.stream(seg).min().orElse(0D);
        double amplitude = (max - min) / 2;

        // 同相位残差:周期信号同相位相减消掉周期项,残差只剩噪声与漂移
        double residualMad = 0D;
        if (sinePeriod > 0 && n > sinePeriod) {
            double[] residual = new double[n - sinePeriod];
            for (int i = sinePeriod; i < n; i++) {
                residual[i - sinePeriod] = seg[i] - seg[i - sinePeriod];
            }
            residualMad = medianOfAbsDev(residual, median(residual));
        }
        return new Baseline(median, mad, amplitude, residualMad, LocalDateTime.now());
    }

    /**
     * 中位数(偶数个取中间两数均值)
     */
    private static double median(double[] data) {
        if (data.length == 0) {
            return 0D;
        }
        double[] sorted = data.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return (sorted.length & 1) == 1
                ? sorted[mid]
                : (sorted[mid - 1] + sorted[mid]) / 2;
    }

    /**
     * 绝对偏差的中位数:median(|x-center|)
     */
    private static double medianOfAbsDev(double[] data, double center) {
        double[] absDev = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            absDev[i] = Math.abs(data[i] - center);
        }
        return median(absDev);
    }
}
