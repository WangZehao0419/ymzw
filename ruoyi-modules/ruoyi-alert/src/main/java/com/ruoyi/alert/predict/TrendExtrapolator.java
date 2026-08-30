package com.ruoyi.alert.predict;

import com.ruoyi.alert.predict.domain.Baseline;
import com.ruoyi.alert.predict.domain.SensorWindow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * L3 趋势外推器:平滑 + 加权最小二乘(WLS)拟合 + 置信带外推
 * <p>
 * 三步消除干扰再做外推:
 * 1) 按 sinePeriod 滑动平均——正弦整周期自相抵消,平滑序列只剩缓变趋势;
 *    滑动平均的中心在窗口中点,平滑值相对真实趋势滞后半窗 (p-1)/2 点,
 *    拟合后按斜率对截距前移补偿(见 halfLag),否则触线时刻系统性推迟;
 * 2) WLS 指数衰减权重(最新点权重 1)——磨损类劣化的斜率是逐渐形成的,
 *    老数据代表性差,压低其权重让拟合贴住近期行为;
 * 3) 显著性门(R2 + 最小斜率 + 方向朝阈值)——纯噪声序列也会拟合出
 *    非零斜率,不设门会对随机波动报"即将越限"。
 * 拟合段只取 onset(CUSUM 漂移起点)之后的点:劣化前后斜率不同,
 * 混合拟合会把劣化斜率稀释成正常水平。
 * </p>
 *
 * @author smartartisan
 */
public final class TrendExtrapolator {

    /** 置信带 z 值:95% 区间取 1.96 */
    private static final double Z95 = 1.96;

    /** 浮点比较下限:方差类量低于此视为 0,防除零 */
    private static final double EPS = 1e-12;

    /** 拟合段最小点数:不足时拟合自由度不够,斜率不可信 */
    private static final int MIN_FIT_POINTS = 30;

    /** 无 onset 时默认拟合长度:最近 300 点(约 5 个周期,兼顾稳定性与近期敏感性) */
    private static final int DEFAULT_FIT_POINTS = 300;

    private TrendExtrapolator() {
    }

    /**
     * 趋势外推
     *
     * @param window     时序窗口
     * @param baseline   正常态基线(amplitude 为上包络半宽)
     * @param threshold  告警阈值(AlertRule 的 upper/lower,非 null)
     * @param upperSide  true=上限阈值(要求斜率向上),false=下限阈值(要求斜率向下)
     * @param onsetTs    劣化起点时间戳(epoch millis,null 取最近 300 点拟合)
     * @param props      配置(sinePeriod/wlsLambda/r2Threshold/minSlope/t1MaxPoints)
     * @return 趋势结果;拟合点不足或显著性门未过返回 null;门过但 t1 越界时 t1Points/band 为 null
     */
    public static Result extrapolate(SensorWindow window, Baseline baseline, double threshold,
                                     boolean upperSide, Long onsetTs, PredictProperties props) {
        int p = props.getSinePeriod();
        int n = window.size();
        if (p <= 0 || n < p + MIN_FIT_POINTS) {
            return null;
        }

        // 平滑:窗口 p 的滑动平均,只对 i>=p-1 有完整窗口
        double[] sm = new double[n];
        double sum = 0D;
        for (int i = 0; i < n; i++) {
            sum += window.getVal()[i];
            if (i >= p) {
                sum -= window.getVal()[i - p];
            }
            if (i >= p - 1) {
                sm[i] = sum / p;
            }
        }

        // 拟合段:有 onset 取 onset 之后的平滑点,否则取最近 300 点
        int from = p - 1;
        if (onsetTs != null) {
            for (int i = p - 1; i < n; i++) {
                if (window.getTs()[i] >= onsetTs) {
                    from = i;
                    break;
                }
            }
        } else {
            from = Math.max(from, n - DEFAULT_FIT_POINTS);
        }
        int fitPoints = n - from;
        if (fitPoints < MIN_FIT_POINTS) {
            return null;
        }

        // WLS:y = a + b*i,i 用原始窗口索引;权重 w=lambda^年龄(最新点权重 1)
        double lambda = props.getWlsLambda();
        double sw = 0D, sx = 0D, sy = 0D, sxx = 0D, sxy = 0D;
        for (int i = from; i < n; i++) {
            double w = Math.pow(lambda, n - 1 - i);
            double x = i;
            double y = sm[i];
            sw += w;
            sx += w * x;
            sy += w * y;
            sxx += w * x * x;
            sxy += w * x * y;
        }
        double xBar = sx / sw;
        double yBar = sy / sw;
        double sxxw = sxx - sw * xBar * xBar;
        if (sxxw <= EPS) {
            // 全部权重压在同一点(理论不会):无方差可谈,拟合无意义
            return null;
        }
        double b = (sxy - sw * xBar * yBar) / sxxw;
        double a = yBar - b * xBar;
        // 平滑滞后补偿:窗口 p 的中心在中点,平滑值 sm[i] 实为趋势在
        // i-(p-1)/2 处的取值,拟合截距整体滞后半窗。按斜率前移半窗,
        // 否则触线时刻被系统性推迟(p=60、0.5s 采样即 15 秒)
        double halfLag = (p - 1) / 2.0;
        double aReal = a + b * halfLag;

        // 加权 R2 与残差尺度 sigma
        double sse = 0D, sst = 0D;
        for (int i = from; i < n; i++) {
            double w = Math.pow(lambda, n - 1 - i);
            double residual = sm[i] - (a + b * i);
            sse += w * residual * residual;
            sst += w * (sm[i] - yBar) * (sm[i] - yBar);
        }
        double r2 = sst <= EPS ? (sse <= EPS ? 1D : 0D) : 1D - sse / sst;
        double sigma = Math.sqrt(sse / sw);

        // 显著性门:拟合优度 + 有效斜率 + 方向朝阈值(有上限阈值要求 b>0,反之为下降趋势不外推)
        if (!(r2 > props.getR2Threshold()) || Math.abs(b) < props.getMinSlope()
                || (upperSide ? b <= 0 : b >= 0)) {
            return null;
        }

        // t1:上包络触线(平滑值+幅值到达阈值)距最新点的点数(用补偿后截距)
        double touchIndex = (threshold - baseline.getAmplitude() - aReal) / b;
        double t1 = touchIndex - (n - 1);
        if (t1 < 0 || t1 > props.getT1MaxPoints()) {
            // 已触线(t1 为负)或远超外推时域:不输出 t1 与预测带,仅保留趋势(a/b/r2 可落快照)
            return new Result(aReal, b, r2, sigma, null, 0L, sm[n - 1], null);
        }

        // 预测时刻:窗口相邻点中位间隔推算(等间隔采样下与真实采样节拍对齐)
        long medianGap = medianGap(window);
        long breachTs = window.getTs()[n - 1] + Math.round(t1 * medianGap);

        // 预测带:触线后再看一个周期,最多 600 点;[ts, low, mid, high]
        int horizon = (int) Math.min(t1 + p, 600);
        List<double[]> band = new ArrayList<>(horizon);
        int fitCount = n - from;
        for (int t = 1; t <= horizon; t++) {
            double x = n - 1 + t;
            double mid = aReal + b * x;
            // 回归预测区间半宽:x 离加权质心越远,截距/斜率估计误差放大越多
            double halfWidth = Z95 * sigma * Math.sqrt(1D / fitCount
                    + (x - xBar) * (x - xBar) / sxxw);
            long ts = window.getTs()[n - 1] + (long) t * medianGap;
            band.add(new double[]{ts, mid - halfWidth, mid, mid + halfWidth});
        }
        return new Result(aReal, b, r2, sigma, (int) Math.round(t1), breachTs, sm[n - 1], band);
    }

