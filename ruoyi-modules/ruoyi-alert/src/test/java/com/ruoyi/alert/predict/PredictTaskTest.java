package com.ruoyi.alert.predict;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ai.api.RemoteAiService;
import com.ruoyi.ai.api.domain.AiPredictRequestDTO;
import com.ruoyi.ai.api.domain.AiPredictResultDTO;
import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.entity.PredictResult;
import com.ruoyi.alert.event.AlertTriggeredEvent;
import com.ruoyi.alert.mapper.AlertRuleMapper;
import com.ruoyi.alert.mapper.PredictAlertMapper;
import com.ruoyi.alert.mapper.PredictResultMapper;
import com.ruoyi.alert.service.PredictResultService;
import com.ruoyi.alert.service.RuleService;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预测主任务单元测试(mock Feign 与落库,不起 Spring)
 * <p>
 * lambdaQuery 链用真实 LambdaQueryChainWrapper 包 mock 的 Mapper(与
 * AlertDetectionServiceTest 同技巧);状态机用真实实例,端到端验证
 * "推理结果 → 状态迁移 → 告警 evidence → predict_result 快照"整条映射。
 * T2 起历史窗口由 ruoyi-ai 拉取并在响应中回显:测试 mock 推理响应携带
 * window/windowTs,验证 sensorValue 取回显末值、空窗跳过本轮。
 * </p>
 *
 * @author smartartisan
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PredictTaskTest {

    @Mock
    private RemoteEquipmentService remoteEquipmentService;
    @Mock
    private RemoteAiService remoteAiService;
    @Mock
    private PredictResultService predictResultService;
    @Mock
    private PredictResultMapper predictResultMapper;
    @Mock
    private RuleService ruleService;
    @Mock
    private AlertRuleMapper ruleMapper;
    @Mock
    private PredictAlertMapper predictAlertMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final PredictProperties props = new PredictProperties();
    private PredictTask task;

    @BeforeAll
    static void initTableInfo() {
        // eq()/orderByAsc() 会急切解析 lambda 列名,先注册实体元数据(已注册则直接返回缓存,幂等)
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PredictResult.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlertRule.class);
    }

    @BeforeEach
    void setUp() {
        // 2 轮入态 + 30 分钟 RUL(窗口在推理响应里 mock,无需本地构造取数)
        props.setEnabled(true);
        props.setAnomalyRounds(2);
        props.getModel().setHorizon(48);

        // 真实状态机:端到端验证迁移与告警链路(publish 后主键回填模拟落库监听器)
        PredictStateMachine stateMachine = new PredictStateMachine(
                props, eventPublisher, predictAlertMapper, new ObjectMapper());
        doAnswer(inv -> {
            inv.getArgument(0, AlertTriggeredEvent.class).getAlertEvent().setId(100L);
            return null;
        }).when(eventPublisher).publishEvent(any(AlertTriggeredEvent.class));

        task = new PredictTask(props, remoteEquipmentService, remoteAiService,
                predictResultService, ruleService, stateMachine);

        // 传感器全量列表:单传感器(历史窗口由 ruoyi-ai 拉取,本侧不再 mock getSensorHistory)
        when(remoteEquipmentService.listAllSensors(SecurityConstants.INNER))
                .thenReturn(R.ok(List.of(sensor())));
        // 规则查询链:启用规则带上限 85(与下限同时配置时预期取上限)
        LambdaQueryChainWrapper<AlertRule> ruleChain = new LambdaQueryChainWrapper<>(ruleMapper);
        when(ruleService.lambdaQuery()).thenReturn(ruleChain);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rule()));
        // 快照查询链:库中无既有记录(走 insert 路径)
        LambdaQueryChainWrapper<PredictResult> resultChain = new LambdaQueryChainWrapper<>(predictResultMapper);
        when(predictResultService.lambdaQuery()).thenReturn(resultChain);
        when(predictResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    }

    @Test
    @DisplayName("端到端映射:连续 2 轮异常入态 DEGRADING,推理字段/阈值/步长/evidence/快照全链路正确")
    void anomalyMapsToSnapshotAndAlert() throws Exception {
        when(remoteAiService.predict(any(AiPredictRequestDTO.class), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(aiResult()));

        task.run();  // 第 1 轮:异常计数 1,未入态
        task.run();  // 第 2 轮:入态 DEGRADING

        // 推理请求:窗口已上收 ruoyi-ai 拉取,本侧只传编码/阈值/步长
        ArgumentCaptor<AiPredictRequestDTO> reqCaptor = ArgumentCaptor.forClass(AiPredictRequestDTO.class);
        verify(remoteAiService, times(2)).predict(reqCaptor.capture(), eq(SecurityConstants.INNER));
        AiPredictRequestDTO req = reqCaptor.getValue();
        assertEquals("TEMP-001", req.getSensorCode());
        assertEquals(85.0, req.getThreshold(), "上下限同时配置时应取上限");
        assertEquals(48, req.getHorizon());

        // predict_result 快照:模型推理字段 + RUL 换算
        ArgumentCaptor<PredictResult> snapCaptor = ArgumentCaptor.forClass(PredictResult.class);
        verify(predictResultService, times(2)).upsert(snapCaptor.capture());
        PredictResult snapshot = snapCaptor.getValue();
        assertEquals("TEMP-001", snapshot.getSensorCode());
        assertEquals(10, snapshot.getEquipmentId());
        assertEquals("DEGRADING", snapshot.getStatus());
        assertEquals(42.5, snapshot.getHealthScore());
        assertEquals(0.87, snapshot.getAnomalyScore());
        assertEquals(300L, snapshot.getRulEarliest());
        assertEquals(500L, snapshot.getRulLatest());
        assertEquals("pdm-v1.2", snapshot.getModelVersion());
        // predictedBreachTime = 落库时刻 + rulPoint(30 分钟),允许毫秒级误差
        LocalDateTime expected = LocalDateTime.now().plusMinutes(30);
        assertNotNull(snapshot.getPredictedBreachTime());
        assertTrue(Math.abs(ChronoUnit.SECONDS.between(expected, snapshot.getPredictedBreachTime())) < 5,
                "predictedBreachTime 应为 now+30 分钟: 实际=" + snapshot.getPredictedBreachTime());
        assertNull(snapshot.getSlope(), "统计链路字段不应再写入");

        // 告警:evidence 含推理产物,等级 SEVERE(入态即有 RUL)
        ArgumentCaptor<AlertTriggeredEvent> alertCaptor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(eventPublisher, times(1)).publishEvent(alertCaptor.capture());
        AlertEvent alert = alertCaptor.getValue().getAlertEvent();
        assertEquals("PREDICT", alert.getAlertType());
        assertEquals("SEVERE", alert.getAlertLevel());
        assertEquals(54.5, alert.getSensorValue(), "展示值应取响应回显窗口末点(两位小数)");
        Map<?, ?> ev = new ObjectMapper().readValue(alert.getEvidence(), Map.class);
        assertEquals(0.87, ev.get("anomalyScore"));
        assertEquals(42.5, ev.get("healthScore"));
        assertEquals(30, ev.get("rulPoint"));
        assertEquals(300, ev.get("rulEarliest"));
        assertEquals(500, ev.get("rulLatest"));
        assertEquals("pdm-v1.2", ev.get("modelVersion"));
    }

    @Test
    @DisplayName("推理失败不落库不迁移:R.FAIL 时本轮无任何结论产出(无降级容错)")
    void inferenceFailureProducesNothing() {
        when(remoteAiService.predict(any(AiPredictRequestDTO.class), eq(SecurityConstants.INNER)))
                .thenReturn(R.fail("pdm-server 不可达"));

        task.run();
        task.run();

        verify(predictResultService, never()).upsert(any(PredictResult.class));
        verify(eventPublisher, never()).publishEvent(any(AlertTriggeredEvent.class));
    }

    @Test
    @DisplayName("推理返回空数据视为失败:R.ok(null) 同样不落库")
    void inferenceEmptyDataProducesNothing() {
        when(remoteAiService.predict(any(AiPredictRequestDTO.class), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(null));

        task.run();

        verify(predictResultService, never()).upsert(any(PredictResult.class));
    }

    @Test
    @DisplayName("响应未带回显窗口:即使判定异常也跳过本轮,不推进状态机不落库")
    void emptyWindowInResponseSkipsRound() {
        AiPredictResultDTO noWindow = aiResult();
        noWindow.setWindow(null);
        when(remoteAiService.predict(any(AiPredictRequestDTO.class), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(noWindow));

        task.run();
        task.run();

        verify(predictResultService, never()).upsert(any(PredictResult.class));
        verify(eventPublisher, never()).publishEvent(any(AlertTriggeredEvent.class));
    }

    @Test
    @DisplayName("RUL 分钟换算:null 直通,正负分钟与 0 边界正确")
    void rulMinuteConversion() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 12, 0, 0);
        assertNull(PredictTask.toPredictedBreachTime(null, now), "无 RUL 应直通 null");
        assertEquals(now, PredictTask.toPredictedBreachTime(0L, now), "0 分钟应等于当前时刻");
        assertEquals(now.plusMinutes(48), PredictTask.toPredictedBreachTime(48L, now));
        assertEquals(now.minusMinutes(5), PredictTask.toPredictedBreachTime(-5L, now),
                "负 RUL(失效时刻已过)应原样换算");
    }

    @Test
    @DisplayName("阈值选取:优先上限,无上限取下限,无规则/无阈值不传")
    void thresholdSelection() {
        AlertRule both = new AlertRule();
        both.setUpperLimit(85.0);
        both.setLowerLimit(20.0);
        assertEquals(85.0, PredictTask.thresholdOf(both), "上下限同时配置应取上限");

        AlertRule lowerOnly = new AlertRule();
        lowerOnly.setLowerLimit(20.0);
        assertEquals(20.0, PredictTask.thresholdOf(lowerOnly));

        AlertRule noLimit = new AlertRule();
        assertNull(PredictTask.thresholdOf(noLimit), "规则无阈值应不传(模型按默认阈值判定)");
        assertNull(PredictTask.thresholdOf(null), "无规则应不传");
    }

    @Test
    @DisplayName("推理请求构造:与任务同入口的公共方法字段全映射(窗口已上收,仅编码/阈值/步长)")
    void buildRequestMapsAllFields() {
        AiPredictRequestDTO request = PredictTask.buildRequest(sensor(), rule(), 48);

        assertEquals("TEMP-001", request.getSensorCode());
        assertEquals(85.0, request.getThreshold());
        assertEquals(48, request.getHorizon());
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

    private AlertRule rule() {
        AlertRule r = new AlertRule();
        r.setId(1L);
        r.setSensorId(1);
        r.setUpperLimit(85.0);
        r.setLowerLimit(20.0);
        r.setEnabled(1);
        return r;
    }

    /**
     * 构造异常推理结果:带 RUL 点估计 30 分钟、完整溯源字段与 10 点回显窗口
     * (值 50.0 起步每点 +0.5,间隔 500ms,末值 54.5 即告警展示值)
     */
    private AiPredictResultDTO aiResult() {
        AiPredictResultDTO ai = new AiPredictResultDTO();
        ai.setIsAnomaly(true);
        ai.setAnomalyScore(0.87);
        ai.setHealthScore(42.5);
        ai.setRulPoint(30L);
        ai.setRulEarliest(300L);
        ai.setRulLatest(500L);
        ai.setModelVersion("pdm-v1.2");
        ai.setQ10(List.of(52.0, 53.0));
        ai.setQ50(List.of(53.0, 54.0));
        ai.setQ90(List.of(54.0, 55.0));
        List<Double> window = new java.util.ArrayList<>(10);
        List<Long> windowTs = new java.util.ArrayList<>(10);
        long ts = 1_700_000_000_000L;
        for (int i = 0; i < 10; i++) {
            window.add(50.0 + i * 0.5);
            windowTs.add(ts + i * 500L);
        }
        ai.setWindow(window);
        ai.setWindowTs(windowTs);
        return ai;
    }
}
