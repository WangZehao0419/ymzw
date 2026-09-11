package com.ruoyi.alert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.ai.api.RemoteAiService;
import com.ruoyi.ai.api.domain.AiPredictRequestDTO;
import com.ruoyi.ai.api.domain.AiPredictResultDTO;
import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.entity.PredictAlert;
import com.ruoyi.alert.entity.PredictResult;
import com.ruoyi.alert.mapper.PredictAlertMapper;
import com.ruoyi.alert.predict.PredictProperties;
import com.ruoyi.alert.predict.PredictTask;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 预测性维护页面接口
 * <p>
 * B4 起检测链路切换为模型推理(Feign→ruoyi-ai→pdm-server),
 * T2 起历史窗口由 ruoyi-ai 拉取,本模块不再取数:
 * sensors: 全量传感器元数据(Feign) 左连 predict_result 快照,供选择器与状态总览;
 * detail: 与 PredictTask 完全同入口构造推理请求调 RemoteAiService(只传编码/阈值/步长),
 * 返回原始窗口(取自推理响应回显) + 本轮推理产物(异常评分/健康分/分位带/RUL),统计字段已全部移除;
 * overview: 按设备聚合的健康总览。
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
    private final RemoteAiService remoteAiService;
    private final PredictResultService predictResultService;
    private final RuleService ruleService;
    private final PredictProperties props;
    private final PredictAlertMapper predictAlertMapper;

    /**
     * 证据窗口条数:够展示触发前趋势,请求量可控(inner 历史接口上限 2000)
     */
    private static final int EVIDENCE_POINTS = 300;

    /**
     * 预测告警分页(predict_alert 独立表,预测记录页专用)
     * <p>
     * 分表后不再借用告警记录接口,字段名与 alert_event 对齐(D2)前端列表零改动。
     * 筛选参数均可选,仅在前端实际传值时才拼接条件;
     * 列表以设备为主语展示(传感器信息移入证据),筛选为设备名称(like)。
     * </p>
     */
    @GetMapping("/alerts")
    public TableDataInfo alerts(@RequestParam(defaultValue = "1") long page,
                                @RequestParam(defaultValue = "10") long size,
                                @RequestParam(required = false) String equipmentName,
                                @RequestParam(required = false) String alertStatus) {
        LambdaQueryWrapper<PredictAlert> wrapper = new LambdaQueryWrapper<PredictAlert>()
                // 设备名称用 like 模糊匹配:按名称习惯查找,支持部分名称命中
                .like(StringUtils.hasText(equipmentName), PredictAlert::getEquipmentName, equipmentName)
                .eq(StringUtils.hasText(alertStatus), PredictAlert::getAlertStatus, alertStatus)
                // 预测页关注最新触发的预测告警,按触发时间倒序
                .orderByDesc(PredictAlert::getTriggerTime);
        Page<PredictAlert> p = predictAlertMapper.selectPage(new Page<>(page, size), wrapper);
        return new TableDataInfo(p.getRecords(), p.getTotal());
    }

    /**
     * 预测告警触发前证据:该设备全部传感器在本条告警触发时刻之前的原始数据曲线
     * <p>
     * 设备维度决策支撑:不只看触发传感器的摘要,而展示同设备全部传感器
     * 触发前的数据段,便于判断设备整体态势。窗口截止于 triggerTime(endTimeTs
     * 时间上界),历史记录不混入触发后数据;单传感器拉取失败跳过(log.warn),
     * 不影响其余传感器返回。
     * </p>
     *
     * @param id 预测告警 id
     * @return 该设备各传感器触发前数据点列表(firing=触发该告警的传感器)
     */
    @GetMapping("/alerts/{id}/evidence")
    public R<List<SensorEvidenceVO>> alertEvidence(@PathVariable("id") Long id) {
        PredictAlert alert = predictAlertMapper.selectById(id);
        if (alert == null) {
            return R.fail("预测告警不存在");
        }
        // 该设备全部传感器元数据(名称/单位展示用)
        R<List<SensorMetaDTO>> metaResult = remoteEquipmentService.listAllSensors(SecurityConstants.INNER);
        if (metaResult == null || R.FAIL == metaResult.getCode() || metaResult.getData() == null) {
            return R.fail("传感器元数据获取失败");
        }
        List<SensorMetaDTO> equipmentSensors = metaResult.getData().stream()
                .filter(m -> m.getEquipmentId() != null && alert.getEquipmentId() != null
                        && m.getEquipmentId().equals(alert.getEquipmentId()))
                .collect(Collectors.toList());
        // 触发时刻作时间上界:证据只含触发前数据
        Long endTimeTs = toEpochMs(alert.getTriggerTime());
        List<SensorEvidenceVO> vos = new ArrayList<>(equipmentSensors.size());
        for (SensorMetaDTO meta : equipmentSensors) {
            SensorEvidenceVO vo = new SensorEvidenceVO();
            vo.setSensorCode(meta.getSensorCode());
            vo.setSensorName(meta.getSensorName());
            vo.setUnit(meta.getUnit());
            // firing 标记:本条告警的触发传感器(前端高亮展示)
            vo.setFiring(meta.getSensorCode() != null && meta.getSensorCode().equals(alert.getSensorCode()));
            try {
                R<List<SensorPointDTO>> history = remoteEquipmentService
                        .getSensorHistory(meta.getSensorCode(), EVIDENCE_POINTS, endTimeTs, SecurityConstants.INNER);
                if (history != null && R.SUCCESS == history.getCode() && history.getData() != null) {
                    vo.setPoints(history.getData());
                } else {
                    vo.setPoints(List.of());
                }
            } catch (Exception e) {
                // 单传感器拉取失败跳过:证据是展示类数据,不让单点失败中断整单
                log.warn("[evidence] 传感器触发前窗口拉取失败, alertId={}, sensorCode={}: {}",
                        id, meta.getSensorCode(), e.getMessage());
                vo.setPoints(List.of());
            }
            vos.add(vo);
        }
        return R.ok(vos);
    }

    /**
     * 传感器预测状态列表(页面选择器 + 总览卡片)
     */
    @GetMapping("/sensors")
    public R<List<SensorVO>> sensors() {
        R<List<SensorMetaDTO>> metaResult = remoteEquipmentService.listAllSensors(SecurityConstants.INNER);
        if (metaResult == null || R.FAIL == metaResult.getCode()
                || metaResult.getData() == null || metaResult.getData().isEmpty()) {
            // 元数据服务不可用:页面选择器无数据可言,返回失败(前端提示重试)
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
                vo.setAnomalyScore(snapshot.getAnomalyScore());
                vo.setPredictedBreachTimeMs(toEpochMs(snapshot.getPredictedBreachTime()));
                vo.setModelVersion(snapshot.getModelVersion());
                vo.setUpdateTimeMs(toEpochMs(snapshot.getUpdateTime()));
            }
            vos.add(vo);
        }
        return R.ok(vos);
    }

    /**
     * 单传感器详情:原始窗口 + 本轮模型推理(异常判定/分位带/RUL)
     * <p>
     * 与 PredictTask 完全同入口构造请求(同规则/同步长)调 RemoteAiService,
     * 同轮同传感器结果一致;历史窗口由 ruoyi-ai 拉取并在响应中回显,
     * raw 段直接取回显的 window/windowTs 构造({ts,value} 形态,前端契约不变);
     * 响应为模型推理产物,统计链路字段(slope/r2/band/t1 等)已移除。
     * </p>
     *
     * @param sensorCode 传感器编号
     */
    @GetMapping("/detail/{sensorCode}")
    public R<DetailVO> detail(@PathVariable String sensorCode) {
        // 传感器元数据:展示字段来源(sensor_code 列历史数据可为 null,须按元数据回查)
        R<List<SensorMetaDTO>> metaResult = remoteEquipmentService.listAllSensors(SecurityConstants.INNER);
        SensorMetaDTO meta = null;
        if (metaResult != null && R.SUCCESS == metaResult.getCode() && metaResult.getData() != null) {
            meta = metaResult.getData().stream()
                    .filter(m -> sensorCode.equals(m.getSensorCode()))
                    .findFirst().orElse(null);
        }
        if (meta == null) {
            return R.fail("传感器不存在");
        }

        // 规则查询与 PredictTask 完全一致(含按 id 升序取首条):
        // 同传感器同轮同规则 → 同推理输入 → 结果一致
        AlertRule rule = ruleService.lambdaQuery()
                .eq(AlertRule::getSensorId, meta.getId())
                .eq(AlertRule::getEnabled, 1)
                .orderByAsc(AlertRule::getId)
                .list().stream().findFirst().orElse(null);

        // 模型推理:与 PredictTask 同入口构造请求(窗口由 ruoyi-ai 拉取)
        AiPredictRequestDTO request = PredictTask.buildRequest(
                meta, rule, props.getModel().getHorizon());
        R<AiPredictResultDTO> aiResp = remoteAiService.predict(request, SecurityConstants.INNER);
        if (aiResp == null || R.FAIL == aiResp.getCode() || aiResp.getData() == null) {
            return R.fail("模型推理失败(" + (aiResp == null ? "推理服务无响应" : aiResp.getMsg()) + ")");
        }
        AiPredictResultDTO ai = aiResp.getData();

        DetailVO vo = new DetailVO();
        vo.setSensorCode(sensorCode);
        vo.setSensorName(meta.getSensorName());
        vo.setEquipmentId(meta.getEquipmentId());
        vo.setEquipmentName(meta.getEquipmentName());
        vo.setUnit(meta.getUnit());

        // 原始窗口:由响应回显的 window/windowTs 构造,取 min 长度防两序列意外错位
        List<Double> aiWindow = ai.getWindow() == null ? List.of() : ai.getWindow();
        List<Long> aiWindowTs = ai.getWindowTs() == null ? List.of() : ai.getWindowTs();
        int rawLen = Math.min(aiWindow.size(), aiWindowTs.size());
        List<PointVO> raw = new ArrayList<>(rawLen);
        for (int i = 0; i < rawLen; i++) {
            raw.add(new PointVO(aiWindowTs.get(i), round2(aiWindow.get(i))));
        }
        vo.setRaw(raw);

        // 本轮推理产物(原样透传,与 predict_result 落库值同口径)
        AiVO aiVo = new AiVO();
        aiVo.setIsAnomaly(ai.getIsAnomaly());
        aiVo.setAnomalyScore(ai.getAnomalyScore());
        aiVo.setHealthScore(ai.getHealthScore());
        aiVo.setQ10(ai.getQ10());
        aiVo.setQ50(ai.getQ50());
        aiVo.setQ90(ai.getQ90());
        aiVo.setRulPoint(ai.getRulPoint());
        aiVo.setRulEarliest(ai.getRulEarliest());
        aiVo.setRulLatest(ai.getRulLatest());
        aiVo.setModelVersion(ai.getModelVersion());
        vo.setAi(aiVo);

        // 最新快照的状态与更新时刻(状态机落库,任务未跑过为 null)
        PredictResult snapshot = predictResultService.lambdaQuery()
                .eq(PredictResult::getSensorCode, sensorCode)
                .one();
        if (snapshot != null) {
            vo.setStatus(snapshot.getStatus());
            vo.setUpdateTimeMs(toEpochMs(snapshot.getUpdateTime()));
        }
        return R.ok(vo);
    }

    /**
     * 设备级预测总览:按设备聚合各传感器最新预测快照
     * <p>
     * 设备健康分=成员传感器 healthScore 均值(null 跳过);
     * 聚合状态=任一 BREACHED&gt;任一 DEGRADING&gt;NORMAL;
     * 最紧迫 RUL=成员传感器剩余 RUL(由 predicted_breach_time 反推)最小值及对应传感器。
     * </p>
     */
    @GetMapping("/overview")
    public R<List<EquipmentOverviewVO>> overview() {
        R<List<SensorMetaDTO>> metaResult = remoteEquipmentService.listAllSensors(SecurityConstants.INNER);
        if (metaResult == null || R.FAIL == metaResult.getCode()
                || metaResult.getData() == null || metaResult.getData().isEmpty()) {
            return R.fail("传感器元数据获取失败");
        }
        Map<String, PredictResult> snapshots = predictResultService.lambdaQuery()
                .list().stream()
                .collect(Collectors.toMap(PredictResult::getSensorCode, Function.identity(), (a, b) -> a));
        return R.ok(aggregateOverview(metaResult.getData(), snapshots, LocalDateTime.now()));
    }

    /**
     * 按设备聚合预测总览(纯函数,便于单测)
     *
     * @param snapshots key=sensorCode 的最新快照
     * @param now       RUL 换算基准时刻
     */
    static List<EquipmentOverviewVO> aggregateOverview(List<SensorMetaDTO> metas,
                                                       Map<String, PredictResult> snapshots,
                                                       LocalDateTime now) {
        // LinkedHashMap 保序:设备按元数据出现顺序输出,前端展示稳定不跳变
        Map<Integer, List<PredictResult>> byEquipment = new LinkedHashMap<>();
        Map<Integer, String> equipmentNames = new LinkedHashMap<>();
        for (SensorMetaDTO meta : metas) {
            equipmentNames.putIfAbsent(meta.getEquipmentId(), meta.getEquipmentName());
            // 任务未跑过的传感器无快照:设备仍进列表(默认 NORMAL),只是不参与数值聚合
            PredictResult snapshot = snapshots.get(meta.getSensorCode());
            if (snapshot != null) {
                byEquipment.computeIfAbsent(meta.getEquipmentId(), k -> new ArrayList<>()).add(snapshot);
            }
        }
        List<EquipmentOverviewVO> result = new ArrayList<>(equipmentNames.size());
        for (Map.Entry<Integer, String> entry : equipmentNames.entrySet()) {
            List<PredictResult> members = byEquipment.getOrDefault(entry.getKey(), List.of());
            EquipmentOverviewVO vo = new EquipmentOverviewVO();
            vo.setEquipmentId(entry.getKey());
            vo.setEquipmentName(entry.getValue());
            vo.setStatus(aggregateStatus(members));
            vo.setHealthScore(averageHealth(members));
            // 最紧迫 RUL:剩余分钟最小者(可能为负=预测失效时刻已过,恰是最紧迫)
            Long minRul = null;
            String minSensor = null;
            for (PredictResult s : members) {
                Long rul = rulMinutes(s, now);
                if (rul != null && (minRul == null || rul < minRul)) {
                    minRul = rul;
                    minSensor = s.getSensorCode();
                }
            }
            vo.setMinRulPoint(minRul);
            vo.setMinRulSensorCode(minSensor);
            result.add(vo);
        }
        return result;
    }

    /**
     * 聚合状态:任一 BREACHED &gt; 任一 DEGRADING &gt; NORMAL(无快照成员按 NORMAL 兜底)
     */
    static String aggregateStatus(List<PredictResult> members) {
        boolean degrading = false;
        for (PredictResult s : members) {
            if ("BREACHED".equals(s.getStatus())) {
                return "BREACHED";
            }
            if ("DEGRADING".equals(s.getStatus())) {
                degrading = true;
            }
        }
        return degrading ? "DEGRADING" : "NORMAL";
    }

    /**
     * 设备健康分:成员传感器 healthScore 算术平均,null 跳过(不按 0 计入);
     * 全 null 时设备无分(返回 null,前端按无数据展示);保留 1 位小数与单传感器口径一致
     */
    static Double averageHealth(List<PredictResult> members) {
        double sum = 0D;
        int n = 0;
        for (PredictResult s : members) {
            if (s.getHealthScore() != null) {
                sum += s.getHealthScore();
                n++;
            }
        }
        if (n == 0) {
            return null;
        }
        return Math.round(sum / n * 10D) / 10D;
    }

    /**
     * 剩余 RUL(分钟):predict_result 未单独存 rulPoint 原值,
     * 由 predicted_breach_time(落库时刻+rulPoint 分钟)反推,随读取时刻自然递减
     */
    static Long rulMinutes(PredictResult snapshot, LocalDateTime now) {
        if (snapshot.getPredictedBreachTime() == null) {
            return null;
        }
        return ChronoUnit.MINUTES.between(now, snapshot.getPredictedBreachTime());
    }

    private Long toEpochMs(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Double round2(double v) {
        return Math.round(v * 100D) / 100D;
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
        /** 健康评分 0-100(模型推理每轮产出,所有状态均有值;任务未跑过为 null) */
        private Double healthScore;
        /** 异常评分(越高越异常) */
        private Double anomalyScore;
        /** 预计失效时刻(epoch millis,RUL 点估计换算;无 RUL/NORMAL 态为 null) */
        private Long predictedBreachTimeMs;
        /** 推理模型版本 */
        private String modelVersion;
        private Long updateTimeMs;
    }

    /**
     * 设备预测总览行
     */
    @Data
    public static class EquipmentOverviewVO {
        private Integer equipmentId;
        private String equipmentName;
        /** 聚合状态:任一 BREACHED>任一 DEGRADING>NORMAL(无快照默认 NORMAL) */
        private String status;
        /** 设备健康分:成员传感器 healthScore 均值(null 跳过;全空为 null) */
        private Double healthScore;
        /** 最紧迫 RUL(剩余分钟):成员传感器最小值;全空为 null(可为负=失效时刻已过) */
        private Long minRulPoint;
        /** 最紧迫 RUL 对应的传感器编号 */
        private String minRulSensorCode;
    }

    /**
     * 详情响应:传感器基本信息 + 原始窗口 + 本轮模型推理结果
     */
    @Data
    public static class DetailVO {
        private String sensorCode;
        private String sensorName;
        private Integer equipmentId;
        private String equipmentName;
        private String unit;
        /** 状态机状态(最新快照;任务未跑过为 null) */
        private String status;
        private Long updateTimeMs;
        private List<PointVO> raw;
        /** 本轮模型推理产物(与 PredictTask 同入口,同轮同传感器结果一致) */
        private AiVO ai;
    }

    /**
     * 模型推理结果(字段与 AiPredictResultDTO 同构,原样透传)
     */
    @Data
    public static class AiVO {
        /** 是否异常 */
        private Boolean isAnomaly;
        /** 异常评分(越高越异常) */
        private Double anomalyScore;
        /** 健康评分 */
        private Double healthScore;
        /** P10 分位预测序列(悲观界) */
        private List<Double> q10;
        /** P50 分位预测序列(中位数) */
        private List<Double> q50;
        /** P90 分位预测序列(乐观界) */
        private List<Double> q90;
        /** RUL 点估计(分钟,可空) */
        private Long rulPoint;
        /** RUL 最早失效(分钟,可空) */
        private Long rulEarliest;
        /** RUL 最晚失效(分钟,可空) */
        private Long rulLatest;
        /** 模型版本 */
        private String modelVersion;
    }

    @Data
    @RequiredArgsConstructor
    public static class PointVO {
        /** epoch millis */
        private final long ts;
        private final double val;
    }

    /**
     * 预测告警触发前证据:单传感器维度(firing=触发该告警的传感器)
     */
    @Data
    public static class SensorEvidenceVO {
        private String sensorCode;
        private String sensorName;
        /** 传感器参数单位(rpm/°C/mm/s) */
        private String unit;
        /** 是否为本条告警的触发传感器(前端高亮展示) */
        private Boolean firing;
        /** 触发时刻之前的原始数据点(时间升序,ts epoch millis) */
        private List<SensorPointDTO> points;
    }
}