    /**
     * 最新平滑值(状态机填告警 sensorValue 用:平滑值剔除了周期项,
     * 比裸值更能代表"当前水平位置",避免正弦相位落在谷底时误读为偏低)
     *
     * @param window 时序窗口
     * @param props  配置(sinePeriod)
     * @return 最近 sinePeriod 点均值
     */
    public static double smoothLast(SensorWindow window, PredictProperties props) {
        int p = props.getSinePeriod();
        int n = window.size();
        double sum = 0D;
        for (int i = Math.max(0, n - p); i < n; i++) {
            sum += window.getVal()[i];
        }
        return sum / Math.min(n, p);
    }

    /**
     * 相邻点间隔的中位数(毫秒):比均值抗采样抖动,比 max 贴真实节拍
     */
    private static long medianGap(SensorWindow window) {
        long[] ts = window.getTs();
        long[] gaps = new long[ts.length - 1];
        for (int i = 1; i < ts.length; i++) {
            gaps[i - 1] = ts[i] - ts[i - 1];
        }
        Arrays.sort(gaps);
        int mid = gaps.length / 2;
        return (gaps.length & 1) == 1 ? gaps[mid] : (gaps[mid - 1] + gaps[mid]) / 2;
    }

    /**
     * 趋势外推结果(t1 无效时 t1Points/band 为 null,a/b/r2 仍有效可落快照)
     */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public static class Result {

        /** 拟合截距(原始窗口索引坐标系,已补偿平滑半窗滞后) */
        private final double a;

        /** 拟合斜率(每点增量) */
        private final double b;

        /** 加权拟合优度 R2 */
        private final double r2;

        /** 加权残差 RMS(预测带宽度基准) */
        private final double sigma;

        /** 触线点数(距最新点;null=未给出:已触线/超外推时域/显著性门未过) */
        private final Integer t1Points;

        /** 预计越界时刻(epoch millis;t1 无效时为 0) */
        private final long predictedBreachTimeMs;

        /** 最新平滑值(告警 sensorValue 用) */
        private final double smoothedCurrent;

        /** 预测带 [ts, low, mid, high] 数组列表(null=不输出) */
        private final List<double[]> band;
    }
}
