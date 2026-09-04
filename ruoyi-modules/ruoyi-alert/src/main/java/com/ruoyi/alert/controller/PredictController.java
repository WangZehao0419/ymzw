package com.ruoyi.alert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.entity.PredictAlert;
import com.ruoyi.alert.entity.PredictResult;
import com.ruoyi.alert.mapper.PredictAlertMapper;
import com.ruoyi.alert.predict.BaselineRegistry;
import com.ruoyi.alert.predict.PredictProperties;
import com.ruoyi.alert.predict.TrendExtrapolator;
import com.ruoyi.alert.predict.domain.Baseline;
import com.ruoyi.alert.predict.domain.SensorWindow;
import com.ruoyi.alert.service.PredictResultService;
import com.ruoyi.alert.service.RuleService;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;
import com.ruoyi.equipment.api.domain.SensorPointDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 预测性维护页面接口
 * <p>
 * sensors: 全量传感器元数据(Feign) 左连 predict_result 快照,供选择器与状态总览;
 * detail: 实时拉历史窗口现算平滑/趋势(读接口,不动 PredictTask 的基线缓存——
 * 缓存缺失时临时学习不落缓存),曲线数据总是最新一轮调度之后的状态。
 * 时间字段一律 Long epoch millis(D9:规避 LocalDateTime 跨端序列化数组坑)。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@RestController
@RequestMapping("/api/predict")
@RequiredArgsConstructor
public class PredictController {

    private final RemoteEquipmentService remoteEquipmentService;
    private final PredictResultService predictResultService;
    private final RuleService ruleService;
    private final BaselineRegistry baselineRegistry;
    private final PredictProperties props;
    private final PredictAlertMapper predictAlertMapper;

    /**
     * 预测告警分页(predict_alert 独立表,预测性维护页专用)
     * <p>
     * 分表后不再借用告警记录接口,字段名与 alert_event 对齐(D2)前端列表零改动。
     * 筛选参数均可选,仅在前端实际传值时才拼接条件。
     * </p>
     */
    @GetMapping("/alerts")
    public TableDataInfo alerts(@RequestParam(defaultValue = "1") long page,
                                @RequestParam(defaultValue = "10") long size,
                                @RequestParam(required = false) String sensorCode,
                                @RequestParam(required = false) String alertLevel,
                                @RequestParam(required = false) String alertStatus) {
        LambdaQueryWrapper<PredictAlert> wrapper = new LambdaQueryWrapper<PredictAlert>()
                .eq(StringUtils.hasText(sensorCode), PredictAlert::getSensorCode, sensorCode)
                .eq(StringUtils.hasText(alertLevel), PredictAlert::getAlertLevel, alertLevel)
                .eq(StringUtils.hasText(alertStatus), PredictAlert::getAlertStatus, alertStatus)
                // 预测页关注最新触发的预测告警,按触发时间倒序
                .orderByDesc(PredictAlert::getTriggerTime);
        Page<PredictAlert> p = predictAlertMapper.selectPage(new Page<>(page, size), wrapper);
        return new TableDataInfo(p.getRecords(), p.getTotal());
    }

    /**
     * 传感器预测状态列表(页面选择器 + 总览卡片)
     */
    @GetMapping("/sensors")
    public R<List<SensorVO>> sensors() {
        R<List<SensorMetaDTO>> metaResult = remoteEquipmentService.listAllSensors(SecurityConstants.INNER);
        if (metaResult == null || R.FAIL == metaResult.getCode()
                || metaResult.getData() == null || metaResult.getData().isEmpty()) {
            // 元数据服务不可用:页面选择器无数据可言,返回空列表(前端提示重试)
            return R.fail("传感器元数据获取失败");
        }
        Map<String, PredictResult> snapshots = predictResultService.lambdaQuery()
                .list().stream()
                .collect(Collectors.toMap(PredictResult::getSensorCode, Function.identity(), (a, b) -> a));
        List<SensorVO> vos = new ArrayList<>(metaResult.getData().size());
        for (SensorMetaDTO meta : metaResult.getData()) {
            SensorVO vo = new SensorVO();
            vo.setSensorCode(meta.getSensorCode());
            vo.setSensorName(meta.getSensorName());
            vo.setEquipmentId(meta.getEquipmentId());
            vo.setEquipmentName(meta.getEquipmentName());
            vo.setUnit(meta.getUnit());
            PredictResult snapshot = snapshots.get(meta.getSensorCode());
            if (snapshot != null) {
                vo.setStatus(snapshot.getStatus());
                vo.setHealthScore(snapshot.getHealthScore());
                vo.setSlope(snapshot.getSlope());
                vo.setT1Points(snapshot.getT1Points());
                vo.setPredictedBreachTimeMs(toEpochMs(snapshot.getPredictedBreachTime()));
                vo.setOnsetTimeMs(toEpochMs(snapshot.getOnsetTime()));
                vo.setUpdateTimeMs(toEpochMs(snapshot.getUpdateTime()));
            }
            vos.add(vo);
        }
        return R.ok(vos);
    }

