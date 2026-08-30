package com.ruoyi.alert.predict;

import com.ruoyi.alert.predict.domain.Baseline;
import com.ruoyi.alert.predict.domain.SensorWindow;

/**
 * L2 缓变漂移检测器:块均值 CUSUM(累积和)
 * <p>
 * 磨损类劣化是缓变小偏移的累积,单点检测对每点 +0.02 的斜率无能为力
 * (淹没在噪声里)。两个手段叠加放大缓变信号:
 * 1) 按 sinePeriod 分块取均值——正弦整周期自相抵消,且均值把块内噪声
 *    压低 sqrt(周期) 倍,微小漂移在块均值序列上变得显著;
 * 2) CUSUM 累积单向偏移——允许差 k·sigma 吸收零均值噪声,只累积
 *    一致的漂移量,累积超 h·sigma 判漂移。
 * 输出 onset(C+ 从 0 起跳的块时间戳)供 L3 趋势拟合"只取劣化后数据"。
 * </p>
 *
 * @author smartartisan
 */
public final class CusumDetector {

    /** sigma 防零下限:基线残差 MAD 为 0 时避免除零与阈值塌缩 */
    private static final double EPS = 1e-9;

    private CusumDetector() {
    }

    /**
     * 对块均值序列做单边 CUSUM(检测上漂)
     *
     * @param window   时序窗口
     * @param baseline 正常态基线(median 为目标均值 mu0,residualMad 折算块 sigma)
     * @param props    配置(sinePeriod/cusumK/cusumH)
     * @return 检测结果(drift=true 时 onsetTs 为漂移起点时间戳)
     */
    public static Result detect(SensorWindow window, Baseline baseline, PredictProperties props) {
        int p = props.getSinePeriod();
        int n = window.size();
        if (p <= 0 || n < 3 * p) {
            return new Result(false, 0L, 0D);
        }
        // 不重叠分块:头部不足一块的余数丢弃(保住最近的完整块)
        int off = n % p;
        int blocks = n / p;
        if (blocks < 3) {
            return new Result(false, 0L, 0D);
        }

        // 块均值序列:正弦整周期在块内自相抵消,均值只剩水平位置 + 块内噪声/sqrt(p)
        double[] blockMean = new double[blocks];
        long[] blockTs = new long[blocks];
        for (int j = 0; j < blocks; j++) {
            int start = off + j * p;
            double sum = 0D;
            for (int i = start; i < start + p; i++) {
                sum += window.getVal()[i];
            }
            blockMean[j] = sum / p;
            blockTs[j] = window.getTs()[start];
        }

        // 块内噪声尺度:同相位残差 MAD 近似单点噪声 MAD,均值再压 sqrt(p) 倍
        double sigmaBlock = Math.max(baseline.getResidualMad() / Math.sqrt(p), EPS);
        double mu0 = baseline.getMedian();
        double k = props.getCusumK() * sigmaBlock;
        double threshold = props.getCusumH() * sigmaBlock;

        double cPlus = 0D;
        long onsetTs = 0L;
        for (int j = 0; j < blocks; j++) {
            double increment = blockMean[j] - mu0 - k;
            double next = Math.max(0D, cPlus + increment);
            // 回溯记录起跳点:C+ 从 0 变正说明该块开始贡献一致的向上偏移
            if (cPlus == 0D && next > 0D) {
                onsetTs = blockTs[j];
            }
            cPlus = next;
            if (cPlus > threshold) {
                return new Result(true, onsetTs, cPlus);
            }
        }
        return new Result(false, 0L, cPlus);
    }

    /**
     * CUSUM 检测结果
     */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public static class Result {

        /** 漂移信号:C+ 超过决策阈值 h·sigma */
        private final boolean drift;

        /** 漂移起点时间戳(epoch millis,C+ 从 0 起跳的块首点;未触发为 0) */
        private final long onsetTs;

        /** 触发时的 C+ 累积量(证据展示用) */
        private final double cPlus;
    }
}
