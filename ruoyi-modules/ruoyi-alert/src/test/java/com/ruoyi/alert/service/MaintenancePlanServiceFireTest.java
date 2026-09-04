package com.ruoyi.alert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ruoyi.alert.entity.MaintenancePlan;
import com.ruoyi.alert.entity.WorkOrder;
import com.ruoyi.alert.mapper.MaintenancePlanMapper;
import com.ruoyi.alert.mapper.WorkOrderMapper;
import com.ruoyi.alert.plan.HolidayService;
import com.ruoyi.alert.service.impl.MaintenancePlanServiceImpl;
import com.ruoyi.common.core.exception.ServiceException;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 维护计划 fireDuePlans 调度闭环单元测试(mock Mapper 不依赖 Spring 与数据库)
 * <p>
 * 到点处理三分支(陈旧非 ONCE 只推进/陈旧 ONCE 收口 DONE/正常到点建单+以原触发点
 * 为基准推进)、单条异常不阻断、以及 create 校验/DONE 拒改/有工单拒删/pause-resume
 * 状态机。update 落库内容经解析 LambdaUpdateWrapper 的 SET 子句断言。
 * </p>
 *
 * @author smartartisan
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaintenancePlanServiceFireTest {

    @Mock
    private MaintenancePlanMapper maintenancePlanMapper;
    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private WorkOrderService workOrderService;
    @Mock
    private HolidayService holidayService;

    private MaintenancePlanService planService;

    @BeforeAll
    static void initEntityMeta() {
        // 纯 Mockito 环境手动注册实体元数据,LambdaWrapper 解析 lambda 需要列缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MaintenancePlan.class);
        TableInfoHelper.initTableInfo(assistant, WorkOrder.class);
    }

    @BeforeEach
    void setUp() {
        planService = new MaintenancePlanServiceImpl(
                maintenancePlanMapper, workOrderMapper, workOrderService, holidayService);
        // 公共默认:无到点计划、建单成功、推进更新成功,个别用例自行覆盖
        when(maintenancePlanMapper.selectList(any())).thenReturn(List.of());
        when(maintenancePlanMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(workOrderService.createFromPlan(any())).thenAnswer(inv -> new WorkOrder());
        when(maintenancePlanMapper.insert(any(MaintenancePlan.class))).thenReturn(1);
    }

    // ============ fireDuePlans:陈旧触发点(早于当前 24 小时) ============

    @Test
    @DisplayName("陈旧非 ONCE:不补建工单,next 以当前时刻重算推进(仅 next+update_time)")
    void staleNonOnceSkipsOrderAndRecalculates() {
        LocalDateTime now = LocalDateTime.now();
        MaintenancePlan plan = enabledPlan("DAILY", LocalTime.of(9, 0));
        // 早于当前 25 小时:停机/故障期错过的触发点
        plan.setNextFireTime(now.minusHours(25));
        when(maintenancePlanMapper.selectList(any())).thenReturn(List.of(plan));

        int processed = planService.fireDuePlans();

        assertEquals(1, processed);
        verify(workOrderService, never()).createFromPlan(any());
        Map<String, Object> sets = captureSingleUpdateSets();
        assertFalse(sets.containsKey("status"), "陈旧推进不应触碰状态");
        assertFalse(sets.containsKey("last_fire_time"), "未实际触发不应记 last_fire_time");
        LocalDateTime next = (LocalDateTime) sets.get("next_fire_time");
        assertNotNull(next, "next_fire_time 应被重算落库");
        assertEquals(LocalTime.of(9, 0), next.toLocalTime(), "重算应保持触发时刻语义");
        assertTrue(next.isAfter(now), "新触发点须在当前时刻之后(不再命中陈旧点): " + next);
        assertTrue(next.isBefore(now.plusDays(2)), "重算起点为当前时刻,不应越过两天");
        assertNotNull(sets.get("update_time"));
    }

    @Test
    @DisplayName("陈旧 ONCE:直接置 DONE 收口,next 置 null,不建单")
    void staleOnceGoesDoneDirectly() {
        LocalDateTime now = LocalDateTime.now();
        MaintenancePlan plan = enabledPlan("ONCE", LocalTime.of(9, 0));
        LocalDateTime stale = now.minusHours(25);
        plan.setNextFireTime(stale);
        plan.setFireDate(stale.toLocalDate());
        when(maintenancePlanMapper.selectList(any())).thenReturn(List.of(plan));

        int processed = planService.fireDuePlans();

        assertEquals(1, processed);
        verify(workOrderService, never()).createFromPlan(any());
        Map<String, Object> sets = captureSingleUpdateSets();
        assertEquals("DONE", sets.get("status"), "陈旧一次性计划应直接完结");
        assertNull(sets.get("next_fire_time"), "DONE 后 next 须显式置 null(不再被扫描命中)");
        assertFalse(sets.containsKey("last_fire_time"), "未实际触发不应记 last_fire_time");
    }

    // ============ fireDuePlans:正常到点 ============

    @Test
    @DisplayName("正常 ONCE 到点:建单 + status DONE + next null + last_fire_time 落值")
    void dueOnceCreatesOrderAndFinishes() {
        LocalDateTime now = LocalDateTime.now();
        MaintenancePlan plan = enabledPlan("ONCE", LocalTime.of(9, 0));
        LocalDateTime due = now.minusMinutes(5);
        plan.setNextFireTime(due);
        plan.setFireDate(due.toLocalDate());
        when(maintenancePlanMapper.selectList(any())).thenReturn(List.of(plan));

        int processed = planService.fireDuePlans();

        assertEquals(1, processed);
        verify(workOrderService).createFromPlan(plan);
        Map<String, Object> sets = captureSingleUpdateSets();
        assertEquals("DONE", sets.get("status"));
        assertNull(sets.get("next_fire_time"), "ONCE 无下一次,next 置 null");
        assertNotNull(sets.get("last_fire_time"), "正常触发应记 last_fire_time");
        assertNotNull(sets.get("update_time"));
    }

    @Test
    @DisplayName("正常 DAILY 到点:建单 + 以原触发点为基准推进(调度延迟不改变周期节律)")
    void dueDailyCreatesOrderAndAdvancesFromOriginalPoint() {
        LocalDateTime now = LocalDateTime.now();
        MaintenancePlan plan = enabledPlan("DAILY", LocalTime.of(9, 0));
        // 触发点刚过 5 分钟:next 应等于 原触发点+1天,而非 now+1天
        LocalDateTime due = now.minusMinutes(5);
        plan.setNextFireTime(due);
        plan.setFireTime(due.toLocalTime());
        when(maintenancePlanMapper.selectList(any())).thenReturn(List.of(plan));

        int processed = planService.fireDuePlans();

        assertEquals(1, processed);
        verify(workOrderService).createFromPlan(plan);
        Map<String, Object> sets = captureSingleUpdateSets();
        assertEquals(due.plusDays(1), sets.get("next_fire_time"),
                "推进基准取原触发点,调度延迟不应顺延节律");
        assertNotNull(sets.get("last_fire_time"));
        assertFalse(sets.containsKey("status"), "非 ONCE 推进不应触碰状态");
    }

    @Test
    @DisplayName("同日已有工单(createFromPlan 返回 null)仍推进,不重复建单")
    void duePlanStillAdvancesWhenOrderSkipped() {
        LocalDateTime now = LocalDateTime.now();
        MaintenancePlan plan = enabledPlan("DAILY", LocalTime.of(9, 0));
        LocalDateTime due = now.minusMinutes(5);
        plan.setNextFireTime(due);
        plan.setFireTime(due.toLocalTime());
        when(maintenancePlanMapper.selectList(any())).thenReturn(List.of(plan));
        when(workOrderService.createFromPlan(any())).thenReturn(null);

        int processed = planService.fireDuePlans();

        assertEquals(1, processed, "跳过建单但仍推进,应计入处理数");
        verify(workOrderService).createFromPlan(plan);
        Map<String, Object> sets = captureSingleUpdateSets();
        assertEquals(due.plusDays(1), sets.get("next_fire_time"));
        assertNotNull(sets.get("last_fire_time"));
    }

    // ============ fireDuePlans:批量容错 ============

    @Test
    @DisplayName("单条异常不阻断:第一条建单抛异常,第二条仍被处理")
    void singleFailureDoesNotBlockBatch() {
        LocalDateTime now = LocalDateTime.now();
        MaintenancePlan bad = enabledPlan("DAILY", LocalTime.of(9, 0));
        LocalDateTime due1 = now.minusMinutes(5);
        bad.setNextFireTime(due1);
        bad.setFireTime(due1.toLocalTime());
        MaintenancePlan good = enabledPlan("DAILY", LocalTime.of(9, 0));
        LocalDateTime due2 = now.minusMinutes(6);
        good.setNextFireTime(due2);
        good.setFireTime(due2.toLocalTime());
        when(maintenancePlanMapper.selectList(any())).thenReturn(List.of(bad, good));
        when(workOrderService.createFromPlan(any()))
                .thenThrow(new RuntimeException("work order insert failed"))
                .thenReturn(new WorkOrder());

        int processed = planService.fireDuePlans();

        assertEquals(1, processed, "失败计划不计入,成功计划正常计入");
        verify(workOrderService, times(2)).createFromPlan(any());
        // 仅成功那条推进:失败计划 next 未动,下一轮扫描自然重试
        verify(maintenancePlanMapper, times(1)).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("无到点计划:返回 0 不做任何写动作")
    void noDuePlansNoop() {
        when(maintenancePlanMapper.selectList(any())).thenReturn(List.of());

        assertEquals(0, planService.fireDuePlans());
        verify(workOrderService, never()).createFromPlan(any());
        verify(maintenancePlanMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    // ============ create 校验 ============

    @Test
    @DisplayName("create 校验:ONCE 缺 fireDate → ServiceException")
    void createRejectsOnceWithoutFireDate() {
        MaintenancePlan plan = validPlan("ONCE");
        plan.setFireDate(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> planService.create(plan));
        assertEquals("一次性计划必须指定触发日期", ex.getMessage());
        verify(maintenancePlanMapper, never()).insert(any(MaintenancePlan.class));
    }

    @Test
    @DisplayName("create 校验:MONTHLY fireDay 越界(32/0/null) → ServiceException")
    void createRejectsMonthlyIllegalFireDay() {
        MaintenancePlan overflow = validPlan("MONTHLY");
        overflow.setFireDay(32);
        ServiceException ex = assertThrows(ServiceException.class, () -> planService.create(overflow));
        assertEquals("每月触发日须为 1-31 的整数", ex.getMessage());

        MaintenancePlan zero = validPlan("MONTHLY");
        zero.setFireDay(0);
        assertThrows(ServiceException.class, () -> planService.create(zero));

        MaintenancePlan missing = validPlan("MONTHLY");
        missing.setFireDay(null);
        assertThrows(ServiceException.class, () -> planService.create(missing));

        verify(maintenancePlanMapper, never()).insert(any(MaintenancePlan.class));
    }

    @Test
    @DisplayName("create 校验:ONCE fireDate 已过 → ServiceException(拒绝立刻补建过期工单)")
    void createRejectsPastOnceFireDate() {
        MaintenancePlan plan = validPlan("ONCE");
        plan.setFireDate(LocalDate.now().minusDays(1));

        ServiceException ex = assertThrows(ServiceException.class, () -> planService.create(plan));
        assertEquals("触发日期已过", ex.getMessage());
    }

    @Test
    @DisplayName("create 校验:未知重复类型 → ServiceException(白名单挡非法值)")
    void createRejectsUnknownRepeatType() {
        MaintenancePlan plan = validPlan("HOURLY");

        ServiceException ex = assertThrows(ServiceException.class, () -> planService.create(plan));
        assertEquals("不支持的重复类型: HOURLY", ex.getMessage());
    }

    @Test
    @DisplayName("create 正常:生成 MP 编号、预计算首触时间、状态 ENABLED")
    void createPersistsEnabledPlanWithNextFire() {
        MaintenancePlan plan = validPlan("DAILY");

        MaintenancePlan created = planService.create(plan);

        assertEquals("ENABLED", created.getStatus());
        assertTrue(created.getPlanNo().matches("MP\\d{17}"),
                "planNo 应为 MP+17位数字: " + created.getPlanNo());
        assertNotNull(created.getNextFireTime(), "首触时间应预计算落库");
        assertTrue(created.getNextFireTime().isAfter(LocalDateTime.now().minusSeconds(1)));
        verify(maintenancePlanMapper).insert(plan);
    }

    // ============ update / delete / pause / resume 状态机 ============

    @Test
    @DisplayName("update 状态机:DONE 终态拒改")
    void updateRejectsDonePlan() {
        MaintenancePlan db = validPlan("DAILY");
        db.setId(1L);
        db.setStatus("DONE");
        when(maintenancePlanMapper.selectById(1L)).thenReturn(db);

        MaintenancePlan req = validPlan("DAILY");
        req.setId(1L);

        ServiceException ex = assertThrows(ServiceException.class, () -> planService.update(req));
        assertEquals("已完成的计划不可修改", ex.getMessage());
        verify(maintenancePlanMapper, never()).updateById(any(MaintenancePlan.class));
    }

    @Test
    @DisplayName("delete 状态机:名下已有工单拒删(保溯源凭证),提示可改为暂停")
    void deleteRejectsPlanWithOrders() {
        when(maintenancePlanMapper.selectById(1L)).thenReturn(validPlan("DAILY"));
        when(workOrderMapper.selectCount(any())).thenReturn(3L);

        ServiceException ex = assertThrows(ServiceException.class, () -> planService.delete(1L));
        assertEquals("该计划已生成工单，不能删除，可改为暂停", ex.getMessage());
        verify(maintenancePlanMapper, never()).deleteById(1L);
    }

    @Test
    @DisplayName("delete 正常:无工单可删")
    void deletePlanWithoutOrders() {
        when(maintenancePlanMapper.selectById(1L)).thenReturn(validPlan("DAILY"));
        when(workOrderMapper.selectCount(any())).thenReturn(0L);

        planService.delete(1L);

        verify(maintenancePlanMapper).deleteById(1L);
    }

    @Test
    @DisplayName("pause 状态机:ENABLED 可暂停;PAUSED/DONE 拒绝")
    void pauseStateMachine() {
        MaintenancePlan enabled = validPlan("DAILY");
        enabled.setId(1L);
        enabled.setStatus("ENABLED");
        when(maintenancePlanMapper.selectById(1L)).thenReturn(enabled);

        planService.pause(1L);

        ArgumentCaptor<MaintenancePlan> captor = ArgumentCaptor.forClass(MaintenancePlan.class);
        verify(maintenancePlanMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals("PAUSED", captor.getValue().getStatus());

        MaintenancePlan paused = validPlan("DAILY");
        paused.setId(2L);
        paused.setStatus("PAUSED");
        when(maintenancePlanMapper.selectById(2L)).thenReturn(paused);
        ServiceException ex = assertThrows(ServiceException.class, () -> planService.pause(2L));
        assertEquals("仅启用中的计划可暂停", ex.getMessage());
    }

    @Test
    @DisplayName("resume 状态机:PAUSED 可恢复且以当前时刻重算 next;ENABLED 拒绝")
    void resumeStateMachine() {
        MaintenancePlan paused = validPlan("DAILY");
        paused.setId(1L);
        paused.setStatus("PAUSED");
        paused.setNextFireTime(LocalDateTime.now().minusDays(3));
        when(maintenancePlanMapper.selectById(1L)).thenReturn(paused);

        planService.resume(1L);

        ArgumentCaptor<MaintenancePlan> captor = ArgumentCaptor.forClass(MaintenancePlan.class);
        verify(maintenancePlanMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals("ENABLED", captor.getValue().getStatus());
        // 恢复以当前时刻重算,不沿用暂停前旧值(防恢复瞬间连续补建过期工单)
        assertNotNull(captor.getValue().getNextFireTime());
        assertTrue(captor.getValue().getNextFireTime().isAfter(LocalDateTime.now().minusSeconds(1)),
                "恢复后触发点应在当前时刻之后");

        MaintenancePlan enabled = validPlan("DAILY");
        enabled.setId(2L);
        enabled.setStatus("ENABLED");
        when(maintenancePlanMapper.selectById(2L)).thenReturn(enabled);
        ServiceException ex = assertThrows(ServiceException.class, () -> planService.resume(2L));
        assertEquals("仅已暂停的计划可恢复", ex.getMessage());
    }

    // ============ 断言辅助 ============

    /**
     * 捕获 update(null, wrapper) 的 LambdaUpdateWrapper 并解析 SET 子句为
     * 列名→参数值 映射,用于断言推进/DONE 收口实际落库内容
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> captureSingleUpdateSets() {
        ArgumentCaptor<LambdaUpdateWrapper> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(maintenancePlanMapper).update(isNull(), captor.capture());
        return sqlSets(captor.getValue());
    }

    /**
     * 解析 wrapper 的 SET 子句:片段形如 "next_fire_time=#{ew.paramNameValuePairs.MPGENVAL2}",
     * 列名取 '=' 左侧,值按占位符名从 paramNameValuePairs 取回(null 值同样经占位符传递)
     */
    private static Map<String, Object> sqlSets(LambdaUpdateWrapper<?> wrapper) {
        Map<String, Object> sets = new LinkedHashMap<>();
        String sqlSet = wrapper.getSqlSet();
        if (sqlSet == null) {
            return sets;
        }
        for (String pair : sqlSet.split(",")) {
            int eq = pair.indexOf('=');
            String column = pair.substring(0, eq).trim();
            String valueExpr = pair.substring(eq + 1).trim();
            if (valueExpr.startsWith("#{")) {
                String placeholder = valueExpr.substring(valueExpr.lastIndexOf('.') + 1,
                        valueExpr.length() - 1);
                sets.put(column, wrapper.getParamNameValuePairs().get(placeholder));
            } else {
                sets.put(column, valueExpr);
            }
        }
        return sets;
    }

    // ============ 测试数据构造 ============

    /** ENABLED 到点计划(指定重复类型与触发时刻,nextFireTime 由用例自设) */
    private MaintenancePlan enabledPlan(String repeatType, LocalTime fireTime) {
        MaintenancePlan plan = validPlan(repeatType);
        plan.setId(1L);
        plan.setStatus("ENABLED");
        plan.setFireTime(fireTime);
        return plan;
    }

    /** 通过 create 校验的完整计划(不含 fireDate/fireDay 规则字段) */
    private MaintenancePlan validPlan(String repeatType) {
        MaintenancePlan plan = new MaintenancePlan();
        plan.setPlanNo("MP20260831090000123");
        plan.setPlanName("1号离心泵定期保养");
        plan.setEquipmentId(10);
        plan.setEquipmentName("1号离心泵");
        plan.setMaintenanceType("一级保养");
        plan.setRepeatType(repeatType);
        plan.setFireTime(LocalTime.of(9, 0));
        return plan;
    }
}