    /**
     * 单传感器详情:原始窗口 + 平滑序列 + 趋势外推(阈值线/预测带/t1)
     *
     * @param sensorCode 传感器编号
     * @param window     窗口点数(默认 predict.window-points,上限 2000 与 Feign 契约一致)
     */
    @GetMapping("/detail/{sensorCode}")
    public R<DetailVO> detail(@PathVariable String sensorCode,
                              @RequestParam(required = false) Integer window) {
        int windowPoints = window == null ? props.getWindowPoints() : Math.min(window, 2000);
        R<List<SensorPointDTO>> history = remoteEquipmentService.getSensorHistory(
                sensorCode, windowPoints, SecurityConstants.INNER);
        if (history == null || R.FAIL == history.getCode()
                || history.getData() == null || history.getData().isEmpty()) {
            return R.fail("历史数据获取失败(设备服务不可用或无数据)");
        }
        SensorWindow sensorWindow = SensorWindow.of(sensorCode, history.getData(), windowPoints);
        if (sensorWindow == null) {
            return R.fail("历史数据量不足(新传感器需等待窗口攒满)");
        }

        // 基线:优先用 PredictTask 已学习的缓存;无缓存临时学习(不落缓存,读接口不写共享态)
        Baseline baseline = baselineRegistry.get(sensorCode);
        if (baseline == null) {
            baseline = Baseline.learn(sensorWindow, windowPoints, props.getSinePeriod());
        }

        DetailVO vo = new DetailVO();
        vo.setSensorCode(sensorCode);

        // 原始窗口
        List<PointVO> raw = new ArrayList<>(sensorWindow.size());
        for (int i = 0; i < sensorWindow.size(); i++) {
            raw.add(new PointVO(sensorWindow.getTs()[i], round2(sensorWindow.getVal()[i])));
        }
        vo.setRaw(raw);

        // 平滑序列:与 TrendExtrapolator 同口径的公共方法(前 sinePeriod-1 点无完整窗口为 NaN,跳过)
        double[] sm = TrendExtrapolator.smoothSeries(sensorWindow, props);
        List<PointVO> smoothed = new ArrayList<>(sensorWindow.size());
        for (int i = 0; i < sensorWindow.size(); i++) {
            if (Double.isNaN(sm[i])) {
                continue;
            }
            smoothed.add(new PointVO(sensorWindow.getTs()[i], round2(sm[i])));
        }
        vo.setSmoothed(smoothed);

        // 趋势外推:与 PredictTask 同逻辑——按 sensor_id 查启用规则,优先上限
        // (sensor_code 列靠 controller 回填、历史数据可为 null,按 code 查会漏规则;
        // list+findFirst 而非 one():规则表历史数据存在同码多行,one() 多记录会抛异常)
        Integer sensorId = null;
        R<List<SensorMetaDTO>> metaResult = remoteEquipmentService.listAllSensors(SecurityConstants.INNER);
        if (metaResult != null && R.SUCCESS == metaResult.getCode() && metaResult.getData() != null) {
            sensorId = metaResult.getData().stream()
                    .filter(m -> sensorCode.equals(m.getSensorCode()))
                    .map(SensorMetaDTO::getId)
                    .findFirst().orElse(null);
        }
        if (sensorId == null) {
            return R.fail("传感器不存在");
        }
        AlertRule rule = ruleService.lambdaQuery()
                .eq(AlertRule::getSensorId, sensorId)
                .eq(AlertRule::getEnabled, 1)
                .list().stream().findFirst().orElse(null);
        if (rule != null && (rule.getUpperLimit() != null || rule.getLowerLimit() != null)) {
            Double threshold = rule.getUpperLimit() != null ? rule.getUpperLimit() : rule.getLowerLimit();
            boolean upperSide = rule.getUpperLimit() != null;
            TrendExtrapolator.Result trend = TrendExtrapolator.extrapolate(
                    sensorWindow, baseline, threshold, upperSide, null, props);
            if (trend != null) {
                TrendVO t = new TrendVO();
                t.setA(round4(trend.getA()));
                t.setB(round4(trend.getB()));
                t.setR2(round4(trend.getR2()));
                t.setThreshold(threshold);
                t.setUpperSide(upperSide);
                t.setAmplitude(round4(baseline.getAmplitude()));
                t.setT1Points(trend.getT1Points());
                t.setPredictedBreachTimeMs(trend.getT1Points() == null ? null
                        : trend.getPredictedBreachTimeMs());
                if (trend.getBand() != null) {
                    List<double[]> band = trend.getBand();
                    List<List<Double>> bandVo = new ArrayList<>(band.size());
                    for (double[] b : band) {
                        bandVo.add(List.of(b[0], round2(b[1]), round2(b[2]), round2(b[3])));
                    }
                    t.setBand(bandVo);
                }
                vo.setTrend(t);
            }
        }

        // 最新快照(status 以后端任务落库为准)
        PredictResult snapshot = predictResultService.lambdaQuery()
                .eq(PredictResult::getSensorCode, sensorCode)
                .one();
        if (snapshot != null) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", snapshot.getStatus());
            result.put("slope", snapshot.getSlope());
            result.put("t1Points", snapshot.getT1Points());
            result.put("predictedBreachTimeMs", toEpochMs(snapshot.getPredictedBreachTime()));
            result.put("onsetTimeMs", toEpochMs(snapshot.getOnsetTime()));
            result.put("updateTimeMs", toEpochMs(snapshot.getUpdateTime()));
            vo.setResult(result);
        }
        return R.ok(vo);
    }

    private Long toEpochMs(java.time.LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Double round2(double v) {
        return Math.round(v * 100D) / 100D;
    }

    private Double round4(double v) {
        return Math.round(v * 10000D) / 10000D;
    }

    /**
     * 传感器预测状态(选择器/总览)
     */
    @Data
    public static class SensorVO {
        private String sensorCode;
        private String sensorName;
        private Integer equipmentId;
        private String equipmentName;
        private String unit;
        /** NORMAL/DEGRADING/BREACHED(predict_result 快照;任务未跑过为 null,前端按 NORMAL 处理) */
        private String status;
        /** 健康度评分 0-100(HealthScoreService 每轮计算落库,所有状态均有值;任务未跑过为 null) */
        private Double healthScore;
        private Double slope;
        private Integer t1Points;
        private Long predictedBreachTimeMs;
        private Long onsetTimeMs;
        private Long updateTimeMs;
    }

    /**
     * 详情响应:原始窗口 + 平滑序列 + 趋势 + 快照
     */
    @Data
    public static class DetailVO {
        private String sensorCode;
        private List<PointVO> raw;
        private List<PointVO> smoothed;
        private TrendVO trend;
        private Map<String, Object> result;
    }

    @Data
    @RequiredArgsConstructor
    public static class PointVO {
        /** epoch millis */
        private final long ts;
        private final double val;
    }

    /**
     * 趋势外推结果(t1 无效时 t1Points/predictedBreachTimeMs/band 为 null)
     */
    @Data
    public static class TrendVO {
        private Double a;
        private Double b;
        private Double r2;
        private Double threshold;
        private Boolean upperSide;
        private Double amplitude;
        private Integer t1Points;
        private Long predictedBreachTimeMs;
        /** 预测带 [ts, low, mid, high] */
        private List<List<Double>> band;
    }
}
