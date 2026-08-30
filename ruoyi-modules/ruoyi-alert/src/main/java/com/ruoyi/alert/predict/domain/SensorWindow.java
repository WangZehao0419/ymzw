package com.ruoyi.alert.predict.domain;

import com.ruoyi.equipment.api.domain.SensorPointDTO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 传感器时序窗口(预测性取数管道的域类)
 * <p>
 * 承载单传感器一轮拉取的最近时序点:ts 升序、已剔除间隔&lt;100ms 的突发重复点。
 * 平行数组而非对象列表:窗口最多数千点,检测器(Task 4/5)需密集按下标访问,
 * 平行数组避免逐点解引用,也便于直接喂给数值计算。
 * </p>
 *
 * @author smartartisan
 */
@Getter
@RequiredArgsConstructor
public class SensorWindow {

    /** 相邻点间隔小于该值(毫秒)视为重复上报剔除 */
    private static final long DEDUP_GAP_MS = 100L;

    private final String sensorCode;

    /** 采集时间数组(升序,epoch millis) */
    private final long[] ts;

    /** 数值数组(与 ts 下标一一对应) */
    private final double[] val;

    /**
     * 窗口点数
     */
    public int size() {
        return ts.length;
    }

    /**
     * 由 Feign 拉取的历史点构建窗口
     * <p>
     * 步骤:过滤脏点(时间戳/数值缺失或非有限值) → 按 ts 升序 → 剔除与相邻保留点
     * 间隔&lt;100ms 的重复点(模拟器重放/入库补写产生的突发重复) → 数据量不足
     * windowPoints*0.8 时视为窗口未成型返回 null,由调用方跳过本轮(基线不稳不可学)。
     * </p>
     *
     * @param sensorCode   传感器编号
     * @param points       Feign 返回的历史点(允许乱序/含脏点)
     * @param windowPoints 配置的期望窗口点数
     * @return 窗口;数据不足或全脏时 null
     */
    public static SensorWindow of(String sensorCode, List<SensorPointDTO> points, int windowPoints) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        // 时序库返回已升序,此处再排序一次防上游乱序;脏点直接剔除
        List<SensorPointDTO> cleaned = points.stream()
                .filter(p -> p != null && p.getTs() != null
                        && p.getVal() != null && Double.isFinite(p.getVal()))
                .sorted(Comparator.comparingLong(SensorPointDTO::getTs))
                .toList();
        List<Long> tsList = new ArrayList<>(cleaned.size());
        List<Double> valList = new ArrayList<>(cleaned.size());
        long lastTs = 0L;
        for (SensorPointDTO p : cleaned) {
            if (!tsList.isEmpty() && p.getTs() - lastTs < DEDUP_GAP_MS) {
                continue;
            }
            tsList.add(p.getTs());
            valList.add(p.getVal());
            lastTs = p.getTs();
        }
        // 窗口未满八成:新注册传感器/时序库断档都会触发,不足则本轮跳过
        if (tsList.size() < windowPoints * 0.8) {
            return null;
        }
        long[] ts = new long[tsList.size()];
        double[] val = new double[tsList.size()];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = tsList.get(i);
            val[i] = valList.get(i);
        }
        return new SensorWindow(sensorCode, ts, val);
    }
}
