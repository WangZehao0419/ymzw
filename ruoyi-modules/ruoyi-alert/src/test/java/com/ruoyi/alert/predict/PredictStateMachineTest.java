package com.ruoyi.alert.predict;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * 劣化状态机单元测试(迁移全路径覆盖,mock 依赖不依赖 Spring)
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
    @Mock
    private BaselineRegistry baselineRegistry;

    private PredictStateMachine machine;
    private final PredictProperties props = new PredictProperties();

    @BeforeEach
    void setUp() {
        machine = new PredictStateMachine(props, baselineRegistry,
                eventPublisher, predictAlertMapper, new ObjectMapper());
        // 模拟真实同步事件链路:publishEvent → 落库监听器 insert → 自增主键回填实体
        doAnswer(inv -> {
            inv.getArgument(0, AlertTriggeredEvent.class).getAlertEvent().setId(100L);
            return null;
        }).when(eventPublisher).publishEvent(any(AlertTriggeredEvent.class));
    }

    @Test
    @DisplayName("NORMAL→DEGRADING(L2 触发):发一条 PREDICT/WARNING 告警")
    void normalToDegradingByL2() {
        String status = machine.advance(sensor(), mad(false), cusum(true), null, 50D);

        assertEquals("DEGRADING", status);
        ArgumentCaptor<AlertTriggeredEvent> captor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AlertEvent alert = captor.getValue().getAlertEvent();
        assertEquals("PREDICT", alert.getAlertType());
        assertEquals("WARNING", alert.getAlertLevel(), "纯 L2 突变入态应为 WARNING");
        assertEquals("FIRING", alert.getAlertStatus());
        assertEquals("TEMP-001", alert.getSensorCode());
    }

    @Test
    @DisplayName("NORMAL→DEGRADING(L3 直入):t1 已知直接 SEVERE 并带预测越界时刻")
    void normalToDegradingByTrend() {
        String status = machine.advance(sensor(), mad(false), cusum(false), trend(400), 62D);

        assertEquals("DEGRADING", status);
        ArgumentCaptor<AlertTriggeredEvent> captor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AlertEvent alert = captor.getValue().getAlertEvent();
        assertEquals("SEVERE", alert.getAlertLevel(), "入态即知 t1 应直接 SEVERE");
        assertEquals(400, alert.getPredictedBreachTime() == null ? 0 : 400,
                "predictedBreachTime 应落值");
        assertTrue(alert.getEvidence().contains("\"t1Points\":400"),
                "evidence 应含 t1 点数: " + alert.getEvidence());
        assertEquals(100L, alert.getId());
    }

    @Test
    @DisplayName("DEGRADING 升级:L2 告警升级为可预测(SEVERE),更新原告警不新发")
    void escalateUpdatesExistingAlert() {
        // 第一轮:仅 L2 突变入态(WARNING)
        machine.advance(sensor(), mad(false), cusum(true), null, 50D);
        // 第二轮:趋势成形,t1=401
        machine.advance(sensor(), mad(false), cusum(true), trend(401), 62D);

        // 只发过一条新告警(升级走 updateById)
        verify(eventPublisher, times(1)).publishEvent(any(AlertTriggeredEvent.class));
        ArgumentCaptor<PredictAlert> upd = ArgumentCaptor.forClass(PredictAlert.class);
        verify(predictAlertMapper, atLeastOnce()).updateById(upd.capture());
        PredictAlert last = upd.getValue();
        assertEquals(100L, last.getId(), "应更新原告警(同一条)");
        assertEquals("SEVERE", last.getAlertLevel());
        assertEquals("DEGRADING", machine.status("TEMP-001"));
    }

    @Test
    @DisplayName("DEGRADING→BREACHED:RULE 告警命中,PREDICT 告警置 RESOLVED")
    void degradingToBreachedByRuleAlert() {
        machine.advance(sensor(), mad(false), cusum(true), null, 50D);

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
        machine.advance(sensor(), mad(false), cusum(true), null, 50D);
        AlertEvent rule = new AlertEvent();
        rule.setAlertType("RULE");
        rule.setSensorCode("TEMP-001");
        machine.onRuleAlert(new AlertTriggeredEvent(this, rule));

        String status = machine.advance(sensor(), mad(false), cusum(false), null, 75D);

        assertEquals("BREACHED", status, "BREACHED 期间不应自动迁移");
    }

    @Test
    @DisplayName("幽灵退出:t1 推后超阈值回 NORMAL,告警置 RESOLVED,基线重置")
    void ghostExitByDeferredT1() {
        // 入态时 t1=100;劣化放缓后 t1=701(推后 601 > defer-exit 600)
        machine.advance(sensor(), mad(false), cusum(false), trend(100), 55D);

        String status = machine.advance(sensor(), mad(false), cusum(false), trend(701), 55D);

        assertEquals("NORMAL", status);
        ArgumentCaptor<PredictAlert> upd = ArgumentCaptor.forClass(PredictAlert.class);
        verify(predictAlertMapper).updateById(upd.capture());
        assertEquals("RESOLVED", upd.getValue().getAlertStatus(), "幽灵告警应解除");
        verify(baselineRegistry).reset("TEMP-001");
    }

    @Test
    @DisplayName("维护复位:任意状态回 NORMAL,活动告警解除,基线重置")
    void maintenanceResetToNormal() {
        machine.advance(sensor(), mad(false), cusum(true), null, 50D);

        machine.reset("TEMP-001");

        assertEquals("NORMAL", machine.status("TEMP-001"));
        ArgumentCaptor<PredictAlert> upd = ArgumentCaptor.forClass(PredictAlert.class);
        verify(predictAlertMapper).updateById(upd.capture());
        assertEquals("RESOLVED", upd.getValue().getAlertStatus());
        verify(baselineRegistry).reset("TEMP-001");
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

    private SensorMetaDTO sensor() {
        SensorMetaDTO s = new SensorMetaDTO();
        s.setId(1);
        s.setSensorCode("TEMP-001");
        s.setSensorName("1号温度传感器");
        s.setEquipmentId(10);
        s.setEquipmentName("1号设备");
        return s;
    }

    private MadDetector.Result mad(boolean hit) {
        return new MadDetector.Result(hit, 1.0);
    }

    private CusumDetector.Result cusum(boolean drift) {
        return new CusumDetector.Result(drift, drift ? 1_700_000_000_000L : 0L, drift ? 0.5D : 0D);
    }

    private TrendExtrapolator.Result trend(int t1Points) {
        return new TrendExtrapolator.Result(50D, 0.02D, 0.95D, 0.1D,
                t1Points, 1_700_000_300_000L, 62D, List.of(new double[]{1_700_000_300_000L, 70D, 72D, 74D}));
    }
}
