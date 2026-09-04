package com.ruoyi.alert.predict;

import com.ruoyi.alert.predict.domain.Baseline;
import com.ruoyi.alert.predict.domain.SensorWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基线注册表单元测试(纯内存,不依赖 Spring)
 * <p>
 * 核心验证维护复位后的基线重学防污染:窗口内复位时刻之后的数据占比
 * 不足 90% 时不重学(旧退化数据会把基线学歪),攒够后才学并清除标记。
 * </p>
 *
 * @author smartartisan
 */
class BaselineRegistryTest {

    private static final int N = 600;
    /** 旧窗口基准时刻(2023-11,远早于测试运行的当前时刻) */
    private static final long OLD_BASE_TS = 1_700_000_000_000L;
    private static final long GAP_MS = 500L;

    private final PredictProperties props = new PredictProperties();
    private final BaselineRegistry registry = new BaselineRegistry(props);

    @Test
    @DisplayName("新传感器无复位标记:shouldLearn 立即 true,learn 后 get 非空")
    void newSensorLearnsImmediately() {
        SensorWindow window = window(OLD_BASE_TS);
        assertTrue(registry.shouldLearn("T", window),
                "无 pendingRelearn 标记(新传感器)应立即允许学习");
        Baseline baseline = registry.learn("T", window);
        assertNotNull(baseline, "learn 应返回基线");
        assertNotNull(registry.get("T"), "learn 后缓存应可查到基线");
    }

    @Test
    @DisplayName("复位后窗口数据全部早于复位时刻:shouldLearn=false(防旧退化数据污染)")
    void resetWithOldWindowRefusesLearn() {
        registry.learn("T", window(OLD_BASE_TS));
        registry.reset("T");
        assertNull(registry.get("T"), "reset 后基线缓存应清空");
        // 窗口 ts 全部为 2023 年,均早于复位时刻(测试运行的当前时间) → 占比 0 < 0.9
        assertFalse(registry.shouldLearn("T", window(OLD_BASE_TS)),
                "复位后旧数据占比 100% 应拒绝重学");
    }

    @Test
    @DisplayName("复位后窗口 90% 以上点晚于复位时刻:允许重学且 learn 清除标记")
    void resetWithFreshWindowLearnsAndClearsFlag() {
        registry.learn("T", window(OLD_BASE_TS));
        registry.reset("T");
        // 窗口起点取复位之后 1 秒:全部 600 点晚于复位时刻,占比 100% >= 0.9
        long afterReset = System.currentTimeMillis() + 1000L;
        SensorWindow freshWindow = window(afterReset);
        assertTrue(registry.shouldLearn("T", freshWindow),
                "复位后新数据占比 100% 应允许重学");
        registry.learn("T", freshWindow);
        // 标记已清除:即使再传旧窗口(全部早于复位时刻)也应按新传感器路径返回 true
        assertTrue(registry.shouldLearn("T", window(OLD_BASE_TS)),
                "learn 成功后应清除待重学标记,再判断返回 true");
    }

    @Test
    @DisplayName("learn 产出的基线统计量可用(median/MAD/幅值/残差 MAD 均为有限值)")
    void learnedBaselineStatsUsable() {
        Baseline baseline = registry.learn("T", window(OLD_BASE_TS));
        assertNotNull(baseline, "learn 应返回基线");
        assertNotNull(baseline.getLearnedAt(), "学习时间应非空");
        assertTrue(Double.isFinite(baseline.getMedian()), "median 应为有限值");
        assertTrue(Double.isFinite(baseline.getMad()), "MAD 应为有限值");
        assertTrue(Double.isFinite(baseline.getAmplitude()), "幅值应为有限值");
        assertTrue(Double.isFinite(baseline.getResidualMad()), "残差 MAD 应为有限值");
    }

    /**
     * 构造窗口:startTs 起等间隔 GAP_MS 采样,值=50+5sin(2πi/60)
     */
    private SensorWindow window(long startTs) {
        long[] ts = new long[N];
        double[] val = new double[N];
        for (int i = 0; i < N; i++) {
            ts[i] = startTs + i * GAP_MS;
            val[i] = 50D + 5D * Math.sin(2 * Math.PI * i / 60.0);
        }
        return new SensorWindow("T", ts, val);
    }
}
