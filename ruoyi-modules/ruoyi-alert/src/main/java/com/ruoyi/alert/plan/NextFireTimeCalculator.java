package com.ruoyi.alert.plan;

import com.ruoyi.alert.entity.MaintenancePlan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.function.Predicate;

/**
 * 维保计划重复规则的下一次触发时刻计算器(小米闹钟式语义)
 * <p>
 * 维护计划按 5 种闹钟式重复规则定时生成维保工单,下次触发时间由后端预计算落库,
 * 触发任务只做"到点即发 + 重算推进"。相比 cron 表达式,闹钟式规则(ONCE 一次性 /
 * DAILY 每天 / WEEKDAYS 周一至周五 / MONTHLY 每月 N 号 / LEGAL_WORKDAY 法定
 * 工作日,含调休补班,周末补班日也触发)足够覆盖维保场景,且字段有限、语义封闭,
 * 可以对全部边界(月末跳月/跨年/调休补班)穷举单测。纯静态工具类,不依赖 Spring
 * 与数据库,保证计算逻辑可独立验证。
 * </p>
 *
 * @author smartartisan
 */
public final class NextFireTimeCalculator {

    /** 逐日推进安全上限(天):正常规则最坏只需数天,400 天仅防御判定函数恒 false 导致的死循环 */
    private static final int MAX_SCAN_DAYS = 400;

    /** 月度推进安全上限(月):合法 fireDay 最多连跳 1 个月(小月后必有大月),48 个月仅防御异常数据 */
    private static final int MAX_SCAN_MONTHS = 48;

    private NextFireTimeCalculator() {
    }

    /**
     * 计算 base 之后(严格晚于)的下一个触发时刻
     *
     * @param plan      维护计划(repeatType/fireTime/fireDay/fireDate)
     * @param base      基准时刻:触发推进场景为本次触发时刻,创建场景为当前时刻
     * @param isWorkday 法定工作日判定(仅 LEGAL_WORKDAY 使用,须已含调休补班覆盖)
     * @return 下一个触发时刻;ONCE 恒返回固定时刻(即使不晚于 base)
     */
    public static LocalDateTime next(MaintenancePlan plan, LocalDateTime base, Predicate<LocalDate> isWorkday) {
        // repeatType 为数据库字符串字段,trim+大写归一以防御手工数据的大小写/空格漂移
        String type = plan.getRepeatType() == null ? "" : plan.getRepeatType().trim().toUpperCase();
        LocalTime fireTime = plan.getFireTime();
        // 五种规则都依赖触发时刻,缺失即数据不完整,快速失败优于算出错误结果
        if (fireTime == null) {
            throw new IllegalArgumentException("fireTime 不能为空: planNo=" + plan.getPlanNo());
        }
        switch (type) {
            case "ONCE":
                return onceAt(plan, fireTime);
            case "DAILY":
                // DAILY 即"每天都满足条件",与 WEEKDAYS/LEGAL_WORKDAY 共用逐日推进逻辑
                return nextByDay(base.toLocalDate(), fireTime, base, d -> true);
            case "WEEKDAYS":
                return nextByDay(base.toLocalDate(), fireTime, base, NextFireTimeCalculator::isMonToFri);
            case "MONTHLY":
                return nextMonthly(base, fireTime, plan.getFireDay());
            case "LEGAL_WORKDAY":
                if (isWorkday == null) {
                    throw new IllegalArgumentException("LEGAL_WORKDAY 规则需要提供工作日判定函数 isWorkday");
                }
                // isWorkday 已含调休补班覆盖:周末补班日返回 true,自然会被选中触发
                return nextByDay(base.toLocalDate(), fireTime, base, isWorkday);
            default:
                throw new IllegalArgumentException("未知重复类型: " + plan.getRepeatType());
        }
    }

    /**
     * 创建计划时的首个触发点
     *
     * @param plan      维护计划
     * @param now       创建时刻
     * @param isWorkday 法定工作日判定(仅 LEGAL_WORKDAY 使用)
     * @return 首个触发时刻
     */
    public static LocalDateTime firstFire(MaintenancePlan plan, LocalDateTime now, Predicate<LocalDate> isWorkday) {
        // ONCE 的 next 恒返回固定时刻(与 base 无关);其余规则从 now 起算的"下一次"
        // 与创建场景的"首次触发"语义完全一致,故直接复用 next,保证两条路径永不分叉
        return next(plan, now, isWorkday);
    }

