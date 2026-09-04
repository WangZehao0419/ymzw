package com.ruoyi.alert.predict;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.entity.PredictAlert;
import com.ruoyi.alert.event.AlertTriggeredEvent;
import com.ruoyi.alert.mapper.PredictAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ruoyi.equipment.api.domain.SensorMetaDTO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预测性维护劣化状态机(per sensor)
 * <p>
 * 状态: NORMAL → DEGRADING → BREACHED
 * - NORMAL→DEGRADING: L2 突变(MAD 体制变化/CUSUM 漂移)或 L3 显著趋势
 *   (R2 达标且 t1 在外推时域内)。入态发一次 PREDICT 告警(D7 防刷屏:
 *   同一劣化期只发一条,后续靠升级/恢复更新该条,不重复发);
 * - DEGRADING→BREACHED: 实测规则告警(RULE)命中说明预测兑现,活动
 *   PREDICT 告警置 RESOLVED(预测的使命已结束,后续由 RULE 告警接管);
 * - DEGRADING→NORMAL(幽灵退出): t1 较上次推后超阈值说明劣化在放缓,
 *   之前的"即将越限"是趋势误读,告警置 RESOLVED 防止长期挂一条不兑现的预测;
 * - 任意→NORMAL: 维护复位(reset),同时清基线让下轮重学。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PredictStateMachine {

    private final PredictProperties props;
    private final BaselineRegistry baselineRegistry;
    private final ApplicationEventPublisher eventPublisher;
    /** 预测告警独立落 predict_alert(D1),句柄更新必须同表命中,故不走 AlertEventMapper */
    private final PredictAlertMapper predictAlertMapper;
    private final ObjectMapper objectMapper;

    /** 传感器状态缓存: key=sensorCode(内存态,单实例假设,与持续计数同风格) */
    private final Map<String, SensorState> states = new ConcurrentHashMap<>();

    /**
     * 单传感器劣化状态(内存态)
     */
    @lombok.Getter
    @lombok.Setter
    static class SensorState {
        private String status = "NORMAL";
        private Long alertEventId;
        private Integer lastT1Points;
        private boolean predictiveNotified;

        void to(String next) {
            this.status = next;
        }
    }

    /**
     * 推进单传感器状态机并处理告警的发出/升级/恢复
     *
     * @param sensor         传感器元数据(告警展示字段来源)
     * @param mad            L2 MAD 检测结果
     * @param cusum          L2 CUSUM 检测结果
     * @param trend          L3 趋势外推结果(null=无规则/未过显著性门)
     * @param smoothedValue  当前平滑值(告警 sensorValue 展示用)
     * @return 推进后的状态(NORMAL/DEGRADING/BREACHED)
     */
    public String advance(SensorMetaDTO sensor, MadDetector.Result mad, CusumDetector.Result cusum,
                          TrendExtrapolator.Result trend, double smoothedValue) {
        String code = sensor.getSensorCode();
        SensorState st = states.computeIfAbsent(code, k -> new SensorState());
        boolean l2Hit = mad.isRegimeChange() || cusum.isDrift();
        Integer t1 = trend != null ? trend.getT1Points() : null;
        synchronized (st) {
            switch (st.getStatus()) {
                case "NORMAL" -> {
                    // 入态双通道:L2 突变(还没形成趋势)或 L3 显著趋势(已能算出触线点数)
                    if (l2Hit || t1 != null) {
                        st.to("DEGRADING");
                        st.setLastT1Points(t1);
                        firePredictAlert(sensor, mad, cusum, trend, smoothedValue, st);
                    }
                }
                case "DEGRADING" -> {
                    // 幽灵退出:仅当有 t1 且较上次推后超阈值(小幅波动不退出,防反复横跳刷告警)
                    if (t1 != null && st.getLastT1Points() != null
                            && t1 - st.getLastT1Points() > props.getT1DeferExitPoints()) {
                        resolveActiveAlert(code, st, "幽灵退出(t1 推后超阈值)");
                        resetInner(code, st);
                    } else {
                        if (t1 != null) {
                            st.setLastT1Points(t1);
                            if (!st.isPredictiveNotified()) {
                                // 升级预留:首次从"L2 突变"升级为"可预测越界时刻",
                                // 更新已有告警(predictedBreachTime/evidence/level 升 SEVERE)不新发
                                st.setPredictiveNotified(true);
                                escalateToPredictive(sensor, mad, cusum, trend, st);
                            } else if (st.getAlertEventId() != null) {
                                // 后续轮次只刷新触线时刻(趋势演进中 t1 会变,保持告警信息最新)
                                refreshBreachTime(trend, st);
                            }
                        }
                    }
                }
                // BREACHED: 预测已兑现,等维护复位(reset)回归 NORMAL,期间不再发预测
                default -> { }
            }
            return st.getStatus();
        }
    }

    /**
     * 实测规则告警联动:RULE 告警命中即预测兑现
     * <p>
     * 监听全局 AlertTriggeredEvent(与落库/通知同一事件链路),只关心
     * RULE 类型:L1 实测越界说明劣化已到阈值,PREDICT 告警使命完成。
     * </p>
     */
    @EventListener
    public void onRuleAlert(AlertTriggeredEvent event) {
        AlertEvent alert = event.getAlertEvent();
        if (!"RULE".equals(alert.getAlertType()) || alert.getSensorCode() == null) {
            return;
        }
        SensorState st = states.get(alert.getSensorCode());
        if (st == null) {
            return;
        }
        synchronized (st) {
            if (!"DEGRADING".equals(st.getStatus())) {
                return;
            }
            resolveActiveAlert(alert.getSensorCode(), st, "实测越界(RULE 告警命中)");
            st.to("BREACHED");
        }
    }

    /**
     * 维护复位:状态回 NORMAL + 清基线(下轮重学)+ 活动预测告警置 RESOLVED
     *
     * @param sensorCode 传感器编号
     */
    public void reset(String sensorCode) {
        SensorState st = states.get(sensorCode);
        if (st == null) {
            // 无内存状态也清基线:维护后正常态参照必须重建
            baselineRegistry.reset(sensorCode);
            return;
        }
        synchronized (st) {
            resolveActiveAlert(sensorCode, st, "维护复位");
            resetInner(sensorCode, st);
        }
    }

    /**
     * 查询传感器当前状态(PredictTask 落快照用;未见过的传感器视为 NORMAL)
     */
    public String status(String sensorCode) {
        SensorState st = states.get(sensorCode);
        return st == null ? "NORMAL" : st.getStatus();
    }

    /**
     * 入态发 PREDICT 告警(WARNING 起步),走与 L1 相同的事件链路(落库/流推送)
     */
    private void firePredictAlert(SensorMetaDTO sensor, MadDetector.Result mad, CusumDetector.Result cusum,
                                  TrendExtrapolator.Result trend, double smoothedValue, SensorState st) {
        AlertEvent alert = new AlertEvent();
        alert.setEquipmentId(sensor.getEquipmentId());
        alert.setEquipmentName(sensor.getEquipmentName());
        alert.setSensorId(sensor.getId());
        alert.setSensorCode(sensor.getSensorCode());
        alert.setSensorName(sensor.getSensorName());
        alert.setAlertType("PREDICT");
        // 入态即带 t1 说明已能预测越界时刻,直接 SEVERE;纯 L2 突变先 WARNING,升级时再抬
        boolean predictive = trend != null && trend.getT1Points() != null;
        alert.setAlertLevel(predictive ? "SEVERE" : "WARNING");
        alert.setAlertStatus("FIRING");
        // 平滑值是滑动平均的原始浮点结果(如 49.998666...),传感器原始上报值本身
        // 为两位小数;此处取两位与 L1 告警口径一致,避免展示/语音播报输出一长串小数
        alert.setSensorValue(Math.round(smoothedValue * 100D) / 100D);
        alert.setTriggerTime(LocalDateTime.now());
        alert.setPredictedBreachTime(predictive
                ? toLocalDateTime(trend.getPredictedBreachTimeMs()) : null);
        alert.setEscalationCount(0);
        alert.setEvidence(buildEvidence(mad, cusum, trend));
        eventPublisher.publishEvent(new AlertTriggeredEvent(this, alert));
        // 落库监听器同步 insert 后主键回填到实体,此处取回留作后续升级/恢复的更新句柄
        st.setAlertEventId(alert.getId());
        st.setPredictiveNotified(predictive);
        log.info("[PREDICT] 劣化入态告警: sensorCode={}, level={}, t1={}, madRatio={}, cusumDrift={}",
                sensor.getSensorCode(), alert.getAlertLevel(),
                trend == null ? null : trend.getT1Points(),
                String.format("%.2f", mad.getRatio()), cusum.isDrift());
    }

    /**
     * L2 告警升级为可预测告警:更新原告警的触线时刻/证据/等级,不新发(防刷屏)
     */
    private void escalateToPredictive(SensorMetaDTO sensor, MadDetector.Result mad, CusumDetector.Result cusum,
                                      TrendExtrapolator.Result trend, SensorState st) {
        if (st.getAlertEventId() == null) {
            // 无活动告警句柄(如落库失败):补发一条,不让升级信息丢失
            firePredictAlert(sensor, mad, cusum, trend, trend.getSmoothedCurrent(), st);
            return;
        }
        AlertEvent upd = new AlertEvent();
        upd.setId(st.getAlertEventId());
        upd.setAlertLevel("SEVERE");
        upd.setPredictedBreachTime(toLocalDateTime(trend.getPredictedBreachTimeMs()));
        upd.setEvidence(buildEvidence(mad, cusum, trend));
        // 句柄 id 来自 predict_alert 落库回填,更新须转 PredictAlert 同表命中(D1)
        predictAlertMapper.updateById(PredictAlert.from(upd));
        log.info("[PREDICT] 告警升级为可预测(SEVERE): sensorCode={}, alertEventId={}, t1={}",
                sensor.getSensorCode(), st.getAlertEventId(), trend.getT1Points());
    }

    /**
     * 刷新活动告警的触线时刻与证据(趋势演进中 t1 持续变化)
     */
    private void refreshBreachTime(TrendExtrapolator.Result trend, SensorState st) {
        AlertEvent upd = new AlertEvent();
        upd.setId(st.getAlertEventId());
        upd.setPredictedBreachTime(toLocalDateTime(trend.getPredictedBreachTimeMs()));
        // 句柄 id 来自 predict_alert 落库回填,更新须转 PredictAlert 同表命中(D1)
        predictAlertMapper.updateById(PredictAlert.from(upd));
    }

    /**
     * 活动预测告警置 RESOLVED(兑现/退出/复位三场景共用)
     */
    private void resolveActiveAlert(String sensorCode, SensorState st, String reason) {
        if (st.getAlertEventId() == null) {
            return;
        }
        AlertEvent upd = new AlertEvent();
        upd.setId(st.getAlertEventId());
        upd.setAlertStatus("RESOLVED");
        upd.setResolveTime(LocalDateTime.now());
        // 句柄 id 来自 predict_alert 落库回填,更新须转 PredictAlert 同表命中(D1)
        predictAlertMapper.updateById(PredictAlert.from(upd));
        log.info("[PREDICT] 告警已恢复: sensorCode={}, alertEventId={}, reason={}",
                sensorCode, st.getAlertEventId(), reason);
    }

    /**
     * 状态内部复位:回 NORMAL + 清告警句柄/退出比较基准(基线由 reset 调用方清)
     */
    private void resetInner(String sensorCode, SensorState st) {
        st.to("NORMAL");
        st.setAlertEventId(null);
        st.setLastT1Points(null);
        st.setPredictiveNotified(false);
        baselineRegistry.reset(sensorCode);
    }

    /**
     * 证据 JSON:layer/slope/r2/onset/madRatio/cusum/t1Points(排查与前端展示用)
     */
    private String buildEvidence(MadDetector.Result mad, CusumDetector.Result cusum,
                                 TrendExtrapolator.Result trend) {
        try {
            Map<String, Object> ev = new HashMap<>();
            ev.put("layer", "PREDICT");
            ev.put("slope", trend == null ? null : round(trend.getB()));
            ev.put("r2", trend == null ? null : round(trend.getR2()));
            ev.put("onset", cusum != null && cusum.isDrift() ? cusum.getOnsetTs() : null);
            ev.put("madRatio", round(mad.getRatio()));
            ev.put("cusum", cusum != null && cusum.isDrift() ? round(cusum.getCPlus()) : null);
            ev.put("t1Points", trend == null ? null : trend.getT1Points());
            return objectMapper.writeValueAsString(ev);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Double round(double v) {
        return Math.round(v * 10000D) / 10000D;
    }

    private LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs),
                java.time.ZoneId.systemDefault());
    }
}
