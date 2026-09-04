package com.ruoyi.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 法定工作日数据缓存，外部 API 自动同步
 *
 * @author smartartisan
 */
@Data
@TableName("holiday_calendar")
public class HolidayCalendar {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 日期(国务院安排涉及的例外日: 放假日/补班日) */
    @TableField("cal_date")
    private LocalDate calDate;

    /** 年份(同步批次键) */
    @TableField("cal_year")
    private Integer calYear;

    /** 1=工作日(调休补班), 0=休息日(法定节假日/调休连休) */
    @TableField("is_workday")
    private Integer isWorkday;

    /** 节假日名称(如 春节/国庆节后补班) */
    @TableField("name")
    private String name;

    /** 数据来源: TIMOR/HOLIDAY_CN */
    @TableField("source")
    private String source;

    /** 同步时间 */
    @TableField("fetch_time")
    private LocalDateTime fetchTime;
}
