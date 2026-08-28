package com.ruoyi.equipment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * TDengine 数据访问服务
 * <p>
 * 负责建库建表(幂等)与传感器时序数据写入。
 * 一传感器一子表,子表名由 sensorCode 规范化而来(特殊字符转下划线)。
 * 落库职责原属 alert 模块,随架构调整(MQTT 统一入口收敛至本模块)迁移至此。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
public class TdengineService {

    private static final String DB = "cloud_iot";
    private static final String STABLE = "sensor_data";
    private static final DateTimeFormatter TS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final JdbcTemplate jdbcTemplate;

    public TdengineService(@Qualifier("tdengineDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 启动时建库建表(幂等)
     */
    @PostConstruct
    public void initSchema() {
        ensureSchema();
    }

    /**
     * 建库 + 建超级表(幂等,启动时调用一次)
     */
    public void ensureSchema() {
        jdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS " + DB);
        jdbcTemplate.execute("CREATE STABLE IF NOT EXISTS " + DB + "." + STABLE +
                " (ts TIMESTAMP, val DOUBLE) TAGS(sensor_code VARCHAR(64), equipment_id INT)");
        log.info("[TDengine] 建库建超级表完成: {}.{}", DB, STABLE);
    }

    /**
     * 写入一条传感器时序数据
     *
     * @param sensorCode  传感器编码(如 VIB-001)
     * @param equipmentId 设备 ID
     * @param value       传感器数值
     * @param ts          采集时间
     */
    public void insertSensorData(String sensorCode, int equipmentId, double value, LocalDateTime ts) {
        String tableName = toTableName(sensorCode);
        String safeCode = escape(sensorCode);
        String sql = String.format(
                "INSERT INTO %s.%s USING %s.%s TAGS('%s', %d) VALUES('%s', %f)",
                DB, tableName, DB, STABLE, safeCode, equipmentId,
                ts.format(TS_FORMATTER), value);
        jdbcTemplate.execute(sql);
    }

    /**
     * sensorCode -> 子表名(仅保留字母数字下划线)
     */
    private String toTableName(String sensorCode) {
        return "sensor_" + sensorCode.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * 转义单引号,防止 SQL 注入
     */
    private String escape(String s) {
        return s.replace("'", "''");
    }
}
