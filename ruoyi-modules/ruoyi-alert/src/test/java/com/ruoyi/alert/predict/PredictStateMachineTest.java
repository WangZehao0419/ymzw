package com.ruoyi.alert.predict;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ai.api.domain.AiPredictResultDTO;
import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.entity.PredictAlert;
import com.ruoyi.alert.event.AlertTriggeredEvent;
import com.ruoyi.alert.mapper.PredictAlertMapper;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * 劣化状态机单元测试(isAnomaly 持续性驱动全路径覆盖,mock 依赖不依赖 Spring)
 *
 * @author smartartisan
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PredictStateMachineTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PredictAlertMapper predictAlertMapper;

    private PredictStateMachine machine;
    private final PredictProperties props = new PredictProperties();

    @BeforeEach
    void setUp() {
        machine = new PredictStateMachine(props, eventPublisher, predictAlertMapper, new ObjectMapper());
        // 模拟真实同步事件链路:publishEvent → 落库监听器 insert → 自增主键回填实体
        doAnswer(inv -> {
            inv.getArgument(0, AlertTriggeredEvent.class).getAlertEvent().setId(100L);
            return null;
        }).when(eventPublisher).publishEvent(any(AlertTriggeredEvent.class));
    }

    @Test
    @DisplayName("持续性判定:单轮异常不入态,连续 anomalyRounds 轮才入态 DEGRADING")
    void singleAnomalyRoundNotEnough() {
        // 第 1 轮异常:计数 1 < 2,不迁移
        String status = machine.advance(sensor(), ai(true, null), 50D);
        assertEquals("NORMAL", status, "单轮异常不应入态");

        // 第 2 轮异常:计数 2 达阈值,入态并发 WARNING 告警(无 RUL)
        status = machine.advance(sensor(), ai(true, null), 51D);
        assertEquals("DEGRADING", status);
        ArgumentCaptor<AlertTriggeredEvent> captor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        AlertEvent alert = captor.getValue().getAlertEvent();
        assertEquals("PREDICT", alert.getAlertType());
        assertEquals("WARNING", alert.getAlertLevel(), "无 RUL 入态应为 WARNING");
        assertNull(alert.getPredictedBreachTime(), "无 RUL 不应带失效时刻");
    }

    @Test
    @DisplayName("异常中断清零:异常一轮后恢复一轮,再异常一轮仍不满足持续性")
    void anomalyCounterResetsOnGap() {
        machine.advance(sensor(), ai(true, null), 50D);
        // 中断轮:isAnomaly=false,连续计数清零
        machine.advance(sensor(), ai(false, null), 50D);
        // 再异常:计数重新从 1 起算,仍不迁移
        String status = machine.advance(sensor(), ai(true, null), 50D);
        assertEquals("NORMAL", status, "中断后计数应清零重计");
        verify(eventPublisher, never()).publishEvent(any(AlertTriggeredEvent.class));
    }

    @Test
    @DisplayName("NORMAL→DEGRADING(带 RUL 直入):SEVERE 并带失效时刻,evidence 含推理产物")
    void normalToDegradingWithRul() throws Exception {
        AiPredictResultDTO ai = ai(true, 400L);
        ai.setAnomalyScore(0.87);
        ai.setHealthScore(55.5);
        ai.setRulEarliest(300L);
        ai.setRulLatest(500L);
        ai.setModelVersion("pdm-v1.2");

        machine.advance(sensor(), ai, 62D);
        String status = machine.advance(sensor(), ai, 62D);  // 第二轮满足持续性入态

        assertEquals("DEGRADING", status);
        ArgumentCaptor<AlertTriggeredEvent> captor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        AlertEvent alert = captor.getValue().getAlertEvent();
        assertEquals("SEVERE", alert.getAlertLevel(), "入态即有 RUL 应直接 SEVERE");
        assertNotNull(alert.getPredictedBreachTime(), "predictedBreachTime 应落值");
        assertEquals(62.0, alert.getSensorValue(), "展示值应取传入的窗口末点值");
        // evidence 为模型推理产物(anomalyScore/healthScore/rulPoint/rulEarliest/rulLatest/modelVersion)
        Map<?, ?> ev = new ObjectMapper().readValue(alert.getEvidence(), Map.class);
        assertEquals(0.87, ev.get("anomalyScore"));
        assertEquals(55.5, ev.get("healthScore"));
        assertEquals(400, ev.get("rulPoint"));
        assertEquals(300, ev.get("rulEarliest"));
        assertEquals(500, ev.get("rulLatest"));
        assertEquals("pdm-v1.2", ev.get("modelVersion"));
        assertEquals("PREDICT", ev.get("layer"));
    }

    @Test
    @DisplayName("DEGRADING 升级:无 RUL 告警在 RUL 产出后升级 SEVERE,更新原告警不新发")
    void escalateUpdatesExistingAlert() {
        AiPredictResultDTO noRul = ai(true, null);
        AiPredictResultDTO withRul = ai(true, 401L);
        // 两轮异常入态(WARNING,无 RUL)
        machine.advance(sensor(), noRul, 50D);
        machine.advance(sensor(), noRul, 50D);
        // 第三轮:RUL 产出,升级为可预测
        machine.advance(sensor(), withRul, 62D);

        // 只发过一条新告警(升级走 updateById)
        verify(eventPublisher, times(1)).publishEvent(any(AlertTriggeredEvent.class));
        ArgumentCaptor<PredictAlert> upd = ArgumentCaptor.forClass(PredictAlert.class);
        verify(predictAlertMapper, atLeastOnce()).updateById(upd.capture());
        PredictAlert last = upd.getValue();
        assertEquals(100L, last.getId(), "应更新原告警(同一条)");
        assertEquals("SEVERE", last.getAlertLevel());
        assertNotNull(last.getPredictedBreachTime(), "升级应补失效时刻");
        assertEquals("DEGRADING", machine.status("TEMP-001"));
    }

    @Test
    @DisplayName("DEGRADING 稳态:RUL 小幅波动不触发幽灵退出,只刷新失效时刻")
    void smallRulFluctuationStays() {
        AiPredictResultDTO r1 = ai(true, 400L);
        AiPredictResultDTO r2 = ai(true, 430L);  // 推后 30 < 阈值 60
        machine.advance(sensor(), r1, 50D);
        machine.advance(sensor(), r1, 50D);
        machine.advance(sensor(), r2, 55D);

        assertEquals("DEGRADING", machine.status("TEMP-001"), "小幅推后不应幽灵退出");
        // 升级后后续轮次只刷新失效时刻(updateById),不新发
        verify(eventPublisher, times(1)).publishEvent(any(AlertTriggeredEvent.class));
        verify(predictAlertMapper, atLeastOnce()).updateById(any(PredictAlert.class));
    }

    @Test
    @DisplayName("幽灵退出:RUL 推后超阈值回 NORMAL,告警置 RESOLVED")
    void ghostExitByDeferredRul() {
        AiPredictResultDTO r1 = ai(true, 100L);
        AiPredictResultDTO r2 = ai(true, 161L);  // 推后 61 > 阈值 60
        machine.advance(sensor(), r1, 55D);
        machine.advance(sensor(), r1, 55D);
        String status = machine.advance(sensor(), r2, 55D);

        assertEquals("NORMAL", status);
        ArgumentCaptor<PredictAlert> upd = ArgumentCaptor.forClass(PredictAlert.class);
        verify(predictAlertMapper).updateById(upd.capture());
        assertEquals("RESOLVED", upd.getValue().getAlertStatus(), "幽灵告警应解除");
    }

    @Test
    @DisplayName("DEGRADING→BREACHED:RULE 告警命中,PREDICT 告警置 RESOLVED")
    void degradingToBreachedByRuleAlert() {
        AiPredictResultDTO ai = ai(true, null);
        machine.advance(sensor(), ai, 50D);
        machine.advance(sensor(), ai, 50D);

        AlertEvent rule = new AlertEvent();
        rule.setAlertType("RULE");
        rule.setSensorCode("TEMP-001");
        machine.onRuleAlert(new AlertTriggeredEvent(this, rule));

        assertEquals("BREACHED", machine.status("TEMP-001"));
        ArgumentCaptor<PredictAlert> upd = ArgumentCaptor.forClass(PredictAlert.class);
        verify(predictAlertMapper).updateById(upd.capture());
        assertEquals("RESOLVED", upd.getValue().getAlertStatus(), "预测兑现后 PREDICT 告警应解除");
        assertEquals(100L, upd.getValue().getId());
    }

    @Test
    @DisplayName("BREACHED 稳态:后续 advance 不迁移,等维护复位")
    void breachedStaysUntilReset() {
        AiPredictResultDTO ai = ai(true, null);
        machine.advance(sensor(), ai, 50D);
        machine.advance(sensor(), ai, 50D);
        AlertEvent rule = new AlertEvent();
        rule.setAlertType("RULE");
        rule.setSensorCode("TEMP-001");
        machine.onRuleAlert(new AlertTriggeredEvent(this, rule));

        String status = machine.advance(sensor(), ai(true, null), 75D);

        assertEquals("BREACHED", status, "BREACHED 期间不应自动迁移");
    }

    @Test
    @DisplayName("维护复位:任意状态回 NORMAL,活动告警解除,连续计数清零")
    void maintenanceResetToNormal() {
        AiPredictResultDTO ai = ai(true, null);
        machine.advance(sensor(), ai, 50D);
        machine.advance(sensor(), ai, 50D);

        machine.reset("TEMP-001");

        assertEquals("NORMAL", machine.status("TEMP-001"));
        ArgumentCaptor<PredictAlert> upd = ArgumentCaptor.forClass(PredictAlert.class);
        verify(predictAlertMapper).updateById(upd.capture());
        assertEquals("RESOLVED", upd.getValue().getAlertStatus());
        // 复位后单轮异常不入态(计数已清零,须重新攒满 anomalyRounds)
        assertEquals("NORMAL", machine.advance(sensor(), ai, 50D));
    }

    @Test
    @DisplayName("未见过的传感器:RULE 告警不误伤,status 默认 NORMAL")
    void unknownSensorIgnored() {
        AlertEvent rule = new AlertEvent();
        rule.setAlertType("RULE");
        rule.setSensorCode("UNKNOWN-001");
        machine.onRuleAlert(new AlertTriggeredEvent(this, rule));

        assertEquals("NORMAL", machine.status("UNKNOWN-001"));
        verify(predictAlertMapper, never()).updateById(any(PredictAlert.class));
    }

    @Test
    @DisplayName("推理结果缺失(null)按非异常处理,不累计持续性")
    void nullAiTreatedAsNotAnomaly() {
        machine.advance(sensor(), ai(true, null), 50D);
        // 推理结果缺失:连续计数清零
        machine.advance(sensor(), null, 50D);
        String status = machine.advance(sensor(), ai(true, null), 50D);
        assertEquals("NORMAL", status, "null 结果应中断连续计数");
    }

    private SensorMetaDTO sensor() {
        SensorMetaDTO s = new SensorMetaDTO();
        s.setId(1);
        s.setSensorCode("TEMP-001");
        s.setSensorName("1号温度传感器");
        s.setEquipmentId(10);
        s.setEquipmentName("1号设备");
        return s;
    }

    /**
     * 构造推理结果:isAnomaly + 可选 RUL 点估计(分钟)
     */
    private AiPredictResultDTO ai(boolean anomaly, Long rulPoint) {
        AiPredictResultDTO ai = new AiPredictResultDTO();
        ai.setIsAnomaly(anomaly);
        ai.setRulPoint(rulPoint);
        return ai;
    }
}
