package com.ruoyi.alert.plan;

import com.ruoyi.alert.service.MaintenancePlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 维护计划定时触发任务(核心调度闭环)
 * <p>
 * 整体链路:本任务每分钟整点扫描 → fireDuePlans 取 ENABLED 且 next_fire_time
 * 到点的计划 → createFromPlan 生成维保工单(同日同计划幂等) → 按重复规则推进
 * next_fire_time(ONCE 触发后置 DONE) → 推进后的计划等待下轮扫描命中,闭环运转。
 * </p>
 * <p>
 * 幂等与兜底:建单与推进在 fireDuePlans 的同一事务内(建单成功推进必成功);
 * 极端场景下(整批回滚/停机重启)即便触发点被重复命中,工单侧"同日同计划不
 * 重复建单"仍会挡住重复建单,调度闭环不依赖任何单点原子性。
 * </p>
 * <p>
 * 单轮整体异常只记 error 不外抛:调度线程不能因单轮异常死亡,下一分钟整点
 * 自然重试;开关 plan.enabled 关闭时空转,既有计划与工单不受影响。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenancePlanTask {

    private final MaintenancePlanService maintenancePlanService;

    /** 扫描总开关:关闭时空转直接返回(与 PredictTask 开关模式一致) */
    @Value("${plan.enabled:true}")
    private boolean enabled;

    /**
     * 每分钟整点扫描到点计划:建单 + 推进
     */
    @Scheduled(cron = "0 * * * * ?")
    public void run() {
        // 开关关闭:空转返回,不扫描不建单
        if (!enabled) {
            return;
        }
        try {
            int processed = maintenancePlanService.fireDuePlans();
            // 空轮不刷屏:仅在本轮有计划被处理时输出汇总
            if (processed > 0) {
                log.info("[PLAN] 本轮计划触发完成: 处理 {} 条", processed);
            }
        } catch (Exception e) {
            // 调度线程不能因单轮异常死亡:吞掉异常记 error,下一轮整点自然重试
            log.error("[PLAN] 计划触发扫描异常: {}", e.getMessage(), e);
        }
    }
}
