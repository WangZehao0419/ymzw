package com.ruoyi.alert.service;

import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.alert.entity.MaintenancePlan;
import com.ruoyi.alert.entity.WorkOrder;
import com.ruoyi.alert.service.domain.CompleteResult;

/**
 * 维保工单服务
 * <p>
 * 工单由告警事件自动生成(事件监听器旁路调用),人工流转走
 * 转派/完成/取消三个状态动作;完成动作内嵌设备退化复位联动。
 * </p>
 *
 * @author smartartisan
 */
public interface WorkOrderService {

    /**
     * 从告警事件生成维保工单
     * <p>
     * 只接受 RULE 类型且 WARNING/IMPORTANT/SEVERE/CRITICAL 级别的告警
     * (NORMAL 为恢复语义无维修动作,不建单);
     * 同设备同传感器同类型(故障维修)已有未完结(PENDING/PROCESSING)工单时跳过,
     * 防模拟器 20 秒/条告警把工单列表刷成重复单。
     * </p>
     *
     * @param alert       告警事件
     * @param handlerId   处理人用户ID(设备未绑定为 null,待转派)
     * @param handlerName 处理人姓名(可空)
     * @return 生成的工单;类型/级别不符或去重命中时返回 null
     */
    WorkOrder createFromAlert(AlertEvent alert, Long handlerId, String handlerName);

    /**
     * 从维护计划生成维保工单
     * <p>
     * 计划到点触发时由调度任务调用;同一计划当日已生成过工单则
     * 返回 null 跳过——计划触发(建单)与 next_fire_time 推进非原子,
     * 建单成功但推进失败的极端场景下,下轮调度会再次命中同一计划,
     * 靠同日幂等防止重复建单。
     * </p>
     *
     * @param plan 到点触发的维护计划
     * @return 生成的工单;同日已有该计划工单时返回 null(跳过语义)
     */
    WorkOrder createFromPlan(MaintenancePlan plan);

    /**
     * 转派处理人
     *
     * @param id          工单ID
     * @param handlerId   处理人用户ID
     * @param handlerName 处理人姓名
     * @param operator    操作人(当前登录用户)
     */
    void assign(Long id, Long handlerId, String handlerName, String operator);

    /**
     * 完成工单(联动设备退化复位)
     * <p>
     * 置 COMPLETED + 处理说明 + 完成时间;按配置联动
     * resetDegradation(Feign→设备服务→MQTT→模拟器退化清零)与
     * 预测状态机复位(清基线,下轮重学)。
     * </p>
     *
     * @param id           工单ID
     * @param handleRemark 处理结果说明
     * @param operator     完成操作人
     * @return 完成结果(复位成功/失败原因)
     */
    CompleteResult complete(Long id, String handleRemark, String operator);

    /**
     * 取消工单(不触发复位)
     *
     * @param id       工单ID
     * @param reason   取消原因
     * @param operator 操作人(当前登录用户)
     */
    void cancel(Long id, String reason, String operator);
}
