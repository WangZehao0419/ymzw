package com.ruoyi.alert.predict;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.entity.PredictAlert;
import com.ruoyi.alert.event.AlertTriggeredEvent;
import com.ruoyi.alert.mapper.PredictAlertMapper;
import com.ruoyi.ai.api.domain.AiPredictResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ruoyi.equipment.api.domain.SensorMetaDTO;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预测性维护劣化状态机(per sensor)
 * <p>
 * B4 起检测信号源由统计算法(MAD/CUSUM/趋势外推)切换为模型推理
 * (RemoteAiService→ruoyi-ai→pdm-server),迁移条件改看 isAnomaly 持续性,
 * 状态语义与告警生命周期保持不变:
 * 状态: NORMAL → DEGRADING → BREACHED
 * - NORMAL→DEGRADING: isAnomaly 连续 true 轮数达到 anomalyRounds
 *   (模型单轮毛刺不触发,替代原"L2 突变单轮触发")。入态发一次 PREDICT 告警
 *   (D7 防刷屏:同一劣化期只发一条,后续靠升级/恢复更新该条,不重复发);
 * - DEGRADING→BREACHED: 实测规则告警(RULE)命中说明预测兑现,活动
 *   PREDICT 告警置 RESOLVED(预测的使命已结束,后续由 RULE 告警接管);
 * - DEGRADING→NORMAL(幽灵退出): RUL 较上轮推后超阈值说明劣化在放缓,
 *   之前的"即将失效"是模型误读,告警置 RESOLVED 防止长期挂一条不兑现的预测;
 * - 任意→NORMAL: 维护复位(reset)。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PredictStateMachine {

    private final PredictProperties props;
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
        /** 上轮 RUL 点估计(分钟):幽灵退出的推后比较基准 */
        private Long lastRulPoint;
        /** 已升级为可预测告警(告警携带失效时刻) */
        private boolean predictiveNotified;
        /** isAnomaly 连续 true 轮计数(中断即清零) */
        private int anomalyRounds;

        void to(String next) {
            this.status = next;
        }
    }

    /**
     * 推进单传感器状态机并处理告警的发出/升级/恢复
     *
     * @param sensor      传感器元数据(告警展示字段来源)
     * @param ai          本轮模型推理结果(null 视为无异常信号,计连续轮清零)
     * @param sensorValue 当前传感器值(窗口末点原始值,告警 sensorValue 展示用)
     * @return 推进后的状态(NORMAL/DEGRADING/BREACHED)
     */
    public String advance(SensorMetaDTO sensor, AiPredictResultDTO ai, double sensorValue) {
        String code = sensor.getSensorCode();
        SensorState st = states.computeIfAbsent(code, k -> new SensorState());
        boolean anomaly = ai != null && Boolean.TRUE.equals(ai.getIsAnomaly());
        Long rul = ai != null ? ai.getRulPoint() : null;
        synchronized (st) {
            // 连续异常轮计数:非异常(含推理结果缺失)立即清零,入态只认连续命中
            st.setAnomalyRounds(anomaly ? st.getAnomalyRounds() + 1 : 0);
            switch (st.getStatus()) {
                case "NORMAL" -> {
                    // 入态看 isAnomaly 持续性:连续 anomalyRounds 轮异常才认定劣化开始
                    if (st.getAnomalyRounds() >= props.getAnomalyRounds()) {
                        st.to("DEGRADING");
                        st.setLastRulPoint(rul);
                        firePredictAlert(sensor, ai, sensorValue, st);
                    }
                }
                case "DEGRADING" -> {
                    // 幽灵退出:仅当 RUL 较上轮推后超阈值(小幅波动不退出,防反复横跳刷告警)
                    if (rul != null && st.getLastRulPoint() != null
                            && rul - st.getLastRulPoint() > props.getRulDeferExitMinutes()) {
                        resolveActiveAlert(code, st, "幽灵退出(RUL 推后超阈值)");
                        resetInner(code, st);
                    } else {
                        if (rul != null) {
                            st.setLastRulPoint(rul);
                            if (!st.isPredictiveNotified()) {
                                // 升级预留:首次从"异常但无 RUL"升级为"可预测失效时刻",
                                // 更新已有告警(predictedBreachTime/evidence/level 升 SEVERE)不新发
                                st.setPredictiveNotified(true);
                                escalateToPredictive(sensor, ai, sensorValue, st);
                            } else if (st.getAlertEventId() != null) {
                                // 后续轮次只刷新失效时刻(RUL 随劣化演进每轮变化,保持告警信息最新)
                                refreshBreachTime(ai, st);
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
     * 维护复位:状态回 NORMAL + 活动预测告警置 RESOLVED
     *
     * @param sensorCode 传感器编号
     */
    public void reset(String sensorCode) {
        SensorState st = states.get(sensorCode);
        if (st == null) {
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
    private void firePredictAlert(SensorMetaDTO sensor, AiPredictResultDTO ai, double sensorValue, SensorState st) {
        AlertEvent alert = new AlertEvent();
        alert.setEquipmentId(sensor.getEquipmentId());
        alert.setEquipmentName(sensor.getEquipmentName());
        alert.setSensorId(sensor.getId());
        alert.setSensorCode(sensor.getSensorCode());
        alert.setSensorName(sensor.getSensorName());
        alert.setAlertType("PREDICT");
        // 入态即有 RUL 说明已能预测失效时刻,直接 SEVERE;纯异常无 RUL 先 WARNING,升级时再抬
        boolean predictive = ai != null && ai.getRulPoint() != null;
        alert.setAlertLevel(predictive ? "SEVERE" : "WARNING");
        alert.setAlertStatus("FIRING");
        // 窗口末点是模型输入的最新采样;取两位与 L1 告警口径一致,
        // 避免展示/语音播报输出一长串小数
        alert.setSensorValue(Math.round(sensorValue * 100D) / 100D);
        alert.setTriggerTime(LocalDateTime.now());
        alert.setPredictedBreachTime(predictive
                ? toLocalDateTime(ai.getRulPoint()) : null);
        alert.setEscalationCount(0);
        alert.setEvidence(buildEvidence(ai));
        eventPublisher.publishEvent(new AlertTriggeredEvent(this, alert));
        // 落库监听器同步 insert 后主键回填到实体,此处取回留作后续升级/恢复的更新句柄
        st.setAlertEventId(alert.getId());
        st.setPredictiveNotified(predictive);
        log.info("[PREDICT] 劣化入态告警: sensorCode={}, level={}, rulPoint={}, anomalyScore={}",
                sensor.getSensorCode(), alert.getAlertLevel(),
                ai == null ? null : ai.getRulPoint(),
                ai == null ? null : ai.getAnomalyScore());
    }

    /**
     * 无 RUL 告警升级为可预测告警:更新原告警的失效时刻/证据/等级,不新发(防刷屏)
     */
    private void escalateToPredictive(SensorMetaDTO sensor, AiPredictResultDTO ai, double sensorValue, SensorState st) {
        if (st.getAlertEventId() == null) {
            // 无活动告警句柄(如落库失败):补发一条,不让升级信息丢失
            firePredictAlert(sensor, ai, sensorValue, st);
            return;
        }
        AlertEvent upd = new AlertEvent();
        upd.setId(st.getAlertEventId());
        upd.setAlertLevel("SEVERE");
        upd.setPredictedBreachTime(toLocalDateTime(ai.getRulPoint()));
        upd.setEvidence(buildEvidence(ai));
        // 句柄 id 来自 predict_alert 落库回填,更新须转 PredictAlert 同表命中(D1)
        predictAlertMapper.updateById(PredictAlert.from(upd));
        log.info("[PREDICT] 告警升级为可预测(SEVERE): sensorCode={}, alertEventId={}, rulPoint={}",
                sensor.getSensorCode(), st.getAlertEventId(), ai.getRulPoint());
    }

    /**
     * 刷新活动告警的失效时刻(RUL 随劣化演进每轮变化)
     */
    private void refreshBreachTime(AiPredictResultDTO ai, SensorState st) {
        AlertEvent upd = new AlertEvent();
        upd.setId(st.getAlertEventId());
        upd.setPredictedBreachTime(toLocalDateTime(ai.getRulPoint()));
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
     * 状态内部复位:回 NORMAL + 清告警句柄/退出比较基准/连续异常计数
     */
    private void resetInner(String sensorCode, SensorState st) {
        st.to("NORMAL");
        st.setAlertEventId(null);
        st.setLastRulPoint(null);
        st.setPredictiveNotified(false);
        st.setAnomalyRounds(0);
    }

    /**
     * 证据 JSON:layer/anomalyScore/healthScore/rulPoint/rulEarliest/rulLatest/modelVersion
     * (模型推理产物,排查与前端展示用)
     */
    private String buildEvidence(AiPredictResultDTO ai) {
        try {
            Map<String, Object> ev = new HashMap<>();
            ev.put("layer", "PREDICT");
            ev.put("anomalyScore", ai == null ? null : ai.getAnomalyScore());
            ev.put("healthScore", ai == null ? null : ai.getHealthScore());
            ev.put("rulPoint", ai == null ? null : ai.getRulPoint());
            ev.put("rulEarliest", ai == null ? null : ai.getRulEarliest());
            ev.put("rulLatest", ai == null ? null : ai.getRulLatest());
            ev.put("modelVersion", ai == null ? null : ai.getModelVersion());
            return objectMapper.writeValueAsString(ev);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * RUL 点估计(分钟) → 预计失效时刻(now + rulPoint 分钟)
     */
    private LocalDateTime toLocalDateTime(Long rulPoint) {
        return LocalDateTime.now().plusMinutes(rulPoint);
    }
}
