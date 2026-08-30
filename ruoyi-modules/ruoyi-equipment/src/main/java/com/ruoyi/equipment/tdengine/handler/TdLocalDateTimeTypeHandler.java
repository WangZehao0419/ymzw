package com.ruoyi.equipment.tdengine.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * TDengine LocalDateTime TypeHandler(仅注册于 TDengine 独立工厂)
 * <p>
 * MyBatis 内置 LocalDateTimeTypeHandler 读写走 rs.getObject/ps.setObject,
 * 而 taos-jdbcdriver REST 驱动对 setObject(LocalDateTime) 会内联成
 * 无引号的 ISO 字符串(T 分隔),TDengine 端解析为 invalid timestamp;
 * 本 handler 强制走 Timestamp 通道(getTimestamp/setTimestamp)——
 * 这是原 JdbcTemplate 版验证过的可靠路径。主 MySQL 工厂不受影响
 * (各自 SqlSessionFactory 独立注册)。
 * </p>
 *
 * @author smartartisan
 */
@MappedTypes(LocalDateTime.class)
public class TdLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setTimestamp(i, Timestamp.valueOf(parameter));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnName);
        return ts == null ? null : ts.toLocalDateTime();
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnIndex);
        return ts == null ? null : ts.toLocalDateTime();
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Timestamp ts = cs.getTimestamp(columnIndex);
        return ts == null ? null : ts.toLocalDateTime();
    }
}
