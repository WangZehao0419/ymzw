package com.ruoyi.alert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ruoyi.alert.entity.MaintenancePlan;
import com.ruoyi.alert.entity.WorkOrder;
import com.ruoyi.alert.entity.WorkOrderActionLog;
import com.ruoyi.alert.mapper.AlertEventMapper;
import com.ruoyi.alert.mapper.WorkOrderActionLogMapper;
import com.ruoyi.alert.mapper.WorkOrderMapper;
import com.ruoyi.alert.predict.PredictStateMachine;
import com.ruoyi.alert.service.impl.WorkOrderServiceImpl;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 维保工单 createFromPlan 单元测试(mock 依赖不依赖 Spring 与数据库)
 * <p>
 * 覆盖计划建单的字段快照(设备级动作,sensor 系列留空,related_id 路由计划)、
 * 描述三分支拼装(含 content/空白 content)、处理人可空、同日幂等拦截。
 * </p>
 *
 * @author smartartisan
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkOrderServiceFromPlanTest {

    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private RemoteEquipmentService remoteEquipmentService;
    @Mock
    private PredictStateMachine predictStateMachine;
    @Mock
    private WorkOrderActionLogMapper workOrderActionLogMapper;
    @Mock
    private AlertEventMapper alertEventMapper;

    private WorkOrderService workOrderService;

    @BeforeAll
    static void initEntityMeta() {
        // 纯 Mockito 环境手动注册实体元数据,LambdaQueryWrapper 解析 lambda 需要列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), WorkOrder.class);
    }

    @BeforeEach
    void setUp() {
        workOrderService = new WorkOrderServiceImpl(
                workOrderMapper, remoteEquipmentService, predictStateMachine,
                workOrderActionLogMapper, alertEventMapper);
        // 同日幂等查询默认无当日工单,个别用例自行覆盖
        when(workOrderMapper.selectCount(any())).thenReturn(0L);
        when(workOrderMapper.insert(any(WorkOrder.class))).thenReturn(1);
        when(workOrderActionLogMapper.insert(any(WorkOrderActionLog.class))).thenReturn(1);
    }

    @Test
    @DisplayName("计划建单字段快照:预防维护/relatedId路由计划/handler快照计划负责人,sensor 系列全空")
    void createFromPlanSnapshotsPlanFields() {
        WorkOrder order = workOrderService.createFromPlan(plan());

        assertNotNull(order);
        assertEquals("预防维护", order.getOrderType(), "计划维保是预防动作,非故障维修");
        // related_id 预防维护路由唯一指向本计划
        assertEquals(7L, order.getRelatedId());
        assertEquals(10, order.getEquipmentId());
        assertEquals("1号离心泵", order.getEquipmentName());
        // 计划是设备级动作:无传感器上下文,溯源统一走 related_id
        assertNull(order.getSensorId());
        assertNull(order.getSensorName());
        assertNull(order.getAlertLevel());
        assertEquals("PENDING", order.getStatus());
        // 计划侧负责人快照到处理人
        assertEquals(9L, order.getHandler());
        assertEquals("张三", order.getHandlerName());
        // 编号:WO + 14位时间戳 + 3位随机 = WO + 17位数字
        assertTrue(order.getOrderNo().matches("WO\\d{17}"),
                "orderNo 应为 WO+17位数字: " + order.getOrderNo());
        // 描述:含 content 时带"：内容"段
        assertEquals("【维护计划】1号离心泵 一级保养：更换润滑油并检查密封件（计划 MP20260831090000123）",
                order.getDescription());

        // 落库实体与返回对象同源(编号一致)
        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).insert(captor.capture());
        assertEquals(order.getOrderNo(), captor.getValue().getOrderNo());
    }

    @Test
    @DisplayName("描述拼装:content 为 null 或空白时省略\"：内容\"段,不出现悬空冒号")
    void createFromPlanDescriptionWithoutContent() {
        MaintenancePlan noContent = plan();
        noContent.setContent(null);
        WorkOrder order = workOrderService.createFromPlan(noContent);
        assertEquals("【维护计划】1号离心泵 一级保养（计划 MP20260831090000123）",
                order.getDescription());
        assertFalse(order.getDescription().contains("："), order.getDescription());

        MaintenancePlan blankContent = plan();
        blankContent.setContent("   ");
        WorkOrder blankOrder = workOrderService.createFromPlan(blankContent);
        assertEquals("【维护计划】1号离心泵 一级保养（计划 MP20260831090000123）",
                blankOrder.getDescription());
    }

    @Test
    @DisplayName("计划未配置负责人:handler 空,留痕文案记待转派")
    void createFromPlanWithoutAssignee() {
        MaintenancePlan unassigned = plan();
        unassigned.setAssigneeId(null);
        unassigned.setAssigneeName(null);

        WorkOrder order = workOrderService.createFromPlan(unassigned);

        assertNotNull(order);
        assertNull(order.getHandler());
        assertNull(order.getHandlerName());
        ArgumentCaptor<WorkOrderActionLog> captor = ArgumentCaptor.forClass(WorkOrderActionLog.class);
        verify(workOrderActionLogMapper).insert(captor.capture());
        assertEquals("CREATE", captor.getValue().getAction());
        assertTrue(captor.getValue().getDetail().contains("待转派"), captor.getValue().getDetail());
        assertFalse(captor.getValue().getDetail().contains("处理人 张三"), captor.getValue().getDetail());
    }

    @Test
    @DisplayName("同日幂等:当日已有该计划预防维护工单 → 返回 null 不 insert(建单与推进非原子的兜底)")
    void createFromPlanSkipsWhenSameDayOrderExists() {
        when(workOrderMapper.selectCount(any())).thenReturn(1L);

        WorkOrder order = workOrderService.createFromPlan(plan());

        assertNull(order);
        verify(workOrderMapper, never()).insert(any(WorkOrder.class));
        // 幂等拦截先于留痕:不产生任何写动作
        verify(workOrderActionLogMapper, never()).insert(any(WorkOrderActionLog.class));
    }

    // ============ 测试数据构造 ============

    /** 完整字段的维护计划(建单输入) */
    private MaintenancePlan plan() {
        MaintenancePlan plan = new MaintenancePlan();
        plan.setId(7L);
        plan.setPlanNo("MP20260831090000123");
        plan.setEquipmentId(10);
        plan.setEquipmentName("1号离心泵");
        plan.setMaintenanceType("一级保养");
        plan.setContent("更换润滑油并检查密封件");
        plan.setAssigneeId(9L);
        plan.setAssigneeName("张三");
        return plan;
    }
}
