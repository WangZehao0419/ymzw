package com.ruoyi.alert.workorder;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.entity.MaintenancePlan;
import com.ruoyi.alert.entity.WorkOrder;
import com.ruoyi.alert.entity.WorkOrderActionLog;
import com.ruoyi.alert.mapper.AlertEventMapper;
import com.ruoyi.alert.mapper.WorkOrderActionLogMapper;
import com.ruoyi.alert.mapper.WorkOrderMapper;
import com.ruoyi.alert.predict.PredictStateMachine;
import com.ruoyi.alert.service.WorkOrderService;
import com.ruoyi.alert.service.domain.CompleteResult;
import com.ruoyi.alert.service.impl.WorkOrderServiceImpl;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 维保工单服务单元测试(全路径覆盖,mock 依赖不依赖 Spring)
 *
 * @author smartartisan
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkOrderServiceImplTest {

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
        // 纯 Mockito 环境没有 Spring 上下文,MyBatis-Plus 不会扫描 Mapper 初始化
        // TableInfo;LambdaQueryWrapper/LambdaUpdateWrapper 解析 Xxx::getXxx 需要
        // lambda 列缓存,此处手动注册实体元数据(生产环境由 MP 启动时完成,测试只需补一次)
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), WorkOrder.class);
        // complete 联动解除告警的 LambdaUpdateWrapper 会解析 AlertEvent::getXxx,须一并注册
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlertEvent.class);
    }

    @BeforeEach
    void setUp() {
        workOrderService = new WorkOrderServiceImpl(
                workOrderMapper, remoteEquipmentService, predictStateMachine,
                workOrderActionLogMapper, alertEventMapper);
        // 默认开启联动复位,个别用例自行覆盖
        ReflectionTestUtils.setField(workOrderService, "resetOnComplete", true);
        // 去重查询默认无未结工单,个别用例自行覆盖
        when(workOrderMapper.selectCount(any())).thenReturn(0L);
        when(workOrderMapper.insert(any(WorkOrder.class))).thenReturn(1);
    }

    // ============ createFromAlert ============

    @Test
    @DisplayName("去重命中:同设备同传感器同来源已有未结工单,返回 null 不 insert")
    void createSkipWhenDuplicate() {
        when(workOrderMapper.selectCount(any())).thenReturn(1L);

        WorkOrder order = workOrderService.createFromAlert(ruleAlert(), null, null);

        assertNull(order);
        verify(workOrderMapper, never()).insert(any(WorkOrder.class));
    }

    @Test
    @DisplayName("防御过滤:STAT 类型/NORMAL 级别不生成工单(先于去重,不触发 selectCount)")
    void createSkipOnTypeOrLevelFilter() {
        AlertEvent stat = ruleAlert();
        stat.setAlertType("STAT");
        assertNull(workOrderService.createFromAlert(stat, null, null));

        AlertEvent normal = ruleAlert();
        normal.setAlertLevel("NORMAL");
        assertNull(workOrderService.createFromAlert(normal, null, null));

        verify(workOrderMapper, never()).selectCount(any());
        verify(workOrderMapper, never()).insert(any(WorkOrder.class));
    }

    @Test
    @DisplayName("RULE 告警生成工单:故障维修 + relatedId路由告警 + handler快照 + 阈值告警描述 + orderNo 格式 WO+17位数字")
    void createFromRuleAlert() {
        WorkOrder order = workOrderService.createFromAlert(ruleAlert(), 9L, "张三");

        assertNotNull(order);
        // order_type 成为唯一来源判别字段
        assertEquals("故障维修", order.getOrderType());
        assertEquals("PENDING", order.getStatus());
        // 处理人快照:工单生成时为设备负责人
        assertEquals(9L, order.getHandler());
        assertEquals("张三", order.getHandlerName());
        // 快照字段
        assertEquals(100L, order.getRelatedId());
        assertEquals(10, order.getEquipmentId());
        assertEquals(1, order.getSensorId());
        assertEquals("SEVERE", order.getAlertLevel());
        // 描述:阈值告警分支带当前值
        assertTrue(order.getDescription().contains("【阈值告警】"), order.getDescription());
        assertTrue(order.getDescription().contains("88.5"), order.getDescription());
        // 编号:WO + 14位时间戳 + 3位随机 = WO + 17位数字
        assertTrue(order.getOrderNo().matches("WO\\d{17}"),
                "orderNo 应为 WO+17位数字: " + order.getOrderNo());

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).insert(captor.capture());
        assertEquals(order.getOrderNo(), captor.getValue().getOrderNo());
    }

    @Test
    @DisplayName("PREDICT 不建单:已分表 predict_alert,id 快照会造成追溯错乱(split-predict 设计)")
    void createFromPredictAlertWithBreachTime() {
        AlertEvent alert = predictAlert();
        alert.setPredictedBreachTime(LocalDateTime.of(2026, 9, 1, 12, 30));

        assertNull(workOrderService.createFromAlert(alert, null, null));
        // 拦截须先于去重与建单,不产生任何写动作(含流转日志)
        verify(workOrderMapper, never()).insert(any(WorkOrder.class));
        verify(workOrderActionLogMapper, never()).insert(any(WorkOrderActionLog.class));
    }

    @Test
    @DisplayName("PREDICT 无预计越界时刻:同样被拦截不建单")
    void createFromPredictAlertWithoutBreachTime() {
        AlertEvent alert = predictAlert();
        alert.setPredictedBreachTime(null);

        assertNull(workOrderService.createFromAlert(alert, null, null));
        verify(workOrderMapper, never()).insert(any(WorkOrder.class));
    }

    @Test
    @DisplayName("order_no 唯一键冲突:DuplicateKeyException 换号重试一次后成功")
    void createRetryOnDuplicateOrderNo() {
        when(workOrderMapper.insert(any(WorkOrder.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry 'WO20260831'"))
                .thenReturn(1);

        WorkOrder order = workOrderService.createFromAlert(ruleAlert(), null, null);

        assertNotNull(order);
        verify(workOrderMapper, times(2)).insert(any(WorkOrder.class));
        assertTrue(order.getOrderNo().matches("WO\\d{17}"), order.getOrderNo());
    }

    // ============ 状态流转:非法状态 ============

    @Test
    @DisplayName("complete 终态不可完成:COMPLETED 状态抛 ServiceException")
    void completeRejectsNonProcessing() {
        when(workOrderMapper.selectById(1L)).thenReturn(order("COMPLETED"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> workOrderService.complete(1L, "修好了", "admin"));
        assertEquals("当前状态不允许完成", ex.getMessage());
        verify(workOrderMapper, never()).updateById(any(WorkOrder.class));
    }

    @Test
    @DisplayName("cancel 仅 PENDING/PROCESSING 可取消:COMPLETED 状态抛 ServiceException")
    void cancelRejectsFinished() {
        when(workOrderMapper.selectById(1L)).thenReturn(order("COMPLETED"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> workOrderService.cancel(1L, "误建", "admin"));
        assertEquals("当前状态不允许取消", ex.getMessage());
        verify(workOrderMapper, never()).updateById(any(WorkOrder.class));
    }

    @Test
    @DisplayName("工单不存在:统一抛 ServiceException(工单不存在)")
    void orderNotFound() {
        when(workOrderMapper.selectById(99L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> workOrderService.assign(99L, 9L, "张三", "admin"));
        assertEquals("工单不存在", ex.getMessage());
    }

    // ============ complete 复位联动 ============

    @Test
    @DisplayName("complete PENDING 可直接完成:状态落 COMPLETED,复位链路正常(接单删除后一气呵成)")
    void completePendingOrderDirectly() {
        when(workOrderMapper.selectById(1L)).thenReturn(order("PENDING"));
        when(remoteEquipmentService.resetDegradation(any(), anyString())).thenReturn(R.ok());
        when(remoteEquipmentService.listAllSensors(anyString()))
                .thenReturn(R.ok(List.of(sensor(1, "TEMP-001", 10))));

        CompleteResult result = workOrderService.complete(1L, "更换轴承", "admin");

        // 状态落 COMPLETED,复位链路照常触发
        assertTrue(result.isResetSuccess());
        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).updateById(captor.capture());
        assertEquals("COMPLETED", captor.getValue().getStatus());
        verify(predictStateMachine).reset("TEMP-001");
        // 处理人归属由转派决定,完成操作人不再覆写 handler
        assertNull(captor.getValue().getHandler());
    }

    @Test
    @DisplayName("complete 复位失败(Feign R.fail):状态仍置 COMPLETED,resetSuccess=false,状态机不复位")
    void completeWithResetFailure() {
        WorkOrder processing = order("PROCESSING");
        when(workOrderMapper.selectById(1L)).thenReturn(processing);
        when(remoteEquipmentService.resetDegradation(any(), anyString()))
                .thenReturn(R.fail("设备离线"));

        CompleteResult result = workOrderService.complete(1L, "更换轴承", "admin");

        // 工单本身完成不受复位失败影响
        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).updateById(captor.capture());
        assertEquals("COMPLETED", captor.getValue().getStatus());
        assertEquals("更换轴承", captor.getValue().getHandleRemark());
        assertNotNull(captor.getValue().getFinishTime());
        // 处理人归属由转派决定:完成不覆写 handler(Long),留空即未转派
        assertNull(captor.getValue().getHandler());
        // 复位失败:resetSuccess=false 且不触发状态机复位(次序不可倒)
        assertFalse(result.isResetSuccess());
        assertTrue(result.getResetMessage().contains("失败"), result.getResetMessage());
        verify(predictStateMachine, never()).reset(anyString());
    }

    @Test
    @DisplayName("complete 复位成功:对该设备全部传感器 reset,其他设备不动")
    void completeWithResetSuccess() {
        WorkOrder processing = order("PROCESSING");
        when(workOrderMapper.selectById(1L)).thenReturn(processing);
        when(remoteEquipmentService.resetDegradation(any(), anyString())).thenReturn(R.ok());
        when(remoteEquipmentService.listAllSensors(anyString()))
                .thenReturn(R.ok(List.of(sensor(1, "TEMP-001", 10),
                        sensor(2, "TEMP-002", 10),
                        sensor(3, "TEMP-003", 20))));

        CompleteResult result = workOrderService.complete(1L, "更换轴承", "admin");

        assertTrue(result.isResetSuccess());
        // 设备级复位:只重置 equipmentId=10 的传感器,20 号设备的传感器不动
        verify(predictStateMachine).reset("TEMP-001");
        verify(predictStateMachine).reset("TEMP-002");
        verify(predictStateMachine, never()).reset("TEMP-003");
    }

    @Test
    @DisplayName("complete 未配置联动复位:工单完成,不下发复位指令")
    void completeWithoutResetConfig() {
        ReflectionTestUtils.setField(workOrderService, "resetOnComplete", false);
        when(workOrderMapper.selectById(1L)).thenReturn(order("PROCESSING"));

        CompleteResult result = workOrderService.complete(1L, "更换轴承", "admin");

        assertTrue(result.isResetSuccess());
        assertEquals("未配置联动复位", result.getResetMessage());
        verify(remoteEquipmentService, never()).resetDegradation(any(), anyString());
        verify(workOrderMapper).updateById(any(WorkOrder.class));
    }

    // ============ complete 联动解除告警 ============

    @Test
    @DisplayName("complete 联动解除:告警批量解除被调用一次,工单状态落 COMPLETED")
    void completeResolvesActiveAlerts() {
        // 关闭复位联动,聚焦解除动作本身不被复位路径干扰
        ReflectionTestUtils.setField(workOrderService, "resetOnComplete", false);
        when(workOrderMapper.selectById(1L)).thenReturn(order("PROCESSING"));

        CompleteResult result = workOrderService.complete(1L, "更换轴承", "admin");

        // 解除调用恰好一次:update(null, wrapper),null 即 isNull()
        verify(alertEventMapper).update(isNull(), any());
        // 工单状态先行落库,不受解除结果影响
        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).updateById(captor.capture());
        assertEquals("COMPLETED", captor.getValue().getStatus());
        assertTrue(result.isResetSuccess());
    }

    @Test
    @DisplayName("复位失败仍解除:维修完成语义先于复位成功,解除调用不因 R.fail 缺席")
    void completeResolvesAlertsEvenWhenResetFails() {
        when(workOrderMapper.selectById(1L)).thenReturn(order("PROCESSING"));
        when(remoteEquipmentService.resetDegradation(any(), anyString()))
                .thenReturn(R.fail("设备离线"));

        workOrderService.complete(1L, "更换轴承", "admin");

        // 复位失败只影响 CompleteResult,不阻断告警收敛
        verify(alertEventMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("预防维护工单跳过解除:sensorId 为空(设备级预防动作),不触发告警 UPDATE")
    void completeSkipsAlertResolveForPlanOrder() {
        WorkOrder planOrder = order("PROCESSING");
        planOrder.setSensorId(null);
        planOrder.setOrderType("预防维护");
        when(workOrderMapper.selectById(1L)).thenReturn(planOrder);
        when(remoteEquipmentService.resetDegradation(any(), anyString())).thenReturn(R.ok());
        when(remoteEquipmentService.listAllSensors(anyString())).thenReturn(R.ok(List.of()));

        workOrderService.complete(1L, "例行保养完成", "admin");

        verify(alertEventMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("解除异常旁路:alertEventMapper.update 抛异常不阻断完成,工单仍落 COMPLETED")
    void completeToleratesAlertResolveFailure() {
        when(workOrderMapper.selectById(1L)).thenReturn(order("PROCESSING"));
        when(alertEventMapper.update(isNull(), any()))
                .thenThrow(new RuntimeException("alert_event table lock"));
        when(remoteEquipmentService.resetDegradation(any(), anyString())).thenReturn(R.ok());
        when(remoteEquipmentService.listAllSensors(anyString())).thenReturn(R.ok(List.of()));

        assertDoesNotThrow(() -> workOrderService.complete(1L, "更换轴承", "admin"));

        // 解除旁路失败不影响主流程:状态仍落库为 COMPLETED
        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).updateById(captor.capture());
        assertEquals("COMPLETED", captor.getValue().getStatus());
    }

    // ============ assign ============

    @Test
    @DisplayName("assign 正常路径:PENDING 工单转派处理人落库(handler+handlerName)")
    void assignSuccess() {
        when(workOrderMapper.selectById(1L)).thenReturn(order("PENDING"));

        workOrderService.assign(1L, 9L, "张三", "admin");

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals(9L, captor.getValue().getHandler());
        assertEquals("张三", captor.getValue().getHandlerName());
    }

    @Test
    @DisplayName("assign 状态机校验:COMPLETED 工单抛 ServiceException")
    void assignRejectsFinished() {
        when(workOrderMapper.selectById(1L)).thenReturn(order("COMPLETED"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> workOrderService.assign(1L, 9L, "张三", "admin"));
        assertEquals("当前状态不允许指派", ex.getMessage());
    }

    // ============ 工单流转日志 ============

    @Test
    @DisplayName("建单留痕:RULE 有处理人写 CREATE 日志,operator=system,detail 含阈值告警触发与处理人")
    void createLogWithAssignee() {
        workOrderService.createFromAlert(ruleAlert(), 9L, "张三");

        ArgumentCaptor<WorkOrderActionLog> captor = ArgumentCaptor.forClass(WorkOrderActionLog.class);
        verify(workOrderActionLogMapper).insert(captor.capture());
        assertEquals("CREATE", captor.getValue().getAction());
        assertEquals("system", captor.getValue().getOperator());
        assertTrue(captor.getValue().getDetail().contains("阈值告警触发自动生成"), captor.getValue().getDetail());
        assertTrue(captor.getValue().getDetail().contains("处理人 张三"), captor.getValue().getDetail());
    }

    @Test
    @DisplayName("计划建单留痕:CREATE 日志含计划编号与处理人(add-maintenance-plan 建单路径)")
    void createLogFromPlan() {
        MaintenancePlan plan = new MaintenancePlan();
        plan.setId(7L);
        plan.setPlanNo("MP20260831090000123");
        plan.setEquipmentId(10);
        plan.setEquipmentName("1号离心泵");
        plan.setMaintenanceType("一级保养");
        plan.setAssigneeId(9L);
        plan.setAssigneeName("张三");

        WorkOrder order = workOrderService.createFromPlan(plan);

        assertNotNull(order);
        ArgumentCaptor<WorkOrderActionLog> captor = ArgumentCaptor.forClass(WorkOrderActionLog.class);
        verify(workOrderActionLogMapper).insert(captor.capture());
        assertEquals("CREATE", captor.getValue().getAction());
        assertEquals("system", captor.getValue().getOperator());
        assertTrue(captor.getValue().getDetail().contains("MP20260831090000123"), captor.getValue().getDetail());
        assertTrue(captor.getValue().getDetail().contains("处理人 张三"), captor.getValue().getDetail());
    }

    @Test
    @DisplayName("转派留痕:原处理人为空(首派),detail=转派处理人:新名")
    void assignLogFirstAssign() {
        when(workOrderMapper.selectById(1L)).thenReturn(order("PENDING"));

        workOrderService.assign(1L, 9L, "张三", "admin");

        ArgumentCaptor<WorkOrderActionLog> captor = ArgumentCaptor.forClass(WorkOrderActionLog.class);
        verify(workOrderActionLogMapper).insert(captor.capture());
        assertEquals("ASSIGN", captor.getValue().getAction());
        assertEquals("admin", captor.getValue().getOperator());
        assertEquals("转派处理人：张三", captor.getValue().getDetail());
    }

    @Test
    @DisplayName("转派留痕:原处理人非空(改派),detail 含转派处理人:旧名 → 新名")
    void assignLogTransfer() {
        WorkOrder existing = order("PENDING");
        existing.setHandlerName("李四");
        when(workOrderMapper.selectById(1L)).thenReturn(existing);

        workOrderService.assign(1L, 9L, "张三", "admin");

        ArgumentCaptor<WorkOrderActionLog> captor = ArgumentCaptor.forClass(WorkOrderActionLog.class);
        verify(workOrderActionLogMapper).insert(captor.capture());
        assertEquals("ASSIGN", captor.getValue().getAction());
        assertTrue(captor.getValue().getDetail().contains("转派处理人：李四 → 张三"), captor.getValue().getDetail());
    }

    @Test
    @DisplayName("取消留痕:写 CANCEL 日志,detail 含取消原因")
    void cancelLog() {
        when(workOrderMapper.selectById(1L)).thenReturn(order("PENDING"));

        workOrderService.cancel(1L, "误报，无需处理", "admin");

        ArgumentCaptor<WorkOrderActionLog> captor = ArgumentCaptor.forClass(WorkOrderActionLog.class);
        verify(workOrderActionLogMapper).insert(captor.capture());
        assertEquals("CANCEL", captor.getValue().getAction());
        assertEquals("admin", captor.getValue().getOperator());
        assertTrue(captor.getValue().getDetail().contains("误报，无需处理"), captor.getValue().getDetail());
    }

    @Test
    @DisplayName("完成留痕:复位成功路径写 COMPLETE 日志,detail 含处理完成与复位结果")
    void completeLog() {
        when(workOrderMapper.selectById(1L)).thenReturn(order("PROCESSING"));
        when(remoteEquipmentService.resetDegradation(any(), anyString())).thenReturn(R.ok());
        when(remoteEquipmentService.listAllSensors(anyString())).thenReturn(R.ok(List.of()));

        workOrderService.complete(1L, "更换轴承", "admin");

        ArgumentCaptor<WorkOrderActionLog> captor = ArgumentCaptor.forClass(WorkOrderActionLog.class);
        verify(workOrderActionLogMapper).insert(captor.capture());
        assertEquals("COMPLETE", captor.getValue().getAction());
        assertEquals("admin", captor.getValue().getOperator());
        assertTrue(captor.getValue().getDetail().contains("处理完成：更换轴承"), captor.getValue().getDetail());
        assertTrue(captor.getValue().getDetail().contains("复位指令已下发"), captor.getValue().getDetail());
    }

    @Test
    @DisplayName("日志旁路容错:log insert 抛异常时 assign 正常完成不抛")
    void logFailureDoesNotAffectMainFlow() {
        when(workOrderMapper.selectById(1L)).thenReturn(order("PENDING"));
        when(workOrderActionLogMapper.insert(any(WorkOrderActionLog.class)))
                .thenThrow(new RuntimeException("log table missing"));

        assertDoesNotThrow(() -> workOrderService.assign(1L, 9L, "张三", "admin"));
        // 主流程写不受旁路影响:指派仍正常落库
        verify(workOrderMapper).updateById(any(WorkOrder.class));
    }

    // ============ 测试数据构造 ============

    /** RULE/SEVERE 告警(阈值告警建单输入) */
    private AlertEvent ruleAlert() {
        AlertEvent alert = new AlertEvent();
        alert.setId(100L);
        alert.setEquipmentId(10);
        alert.setEquipmentName("1号离心泵");
        alert.setSensorId(1);
        alert.setSensorCode("TEMP-001");
        alert.setSensorName("前轴承温度");
        alert.setAlertType("RULE");
        alert.setAlertLevel("SEVERE");
        alert.setSensorValue(88.5D);
        return alert;
    }

    /** PREDICT/WARNING 告警(预测性维护建单输入) */
    private AlertEvent predictAlert() {
        AlertEvent alert = new AlertEvent();
        alert.setId(200L);
        alert.setEquipmentId(10);
        alert.setEquipmentName("1号离心泵");
        alert.setSensorId(1);
        alert.setSensorCode("TEMP-001");
        alert.setSensorName("前轴承温度");
        alert.setAlertType("PREDICT");
        alert.setAlertLevel("WARNING");
        alert.setSensorValue(61.2D);
        return alert;
    }

    /** 指定状态的在库工单 */
    private WorkOrder order(String status) {
        WorkOrder order = new WorkOrder();
        order.setId(1L);
        order.setOrderNo("WO20260831120000001");
        order.setOrderType("故障维修");
        order.setEquipmentId(10);
        order.setEquipmentName("1号离心泵");
        order.setSensorId(1);
        order.setSensorName("前轴承温度");
        order.setStatus(status);
        return order;
    }

    /** 传感器元数据 */
    private SensorMetaDTO sensor(int id, String code, int equipmentId) {
        SensorMetaDTO s = new SensorMetaDTO();
        s.setId(id);
        s.setSensorCode(code);
        s.setSensorName(code + "传感器");
        s.setEquipmentId(equipmentId);
        s.setEquipmentName(equipmentId + "号设备");
        return s;
    }
}
