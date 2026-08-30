package com.ruoyi.alert.predict;

import com.ruoyi.alert.predict.domain.Baseline;
import com.ruoyi.alert.predict.domain.SensorWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CUSUM 缓变漂移检测器单元测试(纯函数,不依赖 Spring)
 *
 * @author smartartisan
 */
class CusumDetectorTest {

    private static final int N = 600;
    private static final int P = 60;
    private static final long BASE_TS = 1_700_000_000_000L;

    private final PredictProperties props = new PredictProperties();

    @Test
    @DisplayName("平稳序列:块均值自消,C+ 恒 0,不触发")
    void stableSeriesNoTrigger() {
        // 全程交替 ±0.3(60 点块内自消,块均值精确为 0),无漂移
        SensorWindow window = driftWindow(0D, N);
        Baseline baseline = baseline();

        CusumDetector.Result r = CusumDetector.detect(window, baseline, props);

        assertFalse(r.isDrift(), "平稳序列不应触发漂移");
        assertEquals(0D, r.getCPlus(), 1e-9);
    }

    @Test
    @DisplayName("已知漂移序列:首个完整劣化块内触发,onset 记录漂移起点")
    void knownDriftTriggersWithOnset() {
        // 模拟器 TEMP-001 场景缩比:第 300 点起每点 +0.01(淹没在 ±0.3 噪声里的缓变漂移)
        // 块均值序列上:块5(i∈[300,360)) 均值偏移 0.01×29.5≈0.295,远超决策线 h≈0.194
        // → 漂移启动后的第一个完整块(60 点≈30 秒)内触发
        int driftStart = 300;
        SensorWindow window = driftWindow(0.01D, driftStart);
        Baseline baseline = baseline();

        CusumDetector.Result r = CusumDetector.detect(window, baseline, props);

        assertTrue(r.isDrift(), "0.01/点的缓变漂移应在首个劣化块内触发");
        // onset = C+ 起跳块(块5)的首点时间戳 = 漂移起点本身
        assertEquals(BASE_TS + driftStart * 500L, r.getOnsetTs(),
                "onset 应为漂移起点块的首点时间戳");
    }

    /**
     * 构造窗口:全程交替 ±0.3 噪声;driftStart 起每点线性抬升 driftPerPoint
     * <p>交替噪声在 60 点块内自消(60 为偶数),块均值只剩漂移贡献,数值确定性。</p>
     */
    private SensorWindow driftWindow(double driftPerPoint, int driftStart) {
        long[] ts = new long[N];
        double[] val = new double[N];
        for (int i = 0; i < N; i++) {
            ts[i] = BASE_TS + i * 500L;
            double v = (i % 2 == 0 ? 0.3 : -0.3);
            if (i >= driftStart) {
                v += driftPerPoint * (i - driftStart);
            }
            val[i] = v;
        }
        return new SensorWindow("TEST", ts, val);
    }

    /**
     * 基线:median=0(正常水平),residualMad=0.3(噪声尺度)
     */
    private Baseline baseline() {
        return new Baseline(0D, 0.5D, 5D, 0.3D, LocalDateTime.now());
    }
}
