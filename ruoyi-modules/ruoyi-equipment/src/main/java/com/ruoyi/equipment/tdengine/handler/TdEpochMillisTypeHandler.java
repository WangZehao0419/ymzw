package com.ruoyi.equipment.tdengine.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * TDengine epoch millis TypeHandler(仅经 TDengine XML resultMap 显式引用)
 * <p>
 * 跨服务时序点 DTO 的 ts 用 Long(epoch millis) 传输;taos REST 驱动对
 * getLong(TIMESTAMP) 列行为不可靠(可能抛异常或按驱动私有格式转数值),
 * 本 handler 强制走 getTimestamp 通道后取 getTime()——与
 * {@link TdLocalDateTimeTypeHandler} 同一原 JdbcTemplate 版验证过的
 * 可靠路径。不经 typeHandlerPackage 注册,不影响主 MySQL 工厂。
 * </p>
 *
 * @author smartartisan
 */
@MappedTypes(Long.class)
public class TdEpochMillisTypeHandler extends BaseTypeHandler<Long> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Long parameter, JdbcType jdbcType)
            throws SQLException {
        // 写场景按 epoch millis 还原 Timestamp(本 mapper 的 ts 仅出现在 SELECT 列,
        // 写路径实现仅为满足 BaseTypeHandler 抽象契约)
        ps.setTimestamp(i, new Timestamp(parameter));
    }

    @Override
    public Long getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnName);
        return ts == null ? null : ts.getTime();
    }

    @Override
    public Long getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnIndex);
        return ts == null ? null : ts.getTime();
    }

    @Override
    public Long getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Timestamp ts = cs.getTimestamp(columnIndex);
        return ts == null ? null : ts.getTime();
    }
}
