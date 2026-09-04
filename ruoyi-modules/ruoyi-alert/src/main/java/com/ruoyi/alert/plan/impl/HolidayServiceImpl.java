package com.ruoyi.alert.plan.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.alert.entity.HolidayCalendar;
import com.ruoyi.alert.mapper.HolidayCalendarMapper;
import com.ruoyi.alert.plan.HolidayService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 法定工作日服务实现
 * <p>
 * 数据流:外部 API(timor.tech 主源/holiday-cn 备源) → holiday_calendar 缓存表 →
 * isLegalWorkday(内存缓存 → 库 → 周一~五退化)。
 * HTTP 用 JDK17 自带 java.net.http.HttpClient、JSON 用模块既有 Jackson,零新增依赖;
 * 同步失败仅记日志返回 0,不影响任何调用方主流程。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayServiceImpl implements HolidayService {

    /** 主源:timor.tech 节假日 API(含调休补班标记) */
    private static final String PRIMARY_URL = "https://timor.tech/api/holiday/year/";

    /** 备源:GitHub holiday-cn 仓库原始 JSON(国务院安排,次年未发布会 404) */
    private static final String BACKUP_URL = "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/";

    /** 缓存上限(约 2 年日期量):超过时清理窗口外老数据,防长期运行内存无限增长 */
    private static final int CACHE_LIMIT = 800;

    private final HolidayCalendarMapper holidayCalendarMapper;
    private final ObjectMapper objectMapper;

    /** 连接超时(毫秒) */
    @Value("${plan.holiday.connect-timeout-ms:3000}")
    private long connectTimeoutMs;

    /** 读取超时(毫秒) */
    @Value("${plan.holiday.read-timeout-ms:5000}")
    private long readTimeoutMs;

    /** 当日判定结果缓存:维护计划扫描分钟级高频判定,命中免查库 */
    private final ConcurrentHashMap<LocalDate, Boolean> cache = new ConcurrentHashMap<>();

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        // JDK HttpClient 无独立读超时 API,读取超时由每个请求的 timeout 承担
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    @Override
    public boolean isLegalWorkday(LocalDate date) {
        // 1.内存缓存优先:高频判定避免每次查库
        Boolean cached = cache.get(date);
        if (cached != null) {
            return cached;
        }
        // 2.查 holiday_calendar 例外日;不用 selectOne——同日期若出现多条脏数据
        // 会抛 TooManyResultsException 中断判定,取首条即可
        HolidayCalendar record = holidayCalendarMapper.selectList(new LambdaQueryWrapper<HolidayCalendar>()
                        .eq(HolidayCalendar::getCalDate, date))
                .stream().findFirst().orElse(null);
        boolean workday;
        if (record != null) {
            workday = record.getIsWorkday() != null && record.getIsWorkday() == 1;
        } else {
            // 3.无记录退化:普通周末不在例外表内,按周一~五判定(DayOfWeek 值: 周一=1..周日=7)
            workday = date.getDayOfWeek().getValue() <= 5;
        }
        cachePut(date, workday);
        return workday;
    }

    /**
     * 缓存写入:超上限先清窗口外老数据(只保留近 2 年),
     * 仍超限(调用方扫描跨度极大)则整体清空——重建成本仅为重新查库,简单可控
     */
    private void cachePut(LocalDate date, boolean workday) {
        if (cache.size() >= CACHE_LIMIT) {
            LocalDate earliest = LocalDate.now().minusYears(2);
            cache.keySet().removeIf(d -> d.isBefore(earliest));
            if (cache.size() >= CACHE_LIMIT) {
                cache.clear();
            }
        }
        cache.put(date, workday);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncYear(int year) {
        List<HolidayCalendar> records = null;
        try {
            records = fetchTimor(year);
        } catch (Exception e) {
            // 请求/解析失败切备源重试,仅记单行 warn
            log.warn("[PLAN] 节假日主源(timor.tech)拉取失败: year={}, error={}", year, e.getMessage());
        }
        if (records == null || records.isEmpty()) {
            // 主源不可用或该年无数据:备源兜底(次年未发布时同样可能拉空)
            try {
                records = fetchHolidayCn(year);
            } catch (Exception e) {
                log.warn("[PLAN] 节假日备源(holiday-cn)拉取失败: year={}, error={}", year, e.getMessage());
            }
        }
        if (records == null) {
            // 两源均请求/解析失败:warn 返回 0,不向调用方抛异常
            log.warn("[PLAN] 节假日同步失败,主备数据源均不可用: year={}", year);
            return 0;
        }
        if (records.isEmpty()) {
            // 有效响应但无该年数据:次年安排官方通常 11 月才发布,属正常情况
            log.info("[PLAN] 节假日数据源暂无 {} 年安排(次年数据未发布),本次跳过", year);
            return 0;
        }
        // 落库幂等:同年旧记录整体删除后重插,事务保证替换原子性,
        // 启动同步+每日定时反复执行不会产生重复数据
        LocalDateTime now = LocalDateTime.now();
        for (HolidayCalendar record : records) {
            record.setFetchTime(now);
        }
        holidayCalendarMapper.delete(new LambdaQueryWrapper<HolidayCalendar>()
                .eq(HolidayCalendar::getCalYear, year));
        for (HolidayCalendar record : records) {
            holidayCalendarMapper.insert(record);
        }
        // 新数据落库后清空判定缓存:淘汰按旧数据/退化规则得出的过期结论
        cache.clear();
        log.info("[PLAN] 节假日同步完成: year={}, source={}, count={}",
                year, records.get(0).getSource(), records.size());
        return records.size();
    }

    /**
     * 主源 timor.tech 拉取并解析(网络动作,不可单测)
     */
    private List<HolidayCalendar> fetchTimor(int year) throws IOException, InterruptedException {
        return parseTimor(objectMapper.readTree(httpGet(PRIMARY_URL + year)), year);
    }

    /**
     * 主源 timor 响应解析(包级可见:纯 JSON→记录转换,不起网络直测)
     * {"code":0,"holiday":{"01-01":{"holiday":true,"name":"元旦","date":"2026-01-01"},
     *  "01-04":{"holiday":false,"name":"元旦后补班","date":"2026-01-04"}}}
     * <p>holiday=true 放假日(is_workday=0),false 调休补班(is_workday=1);仅 code==0 视为成功。</p>
     */
    List<HolidayCalendar> parseTimor(JsonNode root, int year) {
        if (root.path("code").asInt(-1) != 0) {
            throw new IllegalStateException("timor 响应 code 非 0: " + root.path("code").asInt());
        }
        JsonNode holiday = root.path("holiday");
        if (!holiday.isObject()) {
            throw new IllegalStateException("timor 响应缺少 holiday 对象");
        }
        List<HolidayCalendar> records = new ArrayList<>();
        // 对象节点的 forEach 按字段值迭代,每个值为该日期的安排
        holiday.forEach(node -> {
            LocalDate date = LocalDate.parse(node.path("date").asText());
            // 防脏数据:解析出的日期年份必须等于请求年份,否则跳过
            if (date.getYear() != year) {
                log.warn("[PLAN] timor 日期年份与请求不符,跳过: expect={}, date={}", year, date);
                return;
            }
            HolidayCalendar record = new HolidayCalendar();
            record.setCalDate(date);
            record.setCalYear(year);
            record.setIsWorkday(node.path("holiday").asBoolean() ? 0 : 1);
            record.setName(node.path("name").asText(null));
            record.setSource("TIMOR");
            records.add(record);
        });
        return records;
    }

    /**
     * 备源 holiday-cn 拉取并解析(网络动作,不可单测)
     */
    private List<HolidayCalendar> fetchHolidayCn(int year) throws IOException, InterruptedException {
        return parseHolidayCn(objectMapper.readTree(httpGet(BACKUP_URL + year + ".json")), year);
    }

    /**
     * 备源 holiday-cn 响应解析(包级可见:纯 JSON→记录转换,不起网络直测)
     * {"year":2026,"days":[{"name":"元旦","date":"2026-01-01","isOffDay":true},
     *  {"name":"国庆节","date":"2026-10-10","isOffDay":false}]}
     * <p>isOffDay=true 放假日(is_workday=0),false 补班(is_workday=1)。</p>
     */
    List<HolidayCalendar> parseHolidayCn(JsonNode root, int year) {
        List<HolidayCalendar> records = new ArrayList<>();
        for (JsonNode day : root.path("days")) {
            LocalDate date = LocalDate.parse(day.path("date").asText());
            // 防脏数据:解析出的日期年份必须等于请求年份,否则跳过
            if (date.getYear() != year) {
                log.warn("[PLAN] holiday-cn 日期年份与请求不符,跳过: expect={}, date={}", year, date);
                continue;
            }
            HolidayCalendar record = new HolidayCalendar();
            record.setCalDate(date);
            record.setCalYear(year);
            record.setIsWorkday(day.path("isOffDay").asBoolean() ? 0 : 1);
            record.setName(day.path("name").asText(null));
            record.setSource("HOLIDAY_CN");
            records.add(record);
        }
        return records;
    }

    /**
     * GET 请求:非 200 视为失败(备源次年未发布时返回 404)
     */
    private String httpGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(readTimeoutMs))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP 状态码 " + response.statusCode() + ": " + url);
        }
        return response.body();
    }
}
