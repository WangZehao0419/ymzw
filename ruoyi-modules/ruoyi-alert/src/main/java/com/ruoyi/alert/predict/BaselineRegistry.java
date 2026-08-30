package com.ruoyi.alert.predict;

import com.ruoyi.alert.predict.domain.Baseline;
import com.ruoyi.alert.predict.domain.SensorWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 传感器基线注册表
 * <p>
 * 按传感器编号缓存最近一次学习的基线(内存态,单实例假设,与 AlertDetectionService
 * 的持续计数同风格)。检测器(Task 4)用"当前值 vs 基线"做突变判定;
 * 传感器重新注册/长时间断档后由调用方 reset 重学。
 * </p>
 *
 * @author smartartisan
 */
@Component
@RequiredArgsConstructor
public class BaselineRegistry {

    private final PredictProperties props;

    /** 基线缓存: key=sensorCode, value=Baseline */
    private final Map<String, Baseline> baselines = new ConcurrentHashMap<>();

    /**
     * 学习并缓存基线
     *
     * @param sensorCode 传感器编号
     * @param window     时序窗口
     * @return 学习出的基线
     */
    public Baseline learn(String sensorCode, SensorWindow window) {
        Baseline baseline = Baseline.learn(window, props.getWindowPoints(), props.getSinePeriod());
        baselines.put(sensorCode, baseline);
        return baseline;
    }

    /**
     * 清除基线(下次到达时重学)
     *
     * @param sensorCode 传感器编号
     */
    public void reset(String sensorCode) {
        baselines.remove(sensorCode);
    }

    /**
     * 查询基线
     *
     * @param sensorCode 传感器编号
     * @return 基线;未学习过返回 null
     */
    public Baseline get(String sensorCode) {
        return baselines.get(sensorCode);
    }
}
