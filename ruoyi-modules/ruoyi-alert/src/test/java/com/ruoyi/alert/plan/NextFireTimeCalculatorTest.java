package com.ruoyi.alert.plan;

import com.ruoyi.alert.entity.MaintenancePlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 下一次触发时刻计算器单元测试(纯函数直测,不起 Spring)
 * <p>
 * 工作日判定按 Lambda 注入固定日期集合构造:2026 年国庆场景——
 * 10/1-10/7 放假(非工作日)、10/10 周六调休补班(工作日)、其余按周一~五。
 * 2026-10-01 为周四,由此推得 10/06 为周二、10/10 为周六。
 * </p>
 *
 * @author smartartisan
 */
class NextFireTimeCalculatorTest {

    private static final LocalTime FIRE_AT = LocalTime.of(8, 0);

    /** 2026 国庆固定例外日:放假日区间与周六补班日 */
    private static final LocalDate HOLIDAY_START = LocalDate.of(2026, 10, 1);

    private static final LocalDate HOLIDAY_END = LocalDate.of(2026, 10, 7);

    private static final LocalDate MAKEUP_WORKDAY = LocalDate.of(2026, 10, 10);

    // ============ DAILY ============

    @Test
    @DisplayName("DAILY:当天 fireTime 已过 → 次日同刻")
    void dailyRollsToNextDayWhenTodayElapsed() {
        MaintenancePlan plan = plan("DAILY");
        // 2026-08-31 为周一,10:00 已过 08:00
        LocalDateTime base = LocalDateTime.of(2026, 8, 31, 10, 0);

        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 0),
                NextFireTimeCalculator.next(plan, base, null));
    }

    @Test
    @DisplayName("DAILY:当天 fireTime 未过 → 当天(起点取 base 当天而非次日)")
    void dailyFiresTodayWhenNotElapsed() {
        MaintenancePlan plan = plan("DAILY");
        LocalDateTime base = LocalDateTime.of(2026, 8, 31, 7, 0);

        assertEquals(LocalDateTime.of(2026, 8, 31, 8, 0),
                NextFireTimeCalculator.next(plan, base, null));
    }

    // ============ WEEKDAYS ============

    @Test
    @DisplayName("WEEKDAYS:周五触发点已过 → 跳过周末,下周一")
    void weekdaysFridayToMonday() {
        MaintenancePlan plan = plan("WEEKDAYS");
        // 2026-08-28 为周五,09:00 已过 08:00
        LocalDateTime base = LocalDateTime.of(2026, 8, 28, 9, 0);

        assertEquals(LocalDateTime.of(2026, 8, 31, 8, 0),
                NextFireTimeCalculator.next(plan, base, null));
    }

    @Test
    @DisplayName("WEEKDAYS:周六/周日起算 → 均跳到下周一(周末永不触发)")
    void weekdaysSkipWeekend() {
        MaintenancePlan plan = plan("WEEKDAYS");
        // 2026-08-29 周六 / 2026-08-30 周日
        LocalDateTime fromSaturday = LocalDateTime.of(2026, 8, 29, 10, 0);
        LocalDateTime fromSunday = LocalDateTime.of(2026, 8, 30, 10, 0);

        assertEquals(LocalDateTime.of(2026, 8, 31, 8, 0),
                NextFireTimeCalculator.next(plan, fromSaturday, null));
        assertEquals(LocalDateTime.of(2026, 8, 31, 8, 0),
                NextFireTimeCalculator.next(plan, fromSunday, null));
    }

    // ============ MONTHLY ============

    @Test
    @DisplayName("MONTHLY:fireDay=31 在 1/31 触发后 → 跳过无 31 号的 2 月,落 3/31")
    void monthly31SkipsFebruary() {
        MaintenancePlan plan = plan("MONTHLY");
        plan.setFireDay(31);
        LocalDateTime base = LocalDateTime.of(2026, 1, 31, 8, 0);

        assertEquals(LocalDateTime.of(2026, 3, 31, 8, 0),
                NextFireTimeCalculator.next(plan, base, null));
    }

    @Test
    @DisplayName("MONTHLY:本月 fireDay 时刻未过 → 本月(语义同下一次)")
    void monthlyFiresCurrentMonth() {
        MaintenancePlan plan = plan("MONTHLY");
        plan.setFireDay(31);
        LocalDateTime base = LocalDateTime.of(2026, 1, 10, 0, 0);

        assertEquals(LocalDateTime.of(2026, 1, 31, 8, 0),
                NextFireTimeCalculator.next(plan, base, null));
    }

    @Test
    @DisplayName("MONTHLY:跨年 12/31 触发后 → 次年 1/31")
    void monthlyAcrossYear() {
        MaintenancePlan plan = plan("MONTHLY");
        plan.setFireDay(31);
        LocalDateTime base = LocalDateTime.of(2026, 12, 31, 8, 0);

        assertEquals(LocalDateTime.of(2027, 1, 31, 8, 0),
                NextFireTimeCalculator.next(plan, base, null));
    }

    // ============ LEGAL_WORKDAY(2026 国庆场景) ============

    @Test
    @DisplayName("LEGAL_WORKDAY:国庆假期中(10/6 周二)且当日时刻已过 → 10/8 假期后首个工作日")
    void legalWorkdayAfterNationalDay() {
        MaintenancePlan plan = plan("LEGAL_WORKDAY");
        LocalDateTime base = LocalDateTime.of(2026, 10, 6, 10, 0);

        assertEquals(LocalDateTime.of(2026, 10, 8, 8, 0),
                NextFireTimeCalculator.next(plan, base, nationalDay2026()));
    }

    @Test
    @DisplayName("LEGAL_WORKDAY:10/9(周五)当日已过 → 周六补班日 10/10 当日可触发")
    void legalWorkdayHitsMakeupSaturday() {
        MaintenancePlan plan = plan("LEGAL_WORKDAY");
        LocalDateTime base = LocalDateTime.of(2026, 10, 9, 12, 0);

        assertEquals(LocalDateTime.of(2026, 10, 10, 8, 0),
                NextFireTimeCalculator.next(plan, base, nationalDay2026()));
    }

    @Test
    @DisplayName("LEGAL_WORKDAY:补班日当天时刻未过 → 当天补班日触发(不跳到下周一)")
    void legalWorkdayMakeupDayNotElapsed() {
        MaintenancePlan plan = plan("LEGAL_WORKDAY");
        // 10/10 周六补班日 07:00,08:00 未到
        LocalDateTime base = LocalDateTime.of(2026, 10, 10, 7, 0);

        assertEquals(LocalDateTime.of(2026, 10, 10, 8, 0),
                NextFireTimeCalculator.next(plan, base, nationalDay2026()));
    }

    // ============ ONCE ============

    @Test
    @DisplayName("ONCE:恒返回 fireDate+fireTime 固定值,早于 base 也原样返回(纯函数不做隐式推迟)")
    void onceAlwaysReturnsFixedPoint() {
        MaintenancePlan plan = plan("ONCE");
        plan.setFireDate(LocalDate.of(2026, 9, 1));

        // base 晚于触发点:原样返回固定时刻
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 0),
                NextFireTimeCalculator.next(plan, LocalDateTime.of(2026, 12, 1, 0, 0), null));
        // base 早于触发点(创建场景):同样返回固定时刻
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 0),
                NextFireTimeCalculator.firstFire(plan, LocalDateTime.of(2026, 8, 1, 0, 0), null));
    }

    // ============ 非法入参 ============

    @Test
    @DisplayName("ONCE 缺 fireDate → IllegalArgumentException")
    void onceWithoutFireDateRejected() {
        MaintenancePlan plan = plan("ONCE");
        plan.setFireDate(null);

        assertThrows(IllegalArgumentException.class,
                () -> NextFireTimeCalculator.next(plan, LocalDateTime.of(2026, 8, 31, 10, 0), null));
    }

    @Test
    @DisplayName("MONTHLY fireDay 越界(null/0/32) → IllegalArgumentException")
    void monthlyIllegalFireDayRejected() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 31, 10, 0);

        MaintenancePlan nullDay = plan("MONTHLY");
        nullDay.setFireDay(null);
        assertThrows(IllegalArgumentException.class,
                () -> NextFireTimeCalculator.next(nullDay, base, null));

        MaintenancePlan zero = plan("MONTHLY");
        zero.setFireDay(0);
        assertThrows(IllegalArgumentException.class,
                () -> NextFireTimeCalculator.next(zero, base, null));

        MaintenancePlan overflow = plan("MONTHLY");
        overflow.setFireDay(32);
        assertThrows(IllegalArgumentException.class,
                () -> NextFireTimeCalculator.next(overflow, base, null));
    }

    @Test
    @DisplayName("未知重复类型 → IllegalArgumentException")
    void unknownRepeatTypeRejected() {
        MaintenancePlan plan = plan("HOURLY");

        assertThrows(IllegalArgumentException.class,
                () -> NextFireTimeCalculator.next(plan, LocalDateTime.of(2026, 8, 31, 10, 0), null));
    }

    @Test
    @DisplayName("fireTime 缺失 → IllegalArgumentException(五种规则都依赖触发时刻)")
    void missingFireTimeRejected() {
        MaintenancePlan plan = plan("DAILY");
        plan.setFireTime(null);

        assertThrows(IllegalArgumentException.class,
                () -> NextFireTimeCalculator.next(plan, LocalDateTime.of(2026, 8, 31, 10, 0), null));
    }

    @Test
    @DisplayName("LEGAL_WORKDAY 缺 isWorkday 判定函数 → IllegalArgumentException")
    void legalWorkdayWithoutPredicateRejected() {
        MaintenancePlan plan = plan("LEGAL_WORKDAY");

        assertThrows(IllegalArgumentException.class,
                () -> NextFireTimeCalculator.next(plan, LocalDateTime.of(2026, 8, 31, 10, 0), null));
    }

    // ============ 测试数据构造 ============

    /** 指定重复类型的基本计划(编号固定,fireTime 统一 08:00) */
    private MaintenancePlan plan(String repeatType) {
        MaintenancePlan plan = new MaintenancePlan();
        plan.setPlanNo("MP20260831090000123");
        plan.setRepeatType(repeatType);
        plan.setFireTime(FIRE_AT);
        return plan;
    }

    /**
     * 2026 年国庆场景的法定工作日判定(固定日期集合,不依赖外部数据):
     * 10/1-10/7 放假非工作日、10/10 周六补班为工作日、其余按周一~五
     */
    private Predicate<LocalDate> nationalDay2026() {
        return d -> {
            if (MAKEUP_WORKDAY.equals(d)) {
                return true;
            }
            if (!d.isBefore(HOLIDAY_START) && !d.isAfter(HOLIDAY_END)) {
                return false;
            }
            return d.getDayOfWeek().getValue() <= 5;
        };
    }
}
