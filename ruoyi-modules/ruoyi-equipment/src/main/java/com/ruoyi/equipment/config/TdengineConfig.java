package com.ruoyi.equipment.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * TDengine 数据源配置(次数据源,Druid 连接池)
 * <p>
 * REST JDBC(6041)+ Druid,Windows 免 taos.dll。
 * 直接 new DruidDataSource 让 @ConfigurationProperties 绑定所有 Druid setter
 * (url/username/password/driverClassName/initialSize/minIdle/maxActive 等)。
 * Druid 与 TDengine RESTfulDriver 兼容性 OK,用 Druid 字段配置 connection-test-query 等。
 * </p>
 *
 * @author smartartisan
 */
@Configuration
public class TdengineConfig {

    @Bean(name = "tdengineDataSource", initMethod = "init", destroyMethod = "close")
    @ConfigurationProperties(prefix = "spring.datasource.tdengine")
    public DataSource tdengineDataSource() {
        return new DruidDataSource();
    }
}
