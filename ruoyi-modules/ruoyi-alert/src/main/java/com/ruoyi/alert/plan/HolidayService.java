package com.ruoyi.alert.plan;

import java.time.LocalDate;

/**
 * 法定工作日服务
 * <p>
 * 维护计划 LEGAL_WORKDAY 触发判定的数据基础:国务院节假日安排
 * (放假日/调休补班日)由定时任务经外部 API 同步到 holiday_calendar 缓存表。
 * 判定优先级:内存缓存 → holiday_calendar 例外日 → 周一~五退化规则
 * (普通周末不出现在例外表内)。
 * </p>
 *
 * @author smartartisan
 */
public interface HolidayService {

    /**
     * 法定工作日判定
     *
     * @param date 判定日期
     * @return true=法定工作日(含周末调休补班), false=休息日(法定节假日/周末)
     */
    boolean isLegalWorkday(LocalDate date);

    /**
     * 同步指定年份节假日安排到 holiday_calendar
     * <p>
     * 主源(timor.tech)失败自动切备源(holiday-cn),两源均失败仅记日志,
     * 不向调用方抛异常。
     * </p>
     *
     * @param year 年份(如 2026)
     * @return 落库条数;主备源均失败或该年数据未发布时返回 0
     */
    int syncYear(int year);
}
