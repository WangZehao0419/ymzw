package com.ruoyi.alert.plan.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.alert.entity.HolidayCalendar;
import com.ruoyi.alert.mapper.HolidayCalendarMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 法定工作日服务单元测试(mock Mapper 不依赖 Spring 与数据库)
 * <p>
 * isLegalWorkday 覆盖"内存缓存 → 例外日 → 周一~五退化"三级判定;
 * 两源 JSON 解析经包级方法 parseTimor/parseHolidayCn 直测(样例串构造),
 * syncYear 的 HTTP 拉取部分依赖网络属已知边界,不在单测范围。
 * </p>
 *
 * @author smartartisan
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HolidayServiceImplTest {

    @Mock
    private HolidayCalendarMapper holidayCalendarMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HolidayServiceImpl holidayService;

    @BeforeAll
    static void initEntityMeta() {
        // 纯 Mockito 环境手动注册实体元数据,LambdaQueryWrapper 解析 lambda 需要列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), HolidayCalendar.class);
    }

    @BeforeEach
    void setUp() {
        holidayService = new HolidayServiceImpl(holidayCalendarMapper, objectMapper);
        // 默认无例外日记录,个别用例自行覆盖
        when(holidayCalendarMapper.selectList(any())).thenReturn(List.of());
    }

    // ============ isLegalWorkday:例外日判定优先于退化规则 ============

    @Test
    @DisplayName("例外日 is_workday=0(放假日) → false,即便当天是周五")
    void exceptionDayOffIsNotWorkday() {
        // 2026-10-02 为周五,若按退化规则应为工作日,例外表放假标记必须优先
        when(holidayCalendarMapper.selectList(any()))
                .thenReturn(List.of(record(LocalDate.of(2026, 10, 2), 0, "国庆节")));

        assertFalse(holidayService.isLegalWorkday(LocalDate.of(2026, 10, 2)));
    }

    @Test
    @DisplayName("例外日 is_workday=1(调休补班) → true,即便当天是周六")
    void exceptionMakeupDayIsWorkday() {
        // 2026-10-10 为周六,若按退化规则应为休息日,例外表补班标记必须优先
        when(holidayCalendarMapper.selectList(any()))
                .thenReturn(List.of(record(LocalDate.of(2026, 10, 10), 1, "国庆节后补班")));

        assertTrue(holidayService.isLegalWorkday(LocalDate.of(2026, 10, 10)));
    }

    @Test
    @DisplayName("例外日 is_workday 为 null 的脏数据 → false(非 1 即非工作日)")
    void exceptionDayWithNullFlagIsNotWorkday() {
        when(holidayCalendarMapper.selectList(any()))
                .thenReturn(List.of(record(LocalDate.of(2026, 10, 3), null, "国庆节")));

        assertFalse(holidayService.isLegalWorkday(LocalDate.of(2026, 10, 3)));
    }

    @Test
    @DisplayName("无记录退化规则:周一~五 → true")
    void fallbackMondayToFridayIsWorkday() {
        assertTrue(holidayService.isLegalWorkday(LocalDate.of(2026, 9, 7)));
    }

    @Test
    @DisplayName("无记录退化规则:周六 → false")
    void fallbackSaturdayIsNotWorkday() {
        assertFalse(holidayService.isLegalWorkday(LocalDate.of(2026, 9, 12)));
    }

    @Test
    @DisplayName("无记录退化规则:周日 → false")
    void fallbackSundayIsNotWorkday() {
        assertFalse(holidayService.isLegalWorkday(LocalDate.of(2026, 9, 13)));
    }

    @Test
    @DisplayName("同日重复判定命中内存缓存:第二次不再查库")
    void repeatedCheckHitsMemoryCache() {
        LocalDate date = LocalDate.of(2026, 9, 7);

        assertTrue(holidayService.isLegalWorkday(date));
        assertTrue(holidayService.isLegalWorkday(date));

        verify(holidayCalendarMapper, times(1)).selectList(any());
    }

    // ============ 主源 timor.tech 响应解析 ============

    @Test
    @DisplayName("timor 解析:holiday=true 放假→is_workday=0,false 补班→is_workday=1,错年条目跳过")
    void parseTimorMapsHolidayFlags() throws IOException {
        JsonNode root = objectMapper.readTree("{\"code\":0,\"holiday\":{"
                + "\"01-01\":{\"holiday\":true,\"name\":\"元旦\",\"date\":\"2026-01-01\"},"
                + "\"10-10\":{\"holiday\":false,\"name\":\"国庆节后补班\",\"date\":\"2026-10-10\"},"
                + "\"12-31\":{\"holiday\":true,\"name\":\"错年数据\",\"date\":\"2025-12-31\"}}}");

        List<HolidayCalendar> records = holidayService.parseTimor(root, 2026);

        assertEquals(2, records.size(), "年份不匹配(2025-12-31)的条目应被跳过");
        HolidayCalendar off = records.get(0);
        assertEquals(LocalDate.of(2026, 1, 1), off.getCalDate());
        assertEquals(2026, off.getCalYear());
        assertEquals(0, off.getIsWorkday(), "holiday=true 放假日应映射 is_workday=0");
        assertEquals("元旦", off.getName());
        assertEquals("TIMOR", off.getSource());
        HolidayCalendar makeup = records.get(1);
        assertEquals(LocalDate.of(2026, 10, 10), makeup.getCalDate());
        assertEquals(1, makeup.getIsWorkday(), "holiday=false 调休补班应映射 is_workday=1");
        assertEquals("国庆节后补班", makeup.getName());
        assertEquals("TIMOR", makeup.getSource());
    }

    @Test
    @DisplayName("timor 解析:code 非 0 → IllegalStateException(仅 code==0 视为成功)")
    void parseTimorRejectsNonZeroCode() throws IOException {
        JsonNode root = objectMapper.readTree("{\"code\":1,\"holiday\":{}}");

        assertThrows(IllegalStateException.class, () -> holidayService.parseTimor(root, 2026));
    }

    @Test
    @DisplayName("timor 解析:缺少 holiday 对象 → IllegalStateException")
    void parseTimorRejectsMissingHolidayNode() throws IOException {
        JsonNode root = objectMapper.readTree("{\"code\":0}");

        assertThrows(IllegalStateException.class, () -> holidayService.parseTimor(root, 2026));
    }

    // ============ 备源 holiday-cn 响应解析 ============

    @Test
    @DisplayName("holiday-cn 解析:isOffDay=true 放假→is_workday=0,false 补班→is_workday=1,错年条目跳过")
    void parseHolidayCnMapsIsOffDay() throws IOException {
        JsonNode root = objectMapper.readTree("{\"year\":2026,\"days\":["
                + "{\"name\":\"元旦\",\"date\":\"2026-01-01\",\"isOffDay\":true},"
                + "{\"name\":\"国庆节后补班\",\"date\":\"2026-10-10\",\"isOffDay\":false},"
                + "{\"name\":\"错年数据\",\"date\":\"2027-01-01\",\"isOffDay\":true}]}");

        List<HolidayCalendar> records = holidayService.parseHolidayCn(root, 2026);

        assertEquals(2, records.size(), "年份不匹配(2027-01-01)的条目应被跳过");
        HolidayCalendar off = records.get(0);
        assertEquals(LocalDate.of(2026, 1, 1), off.getCalDate());
        assertEquals(2026, off.getCalYear());
        assertEquals(0, off.getIsWorkday(), "isOffDay=true 放假日应映射 is_workday=0");
        assertEquals("元旦", off.getName());
        assertEquals("HOLIDAY_CN", off.getSource());
        HolidayCalendar makeup = records.get(1);
        assertEquals(LocalDate.of(2026, 10, 10), makeup.getCalDate());
        assertEquals(1, makeup.getIsWorkday(), "isOffDay=false 调休补班应映射 is_workday=1");
        assertEquals("HOLIDAY_CN", makeup.getSource());
    }

    @Test
    @DisplayName("holiday-cn 解析:无 days 数组 → 空列表(备源该年数据未发布时兜底)")
    void parseHolidayCnEmptyDays() throws IOException {
        JsonNode root = objectMapper.readTree("{\"year\":2027}");

        List<HolidayCalendar> records = holidayService.parseHolidayCn(root, 2027);

        assertTrue(records.isEmpty());
    }

    // ============ 测试数据构造 ============

    /** 例外日记录 */
    private HolidayCalendar record(LocalDate date, Integer isWorkday, String name) {
        HolidayCalendar record = new HolidayCalendar();
        record.setCalDate(date);
        record.setCalYear(date.getYear());
        record.setIsWorkday(isWorkday);
        record.setName(name);
        record.setSource("TIMOR");
        return record;
    }
}
