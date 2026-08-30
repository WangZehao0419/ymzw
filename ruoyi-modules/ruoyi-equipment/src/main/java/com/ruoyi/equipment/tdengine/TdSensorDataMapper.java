package com.ruoyi.equipment.tdengine;

import com.ruoyi.equipment.api.domain.SensorPointDTO;
import com.ruoyi.equipment.entity.td.TdSensorPoint;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TDengine 传感器时序数据 Mapper(绑定 TDengine 独立 MyBatis 工厂)
 * <p>
 * 本接口必须放在 mapper 包之外(如本包 tdengine):框架 {@code @EnableCustomConfig}
 * 的 {@code @MapperScan("com.ruoyi.**.mapper")} 通配扫描按 AUTOWIRE_BY_TYPE 装配,
 * 只要类路径含 mapper 段就会被抢注并绑定 MyBatis-Plus 主工厂(MySQL),
 * 导致本接口的 SQL 全部发给 MySQL(实测 CREATE STABLE 报 MySQL 语法错误)。
 * 放在无 mapper 段的独立包,通配扫描不命中,由 TdengineConfig 显式注册
 * MapperFactoryBean 绑定 TDengine 工厂,两套工厂互不串包。
 * 手写 SQL 语义与原 JdbcTemplate 版数据访问层完全等价。
 * </p>
 *
 * @author smartartisan
 */
public interface TdSensorDataMapper {

    /**
     * 写入一条传感器时序数据(自动建子表 sensor_{id})
     *
     * @param sensorId    传感器 ID(MySQL 主键,与超级表 tag 同源)
     * @param equipmentId 设备 ID(超级表 tag)
     * @param ts          采集时间
     * @param val         传感器数值
     * @return 影响行数
     */
    int insertOne(@Param("sensorId") int sensorId, @Param("equipmentId") int equipmentId,
                  @Param("ts") LocalDateTime ts, @Param("val") double val);

    /**
     * 分页查询传感器历史时序数据(按时间倒序)
     *
     * @param sensorIds   传感器 ID 列表(空表示不按传感器过滤)
     * @param equipmentId 设备 ID(空表示不按设备过滤)
     * @param start       开始时间(含,空表示不限)
     * @param end         结束时间(含,空表示不限)
     * @param offset      偏移量(&gt;=0)
     * @param limit       每页条数(&gt;=0)
     * @return 时序数据行列表,无数据返回空列表
     */
    List<TdSensorPoint> queryHistory(@Param("sensorIds") List<Integer> sensorIds,
                                     @Param("equipmentId") Integer equipmentId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 按相同条件统计历史时序数据总条数(用于分页 total)
     *
     * @return 总条数
     */
    long countHistory(@Param("sensorIds") List<Integer> sensorIds,
                      @Param("equipmentId") Integer equipmentId,
                      @Param("start") LocalDateTime start,
                      @Param("end") LocalDateTime end);

    /**
     * 查询单个传感器最近 n 条时序数据(按时间倒序,调用方反转为升序使用)
     * <p>
     * 超级表 tag 只有 sensor_id(TDengine 不存 sensor_code),按编码查询需调用方
     * 先回 MySQL equipment_sensor 表换主键;ts 直接映射为 epoch millis Long,
     * 规避 LocalDateTime 跨服务 Jackson 序列化的数组/时区坑。
     * </p>
     *
     * @param sensorId 传感器 ID(超级表 tag,与 MySQL 传感器主键同源)
     * @param n        窗口条数(&gt;0,由 Controller 截断到上限)
     * @return 最近 n 个数据点(ts 降序),无数据返回空列表
     */
    List<SensorPointDTO> selectRecentWindow(@Param("sensorId") Integer sensorId, @Param("n") int n);

    /**
     * 查询单个传感器的最新一条时序数据
     *
     * @param sensorId 传感器 ID
     * @return 最新数据点,无数据返回 null
     */
    TdSensorPoint queryLatest(@Param("sensorId") Integer sensorId);

    /**
     * 查询某设备下每个传感器的最新一条时序数据(实时状态)
     *
     * @param equipmentId 设备 ID(对应超级表 tag)
     * @return 每个传感器一个数据点,无数据返回空列表
     */
    List<TdSensorPoint> queryLatestByEquipment(@Param("equipmentId") Integer equipmentId);

    /**
     * 建库(幂等,启动时由 TdSchemaInitializer 调用)
     */
    @Update("CREATE DATABASE IF NOT EXISTS cloud_iot")
    int createDatabase();

    /**
     * 建超级表(幂等,启动时由 TdSchemaInitializer 调用)
     */
    @Update("CREATE STABLE IF NOT EXISTS cloud_iot.sensor_data (ts TIMESTAMP, val DOUBLE) TAGS(sensor_id INT, equipment_id INT)")
    int createStable();
}
