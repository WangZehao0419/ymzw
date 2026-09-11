package com.ruoyi.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Client 懒初始化配置
 * <p>
 * 为什么需要：spring-ai-starter-mcp-client 2.0.0-M1 的自动配置（已 javap 源码级确认）存在两个事实：
 * 1) mcpSyncClients bean（List&lt;McpSyncClient&gt;）在创建期就会调用 McpSyncClient.initialize()
 *    （spring.ai.mcp.client.initialized 默认 true），对 Streamable-HTTP 连接发起 initialize 握手；
 * 2) 这些 @Bean 方法只标注了 @ConditionalOnProperty(type=SYNC)，没有任何 @Lazy，
 *    会被容器预实例化——即使业务代码全部用 @Lazy 注入 ToolCallbackProvider 也拦不住。
 * 因此 ruoyi-mcp（localhost:9213）不在线时，若不做处理，ruoyi-ai 启动会直接失败。
 * 这里参照 QdrantVectorStoreLazyConfig 的既有模式，把 MCP client 相关 bean 定义改标记为
 * 懒初始化，把连接失败推迟到首次实际使用（即首次对话触发工具装配）：
 * - 启动阶段：MCP 相关 bean 一律不创建，MCP Server 不在线不阻断启动；
 * - 运行阶段：首次对话时触发创建与 initialize，失败被 WorkOrderChatServiceImpl 的
 *   onErrorResume 捕获，输出 error NDJSON 行；MCP Server 恢复后下一次请求自动重试成功。
 * 消费方（WorkOrderChatServiceImpl）须配合以 @Lazy 注入 ToolCallbackProvider，
 * 否则注入点本身就会在启动期触发 bean 创建，懒初始化失效。
 * </p>
 *
 * @author smartartisan
 */
@Configuration
public class McpClientLazyConfig {

    /**
     * BeanFactoryPostProcessor：在单例预实例化之前，
     * 将 MCP client 相关 bean 定义标记为懒初始化。
     * 声明为 static：避免宿主配置类过早实例化导致 BFPP 注册时序问题。
     */
    @Bean
    public static BeanFactoryPostProcessor mcpClientLazyInitProcessor() {
        return beanFactory -> {
            if (beanFactory instanceof DefaultListableBeanFactory dlbf) {
                Logger logger = LoggerFactory.getLogger(McpClientLazyConfig.class);

                // 按 ToolCallbackProvider 类型匹配（含 MCP 自动配置的 mcpToolCallbacks/mcpAsyncToolCallbacks，
                // 也兜住其他同类型 provider；allowEagerInit=false 仅按 bean 定义解析类型，不触发实例化）
                for (String name : dlbf.getBeanNamesForType(ToolCallbackProvider.class, true, false)) {
                    dlbf.getBeanDefinition(name).setLazyInit(true);
                    logger.info("[MCP] ToolCallbackProvider bean [{}] 已标记为懒初始化（MCP Server 不在线不阻断启动）", name);
                }

                // 以下为 2.0.0-M1 自动配置 @Bean 方法名（名称固定，版本锁定）：
                // mcpSyncClients/mcpAsyncClients 创建期执行 initialize() 握手；
                // makeSyncClientsClosable/makeAsyncClientsClosable 创建期会解析依赖上述 client 列表。
                // 若 bean 不存在（如 type=SYNC 时 async 系列不注册），跳过不处理。
                for (String name : new String[]{
                        "mcpSyncClients", "makeSyncClientsClosable",
                        "mcpAsyncClients", "makeAsyncClientsClosable"}) {
                    if (dlbf.containsBeanDefinition(name)) {
                        BeanDefinition definition = dlbf.getBeanDefinition(name);
                        definition.setLazyInit(true);
                        logger.info("[MCP] bean [{}] 已标记为懒初始化（MCP Server 不在线不阻断启动）", name);
                    }
                }
            }
        };
    }
}
