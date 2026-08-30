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
 * MAD 突变检测器单元测试(纯函数,不依赖 Spring)
 *
 * @author smartartisan
 */
class MadDetectorTest {

    private static final int N = 600;
    private static final int P = 60;
    private static final long BASE_TS = 1_700_000_000_000L;

    private final PredictProperties props = new PredictProperties();

    @Test
    @DisplayName("平稳噪声:体制比值约 1,不触发")
    void stableNoiseNoTrigger() {
        // 最近 120 点残差 ±1 交替,基线残差 MAD=1 → R=1
        SensorWindow window = windowWithResidualAmplitude(1.0);
        Baseline baseline = baselineWithResidualMad(1.0);

        MadDetector.Result r = MadDetector.detect(window, baseline, props);

        assertFalse(r.isRegimeChange(), "平稳噪声不应触发体制变化");
        assertEquals(1.0, r.getRatio(), 0.001);
    }

    @Test
    @DisplayName("噪声增益×3:体制比值≈3,触发")
    void noiseGainTripleTriggers() {
        // 模拟器 VIB-001 噪声增益场景:近期残差幅度 3,基线 1 → R=3 > 2.0
        SensorWindow window = windowWithResidualAmplitude(3.0);
        Baseline baseline = baselineWithResidualMad(1.0);

        MadDetector.Result r = MadDetector.detect(window, baseline, props);

        assertTrue(r.isRegimeChange(), "噪声×3 应触发体制变化");
        assertEquals(3.0, r.getRatio(), 0.001);
    }

    @Test
    @DisplayName("掩蔽效应:基线混入离群点撑大σ,MAD 比值仍触发而σ比值漏报")
    void maskingEffectMadStillTriggers() {
        // 基线残差被 5 个 +15 离群点污染(模拟历史数据混入劣化尖峰):
        //   105 点 = 50×(-1) + 50×(+1) + 5×15
        //   median=1 → MAD=2(离群点只有 5 个,中位数不被拉偏)
        //   σ≈3.346(离群平方贡献,σ 被严重撑大)
        // 近期残差整体展宽到 ±4.2(真实体制变化,无离群点):
        //   MAD 比值 = 4.2/2 = 2.1 > 2.0 → 触发
        //   σ  比值 = 4.2/3.346 ≈ 1.26 < 2.0 → σ 尺子漏报
        SensorWindow window = windowWithResidualAmplitude(4.2);
        Baseline baseline = baselineWithResidualMad(2.0);

        MadDetector.Result r = MadDetector.detect(window, baseline, props);

        assertTrue(r.isRegimeChange(), "MAD 对离群点鲁棒,体制变化仍应触发");
        double sigmaRatio = 4.2 / 3.346;
        assertTrue(sigmaRatio < props.getMadRatioThreshold(),
                "同场景下σ比值(" + sigmaRatio + ")应低于阈值,验证σ尺子的掩蔽漏报");
    }

    /**
     * 构造窗口:前 N-2P 点为 0,最近 2P 点的同相位残差 r(i)=val(i)-val(i-P) 为 ±amp 交替
     */
    private SensorWindow windowWithResidualAmplitude(double amp) {
        long[] ts = new long[N];
        double[] val = new double[N];
        for (int i = 0; i < N; i++) {
            ts[i] = BASE_TS + i * 500L;
            val[i] = 0D;
        }
        for (int i = N - 2 * P; i < N; i++) {
            int k = i - (N - 2 * P);
            val[i] = val[i - P] + (k % 2 == 0 ? amp : -amp);
        }
        return new SensorWindow("TEST", ts, val);
    }

    /**
     * 构造基线(median/mad/amplitude 对本检测器无影响,residualMad 是比值分母)
     */
    private Baseline baselineWithResidualMad(double residualMad) {
        return new Baseline(0D, 1D, 5D, residualMad, LocalDateTime.now());
    }
}
