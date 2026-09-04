package com.ruoyi.alert.plan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 法定节假日数据同步任务
 * <p>
 * 启动后异步同步一次 + 每日凌晨定时刷新,每次拉取当年与次年共两个年份
 * (提前拉次年,避免 12 月规划跨年计划时次年无数据可判;次年安排官方
 * 通常 11 月才发布,未发布时拉到 0 条仅记 info 不算失败)。
 * 同步仅影响 holiday_calendar 缓存新鲜度,任何失败都不影响服务主流程。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HolidaySyncTask {

    private final HolidayService holidayService;

    /** 同步开关:关闭时空转直接返回(与 PredictTask 开关模式一致),已有缓存数据继续生效 */
    @Value("${plan.holiday.sync-enabled:true}")
    private boolean syncEnabled;

    /**
     * 启动同步:服务就绪事件触发,独立线程异步执行
     * <p>
     * 不用 @Scheduled 巨大 fixedDelay 模拟一次性任务(语义不可行);
     * 异步避免外部 API 慢/不可达时阻塞启动,守护线程不阻碍 JVM 退出。
     * </p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        // 开关关闭:空转返回,不发起任何外部请求
        if (!syncEnabled) {
            return;
        }
        Thread thread = new Thread(this::syncCurrentAndNextYear, "holiday-sync-startup");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 每日刷新:凌晨低峰拉取,次年数据官方发布后自动补齐,当年安排偶有调整也能及时跟进
     */
    @Scheduled(cron = "${plan.holiday.sync-cron:0 10 3 * * ?}")
    public void dailySync() {
        // 开关关闭:空转返回,不发起任何外部请求
        if (!syncEnabled) {
            return;
        }
        syncCurrentAndNextYear();
    }

    /**
     * 同步当年+次年两个年份
     */
    private void syncCurrentAndNextYear() {
        int year = LocalDate.now().getYear();
        syncQuietly(year);
        syncQuietly(year + 1);
    }

    /**
     * 单年份同步:所有异常就地吞掉仅记 warn,任何失败都不影响调用方
     */
    private void syncQuietly(int year) {
        try {
            holidayService.syncYear(year);
        } catch (Exception e) {
            log.warn("[PLAN] 节假日同步异常: year={}, error={}", year, e.getMessage());
        }
    }
}
