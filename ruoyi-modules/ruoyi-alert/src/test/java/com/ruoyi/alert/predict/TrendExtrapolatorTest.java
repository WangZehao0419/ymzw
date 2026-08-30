package com.ruoyi.alert.predict;

import com.ruoyi.alert.predict.domain.Baseline;
import com.ruoyi.alert.predict.domain.SensorWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 趋势外推器单元测试(纯函数,不依赖 Spring)
 *
 * @author smartartisan
 */
class TrendExtrapolatorTest {

    private static final int N = 600;
    private static final int P = 60;
    private static final long BASE_TS = 1_700_000_000_000L;
    private static final long GAP_MS = 500L;

    private final PredictProperties props = new PredictProperties();

    @Test
    @DisplayName("已知斜率序列:t1 误差<1点,预测时刻与外推公式一致")
    void knownSlopeT1Accuracy() {
        // 50 + 0.02i + 5sin(2πi/60):平滑后正弦自消,只剩精确线性趋势
        // 阈值 75、幅值 5 → 上包络触线:50+0.02i+5=75 → i=1000 → t1=1000-599=401
        SensorWindow window = trendWindow(50D, 0.02D, 5D, 0D);
        Baseline baseline = new Baseline(50D, 0D, 5D, 0D, LocalDateTime.now());

        TrendExtrapolator.Result r = TrendExtrapolator.extrapolate(
                window, baseline, 75D, true, null, props);

        assertNotNull(r, "显著线性趋势应输出外推结果");
        assertEquals(0.02D, r.getB(), 1e-6, "斜率应还原 0.02/点");
        assertTrue(r.getR2() > 0.99, "无噪声序列拟合优度应接近 1");
        assertEquals(401, r.getT1Points(), 1, "t1 应约 401 点(误差<1点)");
        assertEquals(BASE_TS + (N - 1) * GAP_MS + 401 * GAP_MS,
                r.getPredictedBreachTimeMs(), "预测越界时刻=最新点+401个采样间隔");
        assertNotNull(r.getBand(), "t1 有效时应输出预测带");
        assertEquals(Math.min(401 + P, 600), r.getBand().size(), "预测带长度=触线+一个周期");
        // 带的最后一点时刻应越过预测越界时刻
        List<double[]> band = r.getBand();
        assertTrue(band.get(band.size() - 1)[0] >= r.getPredictedBreachTimeMs(),
                "预测带末端应覆盖触线时刻");
    }

    @Test
    @DisplayName("平稳噪声序列:斜率近 0 被 min-slope 门拦截,不误报")
    void flatNoiseInterceptedBySlopeGate() {
        // 50 + 5sin + 交替±0.3:平滑后正弦与交替噪声同时自消,平滑序列为常数 50
        // 拟合斜率≈0 → |b|<min-slope(0.005) → 显著性门拦截返回 null
        SensorWindow window = trendWindow(50D, 0D, 5D, 0.3D);
        Baseline baseline = new Baseline(50D, 0.5D, 5D, 0.3D, LocalDateTime.now());

        TrendExtrapolator.Result r = TrendExtrapolator.extrapolate(
                window, baseline, 75D, true, null, props);

        assertNull(r, "平稳序列不应产出趋势外推(防随机波动误报)");
    }

    @Test
    @DisplayName("已越限场景:t1 为负,保留趋势但不输出触线时刻与预测带")
    void alreadyBreachedKeepsTrendWithoutT1() {
        // 阈值 40 低于当前平滑值(≈62):上包络早已越过阈值线
        SensorWindow window = trendWindow(50D, 0.02D, 5D, 0D);
        Baseline baseline = new Baseline(50D, 0D, 5D, 0D, LocalDateTime.now());

        TrendExtrapolator.Result r = TrendExtrapolator.extrapolate(
                window, baseline, 40D, true, null, props);

        assertNotNull(r, "趋势本身有效应保留(快照可落 slope)");
        assertEquals(0.02D, r.getB(), 1e-6);
        assertNull(r.getT1Points(), "已触线不应再给出未来触线点数");
        assertNull(r.getBand(), "已触线不应输出预测带");
    }

    /**
     * 构造窗口:base + slope×i + amp×sin(2πi/60) + 交替±noise
     */
    private SensorWindow trendWindow(double base, double slope, double amp, double noise) {
        long[] ts = new long[N];
        double[] val = new double[N];
        for (int i = 0; i < N; i++) {
            ts[i] = BASE_TS + i * GAP_MS;
            val[i] = base + slope * i + amp * Math.sin(2 * Math.PI * i / 60.0)
                    + (i % 2 == 0 ? noise : -noise);
        }
        return new SensorWindow("TEST", ts, val);
    }
}