    /**
     * ONCE: 返回 fireDate + fireTime 固定时刻
     * <p>
     * 即使该时刻不晚于 base 也原样返回:Calculator 保持纯函数语义,不做隐式推迟,
     * "fireDate 是否已过"由调用方在创建时校验,避免一次性计划被悄悄改期。
     * </p>
     */
    private static LocalDateTime onceAt(MaintenancePlan plan, LocalTime fireTime) {
        LocalDate fireDate = plan.getFireDate();
        if (fireDate == null) {
            throw new IllegalArgumentException("ONCE 规则需要 fireDate: planNo=" + plan.getPlanNo());
        }
        return LocalDateTime.of(fireDate, fireTime);
    }

    /**
     * 逐日推进寻找下一个满足 dayMatcher 的触发日
     * <p>
     * 起点取 base 当天而非次日:当天 fireTime 尚未过(候选严格晚于 base)就该用当天,
     * 与闹钟"今天还没到点就今天响"的心智一致。
     * </p>
     *
     * @param start      起始日期(base 当天)
     * @param fireTime   触发时刻
     * @param base       基准时刻,候选必须严格晚于它
     * @param dayMatcher 日期是否满足规则(周一~五 / 法定工作日 / 每天)
     */
    private static LocalDateTime nextByDay(LocalDate start, LocalTime fireTime, LocalDateTime base, Predicate<LocalDate> dayMatcher) {
        LocalDate d = start;
        for (int i = 0; i < MAX_SCAN_DAYS; i++) {
            if (dayMatcher.test(d)) {
                LocalDateTime candidate = LocalDateTime.of(d, fireTime);
                // 必须严格晚于 base:推进场景 base 即本次触发时刻,允许相等会让触发任务
                // 每轮算出同一时刻而死循环;允许更早则会对历史时段补发工单
                if (candidate.isAfter(base)) {
                    return candidate;
                }
            }
            d = d.plusDays(1);
        }
        // 正常规则 400 天内必命中(最长法定连休远小于此),走到底说明判定函数恒
        // false(如 isWorkday 实现缺陷或节假日数据错乱),显式失败优于死循环
        throw new IllegalStateException("连续 " + MAX_SCAN_DAYS + " 天未找到符合规则的触发日,请检查重复规则或工作日判定函数");
    }

    /**
     * MONTHLY: 从 base 所在月起按月探测 fireDay 号的触发时刻
     * <p>
     * 本月 fireDay 时刻仍晚于 base 就用本月(语义同"下一次");该月无 fireDay 日
     * (如 31 遇小月)则整月跳过而非取月末最后一天——用户设定的是"每月 N 号",
     * 静默改成月末会在 2 月凭空多触发一次,与闹钟"跳过不响"的行为不一致。
     * 跨年(12 月→次年 1 月)由 YearMonth.plusMonths 自然处理。
     * </p>
     */
    private static LocalDateTime nextMonthly(LocalDateTime base, LocalTime fireTime, Integer fireDay) {
        if (fireDay == null || fireDay < 1 || fireDay > 31) {
            // 非法值会让"存在该日"永远不成立,前置拦截避免落入安全上限兜底
            throw new IllegalArgumentException("MONTHLY 规则 fireDay 非法(须 1-31): " + fireDay);
        }
        YearMonth month = YearMonth.from(base);
        for (int i = 0; i < MAX_SCAN_MONTHS; i++) {
            YearMonth m = month.plusMonths(i);
            if (fireDay <= m.lengthOfMonth()) {
                LocalDateTime candidate = m.atDay(fireDay).atTime(fireTime);
                // 严格晚于 base,理由同逐日推进:防重复触发与历史补发
                if (candidate.isAfter(base)) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("连续 " + MAX_SCAN_MONTHS + " 个月未找到合法触发日,fireDay=" + fireDay);
    }

    /**
     * 周一至周五判定(自然工作日,不含法定节假日与调休)
     */
    private static boolean isMonToFri(LocalDate d) {
        DayOfWeek dw = d.getDayOfWeek();
        return dw != DayOfWeek.SATURDAY && dw != DayOfWeek.SUNDAY;
    }
}
