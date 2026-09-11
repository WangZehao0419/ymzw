package com.ruoyi.mcp;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * MCP服务模块（云眸智维）
 * <p>
 * 将维保工单全生命周期接口封装为标准 MCP Server（Streamable HTTP，端点 /mcp），
 * 供 ruoyi-ai 对话智能体（MCP Client）及外部 MCP 客户端调用。
 * 工具实现在 com.ruoyi.mcp.tools.WorkOrderMcpTools，经 OpenFeign 下探
 * ruoyi-alert / ruoyi-equipment 的 @InnerAuth 内部接口。
 * </p>
 * <p>
 * 本服务无数据库：api 契约模块传递引入了 mybatis/jdbc 依赖，若不显式排除
 * DataSource/Mybatis 自动装配，会在无 spring.datasource 配置时启动失败。
 * </p>
 *
 * @author ruoyi
 */
@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, MybatisAutoConfiguration.class})
public class RuoYiMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuoYiMcpApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  MCP模块启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
