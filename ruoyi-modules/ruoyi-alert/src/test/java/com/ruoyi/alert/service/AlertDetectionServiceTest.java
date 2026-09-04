package com.ruoyi.alert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.event.AlertEscalatedEvent;
import com.ruoyi.alert.event.AlertTriggeredEvent;
import com.ruoyi.alert.event.SensorDataReceivedEvent;
import com.ruoyi.alert.mapper.AlertEventMapper;
import com.ruoyi.alert.mapper.AlertRuleMapper;
import com.ruoyi.equipment.api.RemoteEquipmentService;
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

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多级阈值告警升级单元测试(纯 Mockito 不起 Spring 上下文)
 * <p>
 * lambdaQuery 链用真实 LambdaQueryChainWrapper 包 mock 的 AlertRuleMapper,
 * list() 底层走 baseMapper.selectList,直接 stub 返回规则列表;
 * 活动告警查询直接 stub alertEventMapper.selectList。
 * in() 等条件会急切解析 lambda 列名,需 @BeforeAll 注册实体 TableInfo(幂等)。
 * </p>
 *
 * @author smartartisan
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertDetectionServiceTest {

    @Mock
    private RuleService ruleService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private RemoteEquipmentService remoteEquipmentService;
    @Mock
    private AlertEventMapper alertEventMapper;
    @Mock
    private AlertRuleMapper ruleMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AlertDetectionService service;

    @BeforeAll
    static void initTableInfo() {
        // in()/orderByDesc 会急切解析 lambda 列名,先注册实体元数据(已注册则直接返回缓存,幂等)
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlertEvent.class);
    }

    @BeforeEach
    void setUp() {
        // 每个用例新建 service,隔离各用例间的内存防抖计数
        service = new AlertDetectionService(ruleService, eventPublisher, objectMapper,
                remoteEquipmentService, alertEventMapper);
    }

    /**
     * 规则查询链 stub:chain.list() → ruleMapper.selectList 返回给定规则
     */
    private void stubRules(AlertRule... rules) {
        LambdaQueryChainWrapper<AlertRule> chain = new LambdaQueryChainWrapper<>(ruleMapper);
        when(ruleService.lambdaQuery()).thenReturn(chain);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rules));
    }

    /**
     * 喂一个数据点(传感器 1 / TEMP-001)
     */
    private void feed(double value) {
        service.detect(new SensorDataReceivedEvent(this, "TEMP-001", 1, value, 10, LocalDateTime.now()));
    }

    /**
     * 喂一个指定采集时间的数据点(传感器 1 / TEMP-001),
     * 用于连环升级场景断言 triggerTime 保持首次触发时间的语义
     */
    private void feedAt(double value, LocalDateTime time) {
        service.detect(new SensorDataReceivedEvent(this, "TEMP-001", 1, value, 10, time));
    }

    private AlertRule rule(long id, Double upper, Double lower, String level, int sustain) {
        AlertRule r = new AlertRule();
        r.setId(id);
        r.setSensorId(1);
        r.setUpperLimit(upper);
        r.setLowerLimit(lower);
        r.setLevel(level);
        r.setSustainPoints(sustain);
        r.setEnabled(1);
        return r;
    }

    private AlertEvent activeAlert(long id, String level, String status, String evidence,
                                   LocalDateTime triggerTime) {
        AlertEvent a = new AlertEvent();
        a.setId(id);
        a.setSensorId(1);
        a.setAlertType("RULE");
        a.setAlertLevel(level);
        a.setAlertStatus(status);
        a.setEvidence(evidence);
        a.setSensorValue(41.0);
        a.setTriggerTime(triggerTime);
        a.setEscalationCount(0);
        return a;
    }

    @Test
    @DisplayName("多规则独立防抖:严重规则先满足直接 SEVERE,后续 WARNING 因更低被去重忽略")
    void severeRuleFiresFirstThenLowerIgnored() {
        stubRules(rule(1L, 40D, null, "WARNING", 5), rule(2L, 70D, null, "SEVERE", 2));
        // 第 2 点规则B防抖满足时无活动告警;此后 SEVERE 活动告警已存在(mock 出已落库状态)
        when(alertEventMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(activeAlert(100L, "SEVERE", "FIRING",
                        "{\"layer\":\"RULE\",\"breach\":\"upper\",\"value\":71.0,\"sustain\":2}",
                        LocalDateTime.now())));

        feed(71);  // A 计 1, B 计 1
        feed(71);  // B 满 2 → 无活动告警 → 发布 SEVERE
        feed(71);  // A 计 3
        feed(71);  // A 计 4(防抖触发后重新累计的 B 也再次满足,但等级相同被忽略)
        feed(71);  // A 满 5 → SEVERE 已存在且 WARNING 更低 → 不再发布任何事件

        ArgumentCaptor<AlertTriggeredEvent> captor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertEquals("SEVERE", captor.getValue().getAlertEvent().getAlertLevel());
        verify(eventPublisher, never()).publishEvent(any(AlertEscalatedEvent.class));
    }

    @Test
    @DisplayName("WARNING→SEVERE 升级:更新原告警,等级/次数/值更新,triggerTime 不变,证据追加升级记录")
    void warningEscalatesToSevere() throws Exception {
        stubRules(rule(1L, 40D, null, "WARNING", 5), rule(2L, 70D, null, "SEVERE", 2));
        LocalDateTime triggerTime = LocalDateTime.of(2026, 8, 31, 10, 0);
        AlertEvent active = activeAlert(100L, "WARNING", "FIRING",
                "{\"layer\":\"RULE\",\"breach\":\"upper\",\"value\":41.0,\"sustain\":5}", triggerTime);
        // 第 5 点规则A满足时无活动告警 → 发布 WARNING;此后 mock 出已落库的 WARNING 活动告警
        when(alertEventMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(active));

        feed(41);
        feed(41);
        feed(41);
        feed(41);
        feed(41);  // A 满 5 → 发布 WARNING
        feed(71);
        feed(71);  // B 满 2 → 升级既有 WARNING

        verify(eventPublisher, times(1)).publishEvent(any(AlertTriggeredEvent.class));
        ArgumentCaptor<AlertEscalatedEvent> captor = ArgumentCaptor.forClass(AlertEscalatedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        AlertEvent escalated = captor.getValue().getAlertEvent();
        assertEquals(100L, escalated.getId(), "应升级原告警而非新建");
        assertEquals("SEVERE", escalated.getAlertLevel());
        assertEquals(1, escalated.getEscalationCount().intValue());
        assertEquals(71.0, escalated.getSensorValue());
        assertEquals(triggerTime, escalated.getTriggerTime(), "首次触发时间应保留不变");
        assertEquals("FIRING", escalated.getAlertStatus());

        Map<?, ?> ev = objectMapper.readValue(escalated.getEvidence(), Map.class);
        assertEquals("upper", ev.get("breach"), "原证据的方向信息应保留");
        List<?> escalations = (List<?>) ev.get("escalations");
        assertEquals(1, escalations.size());
        Map<?, ?> record = (Map<?, ?>) escalations.get(0);
        assertEquals("WARNING", record.get("from"));
        assertEquals("SEVERE", record.get("to"));
        assertEquals(71.0, record.get("value"));
        assertEquals(70.0, record.get("threshold"), "阈值应取命中规则B的上限");
        assertEquals(2, record.get("ruleId"));
        assertTrue(record.get("time") != null, "升级记录应含时间");
    }

    @Test
    @DisplayName("ACKED 告警升级后重置为 FIRING 重新引起关注")
    void ackedResetToFiringOnEscalation() {
        stubRules(rule(1L, 40D, null, "WARNING", 5), rule(2L, 70D, null, "SEVERE", 2));
        AlertEvent active = activeAlert(100L, "WARNING", "ACKED",
                "{\"layer\":\"RULE\",\"breach\":\"upper\",\"value\":41.0,\"sustain\":5}",
                LocalDateTime.now());
        when(alertEventMapper.selectList(any(Wrapper.class))).thenReturn(List.of(active));

        feed(71);  // A 计 1, B 计 1
        feed(71);  // B 满 2 → SEVERE > WARNING → 升级并重置状态

        ArgumentCaptor<AlertEscalatedEvent> captor = ArgumentCaptor.forClass(AlertEscalatedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertEquals("FIRING", captor.getValue().getAlertEvent().getAlertStatus(),
                "ACKED 升级后应重置 FIRING");
        verify(eventPublisher, never()).publishEvent(any(AlertTriggeredEvent.class));
    }

    @Test
    @DisplayName("只升不降:活动告警已是 SEVERE,更低等级 WARNING 防抖满足也不发布任何事件")
    void lowerLevelIgnoredWhenSevereActive() {
        stubRules(rule(1L, 40D, null, "WARNING", 5));
        AlertEvent active = activeAlert(100L, "SEVERE", "FIRING",
                "{\"layer\":\"RULE\",\"breach\":\"upper\",\"value\":75.0,\"sustain\":2}",
                LocalDateTime.now());
        when(alertEventMapper.selectList(any(Wrapper.class))).thenReturn(List.of(active));

        feed(41);
        feed(41);
        feed(41);
        feed(41);
        feed(41);  // A 满 5 → SEVERE 已存在且 WARNING 更低 → 忽略

        verify(eventPublisher, never()).publishEvent(any(AlertTriggeredEvent.class));
        verify(eventPublisher, never()).publishEvent(any(AlertEscalatedEvent.class));
    }

    @Test
    @DisplayName("防抖独立清零:中间值仅清对应规则计数,严重规则重新累计后再触发")
    void debounceIndependentClear() {
        stubRules(rule(1L, 40D, null, "WARNING", 5), rule(2L, 70D, null, "SEVERE", 2));
        when(alertEventMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        feed(71);  // A=1, B=1
        feed(50);  // B 清零(50 未超 70), A=2(50 仍超 40)
        feed(71);  // B 重新计 1 不足 2 → 无事件
        verify(eventPublisher, never()).publishEvent(any(AlertTriggeredEvent.class));
        verify(eventPublisher, never()).publishEvent(any(AlertEscalatedEvent.class));

        feed(71);  // B=2 → 触发 SEVERE

        ArgumentCaptor<AlertTriggeredEvent> captor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertEquals("SEVERE", captor.getValue().getAlertEvent().getAlertLevel());
    }

    @Test
    @DisplayName("单规则回归:防抖满足恰好发布一次 WARNING 告警,escalationCount=0")
    void singleRuleRegression() throws Exception {
        stubRules(rule(1L, 40D, null, "WARNING", 5));
        when(alertEventMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        feed(41);
        feed(41);
        feed(41);
        feed(41);
        feed(41);  // 满 5 → 新建告警

        ArgumentCaptor<AlertTriggeredEvent> captor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        AlertEvent alert = captor.getValue().getAlertEvent();
        assertEquals("WARNING", alert.getAlertLevel());
        assertEquals("FIRING", alert.getAlertStatus());
        assertEquals("RULE", alert.getAlertType());
        assertEquals(0, alert.getEscalationCount().intValue());
        Map<?, ?> ev = objectMapper.readValue(alert.getEvidence(), Map.class);
        assertEquals("upper", ev.get("breach"));
        assertEquals(5, ev.get("sustain"));
        verify(eventPublisher, never()).publishEvent(any(AlertEscalatedEvent.class));
    }

    @Test
    @DisplayName("方向隔离:upper 活动告警不拦截 lower 规则新建告警")
    void directionIsolation() throws Exception {
        stubRules(rule(1L, 40D, null, "WARNING", 5), rule(3L, null, 20D, "WARNING", 2));
        AlertEvent upperActive = activeAlert(100L, "WARNING", "FIRING",
                "{\"layer\":\"RULE\",\"breach\":\"upper\",\"value\":45.0,\"sustain\":5}",
                LocalDateTime.now());
        when(alertEventMapper.selectList(any(Wrapper.class))).thenReturn(List.of(upperActive));

        feed(5);  // 上限规则不越界(5 未超 40)计数清零;下限规则越界(5 < 20)计 1
        feed(5);  // 下限规则满 2 → 无同向(lower)活动告警 → 新建

        ArgumentCaptor<AlertTriggeredEvent> captor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        AlertEvent created = captor.getValue().getAlertEvent();
        Map<?, ?> ev = objectMapper.readValue(created.getEvidence(), Map.class);
        assertEquals("lower", ev.get("breach"), "应新建下限方向告警");
        assertEquals(1, created.getSensorId().intValue());
        verify(eventPublisher, never()).publishEvent(any(AlertEscalatedEvent.class));
    }

    @Test
    @DisplayName("四级等级序:levelRank 满足 NORMAL(0)<WARNING(1)<IMPORTANT(2)<SEVERE(3)<CRITICAL(4)")
    void fourLevelRankOrder() throws Exception {
        // levelRank 为 private static,反射直读等级序:
        // IMPORTANT 插入中间档=2、CRITICAL 顶部新增=4、SEVERE 由 2 提升至 3
        Method levelRank = AlertDetectionService.class.getDeclaredMethod("levelRank", String.class);
        levelRank.setAccessible(true);
        assertEquals(0, levelRank.invoke(null, "NORMAL"));
        assertEquals(1, levelRank.invoke(null, "WARNING"));
        assertEquals(2, levelRank.invoke(null, "IMPORTANT"));
        assertEquals(3, levelRank.invoke(null, "SEVERE"));
        assertEquals(4, levelRank.invoke(null, "CRITICAL"));
        assertEquals(0, levelRank.invoke(null, (Object) null), "null 应按 0 处理");
        assertEquals(0, levelRank.invoke(null, "UNKNOWN"), "未知级别应按 0 处理");
    }

    @Test
    @DisplayName("四级等级序(行为):高等级活动告警在场时,低等级命中被去重忽略")
    void higherActiveLevelBlocksLowerHit() {
        // IMPORTANT(2) 活动时 WARNING(1) 命中被忽略
        assertLowerHitIgnored("IMPORTANT", "WARNING");
        // SEVERE(3) 活动时 IMPORTANT(2) 命中被忽略
        assertLowerHitIgnored("SEVERE", "IMPORTANT");
        // CRITICAL(4) 活动时 SEVERE(3) 命中被忽略
        assertLowerHitIgnored("CRITICAL", "SEVERE");
    }

    /**
     * 构造"活动告警等级=activeLevel,命中规则等级=hitLevel"的单点命中场景,
     * 断言不新建、不升级(低等级命中在高等级活动告警前必须被去重)
     */
    private void assertLowerHitIgnored(String activeLevel, String hitLevel) {
        // 换规则重建场景:命中规则 sustain=1 单点即满足防抖
        stubRules(rule(9L, 40D, null, hitLevel, 1));
        when(alertEventMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                activeAlert(900L, activeLevel, "FIRING",
                        "{\"layer\":\"RULE\",\"breach\":\"upper\",\"value\":75.0,\"sustain\":1}",
                        LocalDateTime.now())));

        feed(41);  // 命中规则防抖满足,但活动告警等级更高 → 忽略

        verify(eventPublisher, never()).publishEvent(any(AlertTriggeredEvent.class));
        verify(eventPublisher, never()).publishEvent(any(AlertEscalatedEvent.class));
    }

    @Test
    @DisplayName("连环四级升级:同一告警 WARNING→IMPORTANT→SEVERE→CRITICAL,escalationCount=3,triggerTime 不变")
    void fourLevelChainEscalation() throws Exception {
        // TEMP-001 四规则:40/WARNING/5点、55/IMPORTANT/3点、70/SEVERE/2点、85/CRITICAL/1点
        stubRules(
                rule(1L, 40D, null, "WARNING", 5),
                rule(2L, 55D, null, "IMPORTANT", 3),
                rule(3L, 70D, null, "SEVERE", 2),
                rule(4L, 85D, null, "CRITICAL", 1));

        // 模拟"新建告警落库后 selectList 可查到":发布 Triggered 事件时把该告警登记为已落库,
        // 后续 findActiveAlert 返回同一对象(升级为原地更新,与真实落库 update 语义一致)
        AtomicReference<AlertEvent> persisted = new AtomicReference<>();
        doAnswer(inv -> {
            persisted.set(inv.getArgument(0, AlertTriggeredEvent.class).getAlertEvent());
            return null;
        }).when(eventPublisher).publishEvent(any(AlertTriggeredEvent.class));
        // 事件持有的是同一可变 AlertEvent 对象,事后读 level 只见终态,
        // 故按发布顺序在发布时刻记录每次升级事件的等级快照
        List<String> escalationLevelsAtPublish = new ArrayList<>();
        doAnswer(inv -> {
            escalationLevelsAtPublish.add(inv.getArgument(0, AlertEscalatedEvent.class)
                    .getAlertEvent().getAlertLevel());
            return null;
        }).when(eventPublisher).publishEvent(any(AlertEscalatedEvent.class));
        when(alertEventMapper.selectList(any(Wrapper.class)))
                .thenAnswer(inv -> persisted.get() == null ? List.of() : List.of(persisted.get()));

        LocalDateTime base = LocalDateTime.of(2026, 8, 31, 10, 0, 0);
        LocalDateTime firstTriggerTime = base.plusSeconds(4);  // 第 5 个数据点触发新建

        // 41×5 → WARNING 告警新建(escalationCount=0)
        feedAt(41, base);
        feedAt(41, base.plusSeconds(1));
        feedAt(41, base.plusSeconds(2));
        feedAt(41, base.plusSeconds(3));
        feedAt(41, firstTriggerTime);
        // 56×3 → 同条告警升级 IMPORTANT(escalationCount=1)
        feedAt(56, base.plusSeconds(5));
        feedAt(56, base.plusSeconds(6));
        feedAt(56, base.plusSeconds(7));
        // 71×2 → 同条告警升级 SEVERE(escalationCount=2;期间 WARNING 规则防抖再次满足被去重)
        feedAt(71, base.plusSeconds(8));
        feedAt(71, base.plusSeconds(9));
        // 86×1 → 危急规则 sustainPoints=1 单点即升级 CRITICAL(escalationCount=3;IMPORTANT 规则命中被去重)
        feedAt(86, base.plusSeconds(10));

        verify(eventPublisher, times(1)).publishEvent(any(AlertTriggeredEvent.class));
        ArgumentCaptor<AlertEscalatedEvent> captor = ArgumentCaptor.forClass(AlertEscalatedEvent.class);
        verify(eventPublisher, times(3)).publishEvent(captor.capture());
        List<AlertEscalatedEvent> escalatedEvents = captor.getAllValues();

        // 同一 AlertEvent 对象经历三次升级(升级即原地更新,绝不新建)
        AlertEvent theAlert = escalatedEvents.get(0).getAlertEvent();
        assertSame(theAlert, escalatedEvents.get(1).getAlertEvent(), "三次升级应为同一告警对象");
        assertSame(theAlert, escalatedEvents.get(2).getAlertEvent(), "三次升级应为同一告警对象");
        assertSame(persisted.get(), theAlert, "升级的应是已落库的原告警");
        // 三次升级按发布时刻的等级序列 IMPORTANT→SEVERE→CRITICAL
        assertEquals(List.of("IMPORTANT", "SEVERE", "CRITICAL"), escalationLevelsAtPublish);

        // 终态:危急等级、升级三次、触发值 86、首次触发时间不变
        assertEquals("CRITICAL", theAlert.getAlertLevel());
        assertEquals(3, theAlert.getEscalationCount().intValue());
        assertEquals(86.0, theAlert.getSensorValue());
        assertEquals(firstTriggerTime, theAlert.getTriggerTime(), "triggerTime 应保持首次触发时间");

        // 证据链:escalations 三条,from/to 序列 WARNING→IMPORTANT→SEVERE→CRITICAL
        Map<?, ?> ev = objectMapper.readValue(theAlert.getEvidence(), Map.class);
        assertEquals("upper", ev.get("breach"), "原证据的方向信息应保留");
        assertEquals(5, ev.get("sustain"), "原证据的首次防抖计数应保留");
        List<?> escalations = (List<?>) ev.get("escalations");
        assertEquals(3, escalations.size());
        Map<?, ?> r1 = (Map<?, ?>) escalations.get(0);
        assertEquals("WARNING", r1.get("from"));
        assertEquals("IMPORTANT", r1.get("to"));
        assertEquals(55.0, r1.get("threshold"), "阈值应取 IMPORTANT 规则上限");
        assertEquals(2, r1.get("ruleId"));
        Map<?, ?> r2 = (Map<?, ?>) escalations.get(1);
        assertEquals("IMPORTANT", r2.get("from"));
        assertEquals("SEVERE", r2.get("to"));
        assertEquals(70.0, r2.get("threshold"), "阈值应取 SEVERE 规则上限");
        assertEquals(3, r2.get("ruleId"));
        Map<?, ?> r3 = (Map<?, ?>) escalations.get(2);
        assertEquals("SEVERE", r3.get("from"));
        assertEquals("CRITICAL", r3.get("to"));
        assertEquals(85.0, r3.get("threshold"), "阈值应取 CRITICAL 规则上限");
        assertEquals(4, r3.get("ruleId"));
    }

    @Test
    @DisplayName("危急一点触发:CRITICAL 规则 sustainPoints=1,单点越界立即新建告警")
    void criticalSinglePointTrigger() {
        stubRules(rule(4L, 85D, null, "CRITICAL", 1));
        when(alertEventMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        feed(86);  // 危急场景无需防抖等待,单点即告警

        ArgumentCaptor<AlertTriggeredEvent> captor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertEquals("CRITICAL", captor.getValue().getAlertEvent().getAlertLevel());
        assertEquals(0, captor.getValue().getAlertEvent().getEscalationCount().intValue());
        verify(eventPublisher, never()).publishEvent(any(AlertEscalatedEvent.class));
    }
}
