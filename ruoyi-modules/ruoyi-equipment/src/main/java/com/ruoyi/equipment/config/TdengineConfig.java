package com.ruoyi.equipment.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.ruoyi.equipment.tdengine.TdSensorDataMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * TDengine 数据源与 MyBatis 独立工厂配置(次数据源,Druid 连接池)
 * <p>
 * REST JDBC(6041)+ Druid,Windows 免 taos.dll。
 * 直接 new DruidDataSource 让 @ConfigurationProperties 绑定所有 Druid setter
 * (url/username/password/driverClassName/initialSize/minIdle/maxActive 等)。
 * TDengine 走原生 MyBatis 工厂(手写 SQL,不用 BaseMapper),不注入
 * MybatisPlusInterceptor——分页插件是 MySQL 方言;工厂只加载 mapper/td/*.xml,
 * 主工厂 mapper-locations 只扫 mapper/*.xml 不递归子目录,两套 XML 天然隔离。
 * </p>
 * <p>
 * 为何不用常见的 "@MapperScan + @Bean SqlSessionFactory" 双工厂写法(本项目两处限制):
 * 1) MyBatis-Plus 自动配置的 sqlSessionFactory 带 @ConditionalOnMissingBean,
 *    用户 Bean 先于自动配置注册,一旦声明 SqlSessionFactory 类型的 Bean,
 *    主工厂会整体退避,所有 MySQL mapper 将按类型装配错绑到 TDengine 工厂;
 * 2) 框架 @EnableCustomConfig 的 @MapperScan("com.ruoyi.**.mapper") 通配扫描
 *    按 AUTOWIRE_BY_TYPE 装配,同名 Bean 注册竞争的跳过规则不可靠(实测被
 *    扫描器抢注绑到 MySQL 主工厂),故 TD mapper 接口放 mapper 包之外的
 *    tdengine 包从根源避开扫描。
 * 此处显式注册 MapperFactoryBean,工厂私有构建不进容器,
 * 对 MyBatis-Plus 主工厂零影响。
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

    /**
     * TDengine 专用 Mapper 注册(显式 MapperFactoryBean)
     * <p>
     * TD mapper 接口放在 mapper 包之外的 tdengine 包:框架 @MapperScan 通配
     * 扫描路径含 mapper 段即按类型装配绑 MySQL 主工厂(实测 CREATE STABLE
     * 发给了 MySQL 报语法错误);置于扫描范围外,本 Bean 是其唯一注册来源。
     * </p>
     */
    @Bean(name = "com.ruoyi.equipment.tdengine.TdSensorDataMapper")
    public MapperFactoryBean<TdSensorDataMapper> tdSensorDataMapper(
            @Qualifier("tdengineDataSource") DataSource dataSource) throws Exception {
        MapperFactoryBean<TdSensorDataMapper> factoryBean = new MapperFactoryBean<>(TdSensorDataMapper.class);
        factoryBean.setSqlSessionFactory(buildTdSqlSessionFactory(dataSource));
        return factoryBean;
    }

    /**
     * 私有构建 TDengine 专用 SqlSessionFactory:不注册为容器 Bean,
     * MyBatis-Plus 主工厂的 @ConditionalOnMissingBean 评估感知不到它的
     * 存在,主工厂行为与本配置完全解耦
     */
    private SqlSessionFactory buildTdSqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        // TD 的 XML 独立目录:主工厂 mapper-locations 只扫 mapper/*.xml 不递归子目录,天然隔离
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/td/*.xml"));
        return factoryBean.getObject();
    }
}
