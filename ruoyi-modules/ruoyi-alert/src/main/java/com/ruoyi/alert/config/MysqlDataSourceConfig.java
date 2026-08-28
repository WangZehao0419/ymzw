package com.ruoyi.alert.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * MySQL 主数据源配置(告警规则/事件等关系数据,Druid 连接池)
 * <p>
 * 因项目同时存在 MySQL 与 TDengine 两个 DataSource Bean,
 * 手写 tdengineDataSource 会抑制 spring.datasource.* 的自动配置,
 * 故此处显式声明 MySQL 主数据源并标注 @Primary,MyBatis-Plus 默认绑定它。
 * 用 DruidDataSource(@ConfigurationProperties 绑定 Druid 字段:url/username/password/initialSize 等)。
 * </p>
 *
 * @author ruoyi
 */
@Configuration
public class MysqlDataSourceConfig {

    @Primary
    @Bean(name = "mysqlDataSource", initMethod = "init", destroyMethod = "close")
    @ConfigurationProperties(prefix = "spring.datasource.druid")
    public DataSource mysqlDataSource() {
        // 直接构造 DruidDataSource,所有 setter 可被 @ConfigurationProperties 绑定
        // (url/username/password/driverClassName/initialSize/minIdle/maxActive/maxWait/filters 等)
        return new DruidDataSource();
    }
}
