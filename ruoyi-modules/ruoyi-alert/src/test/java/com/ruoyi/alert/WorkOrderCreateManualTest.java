package com.ruoyi.alert;

import com.ruoyi.alert.api.domain.WorkOrderCreateDTO;
import com.ruoyi.alert.entity.WorkOrder;
import com.ruoyi.alert.entity.WorkOrderActionLog;
import com.ruoyi.alert.mapper.AlertEventMapper;
import com.ruoyi.alert.mapper.WorkOrderActionLogMapper;
import com.ruoyi.alert.mapper.WorkOrderMapper;
import com.ruoyi.alert.predict.PredictStateMachine;
import com.ruoyi.alert.service.WorkOrderService;
import com.ruoyi.alert.service.impl.WorkOrderServiceImpl;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * createManual 手动创建维保工单单元测试（纯 Mockito，不依赖 Spring）
 *
 * @author smartartisan
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderCreateManualTest {

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

    @BeforeEach
    void setUp() {
        // createManual 不触发去重查询（手动建单不去重），无需注册 TableInfo；
        // 构造参数顺序与 WorkOrderServiceImpl 的 @RequiredArgsConstructor 字段声明顺序一致
        workOrderService = new WorkOrderServiceImpl(
                workOrderMapper, remoteEquipmentService, predictStateMachine,
                workOrderActionLogMapper, alertEventMapper);
    }

    @Test
    @DisplayName("正常路径：status=PENDING、alertLevel 缺省 WARNING、orderNo 以 WO 开头，CREATE 日志 operator=ai-assistant")
    void createManualSuccess() {
        when(workOrderMapper.insert(any(WorkOrder.class))).thenReturn(1);

        WorkOrder order = workOrderService.createManual(cmd(), "ai-assistant");

        assertNotNull(order);
        assertEquals("PENDING", order.getStatus());
        // alertLevel 未传，缺省 WARNING
        assertEquals("WARNING", order.getAlertLevel());
        assertTrue(order.getOrderNo().startsWith("WO"), "orderNo 应以 WO 开头: " + order.getOrderNo());
        // 手动单无自动来源，related_id 不设
        assertNull(order.getRelatedId());
        assertEquals("故障维修", order.getOrderType());
        assertEquals(10, order.getEquipmentId());

        // 建单留痕：CREATE 日志 operator=ai-assistant，detail 含手动创建与设备名
        ArgumentCaptor<WorkOrderActionLog> captor = ArgumentCaptor.forClass(WorkOrderActionLog.class);
        verify(workOrderActionLogMapper).insert(captor.capture());
        assertEquals("CREATE", captor.getValue().getAction());
        assertEquals("ai-assistant", captor.getValue().getOperator());
        assertTrue(captor.getValue().getDetail().contains("手动创建"), captor.getValue().getDetail());
        assertTrue(captor.getValue().getDetail().contains("1号离心泵"), captor.getValue().getDetail());
    }

    @Test
    @DisplayName("显式传入 alertLevel 时原样落库，不覆盖为缺省值")
    void createManualKeepsExplicitAlertLevel() {
        when(workOrderMapper.insert(any(WorkOrder.class))).thenReturn(1);
        WorkOrderCreateDTO cmd = cmd();
        cmd.setAlertLevel("CRITICAL");

        WorkOrder order = workOrderService.createManual(cmd, "ai-assistant");

        assertEquals("CRITICAL", order.getAlertLevel());
    }

    @Test
    @DisplayName("必填缺失：orderType 为 null 抛 ServiceException，不触发 insert")
    void createManualRejectsNullOrderType() {
        WorkOrderCreateDTO cmd = cmd();
        cmd.setOrderType(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> workOrderService.createManual(cmd, "ai-assistant"));
        assertEquals("工单类型不能为空", ex.getMessage());
        verify(workOrderMapper, never()).insert(any(WorkOrder.class));
    }

    @Test
    @DisplayName("必填缺失：description 为空字符串抛 ServiceException，不触发 insert")
    void createManualRejectsEmptyDescription() {
        WorkOrderCreateDTO cmd = cmd();
        cmd.setDescription("");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> workOrderService.createManual(cmd, "ai-assistant"));
        assertEquals("工单描述不能为空", ex.getMessage());
        verify(workOrderMapper, never()).insert(any(WorkOrder.class));
    }

    @Test
    @DisplayName("order_no 唯一键冲突：DuplicateKeyException 换号重试一次后成功")
    void createManualRetryOnDuplicateOrderNo() {
        when(workOrderMapper.insert(any(WorkOrder.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry 'WO20260908'"))
                .thenReturn(1);

        WorkOrder order = workOrderService.createManual(cmd(), "ai-assistant");

        assertNotNull(order);
        verify(workOrderMapper, times(2)).insert(any(WorkOrder.class));
        assertTrue(order.getOrderNo().startsWith("WO"), order.getOrderNo());
    }

    /** 合法建单命令（alertLevel 刻意不设，用于验证缺省逻辑） */
    private WorkOrderCreateDTO cmd() {
        WorkOrderCreateDTO cmd = new WorkOrderCreateDTO();
        cmd.setOrderType("故障维修");
        cmd.setEquipmentId(10);
        cmd.setEquipmentName("1号离心泵");
        cmd.setSensorId(1);
        cmd.setSensorName("前轴承温度");
        cmd.setDescription("AI 助手手动创建：前轴承温度偏高，请安排检修");
        return cmd;
    }
}
