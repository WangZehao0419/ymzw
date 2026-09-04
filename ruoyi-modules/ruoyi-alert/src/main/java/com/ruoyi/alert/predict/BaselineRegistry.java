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

    /** 待重学标记: key=sensorCode, value=复位时刻 epoch millis */
    private final Map<String, Long> pendingRelearnTs = new ConcurrentHashMap<>();

    /**
     * 学习并缓存基线
     * <p>
     * 学习成功后清除待重学标记:窗口已按新数据完成重学,下轮回到常规路径。
     * </p>
     *
     * @param sensorCode 传感器编号
     * @param window     时序窗口
     * @return 学习出的基线
     */
    public Baseline learn(String sensorCode, SensorWindow window) {
        Baseline baseline = Baseline.learn(window, props.getWindowPoints(), props.getSinePeriod());
        baselines.put(sensorCode, baseline);
        pendingRelearnTs.remove(sensorCode);
        return baseline;
    }

    /**
     * 清除基线并记录复位时刻(下次到达时按 shouldLearn 判定重学时机)
     *
     * @param sensorCode 传感器编号
     */
    public void reset(String sensorCode) {
        // 无基线也清:reset 语义是"下次重学";同时记录复位时刻供 shouldLearn 判断新数据占比
        baselines.remove(sensorCode);
        pendingRelearnTs.put(sensorCode, System.currentTimeMillis());
    }

    /**
     * 基线缺失时是否允许立即学习
     * <p>
     * 复位后窗口(600 点≈5 分钟)内大部分仍是退化期旧数据,立即重学会把
     * 退化水平(如 88°C 封顶段)学成新基线,导致后续 MAD/CUSUM 判定全部失真;
     * 仅当窗口内复位时刻之后的数据占比 ≥ 90% 才重学。
     * 无标记 = 新传感器首次学习,立即学(行为与旧版一致)。
     * </p>
     *
     * @param sensorCode 传感器编号
     * @param window     时序窗口
     * @return true=允许学习;false=新数据未攒够,本轮应跳过检测
     */
    public boolean shouldLearn(String sensorCode, SensorWindow window) {
        Long resetTs = pendingRelearnTs.get(sensorCode);
        if (resetTs == null) {
            return true;
        }
        long[] ts = window.getTs();
        int after = 0;
        for (long t : ts) {
            if (t > resetTs) {
                after++;
            }
        }
        return (double) after / ts.length >= 0.9;
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
