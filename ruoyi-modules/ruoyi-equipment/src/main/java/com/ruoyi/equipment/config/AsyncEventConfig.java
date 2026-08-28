package com.ruoyi.equipment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步事件线程池配置
 * <p>
 * 为不同优先级的异步监听器提供独立线程池，实现故障隔离。
 * 推送线程池和 AI 推理线程池互不影响，一个池满不阻塞另一个。
 * </p>
 *
 * @author smartartisan
 */
@Configuration
public class AsyncEventConfig {

    /**
     * 实时推送线程池 (NDJSON 流式推送)
     * <p>
     * 推送操作轻量、瞬时，核心线程数较小即可满足。
     * 队列容量 100，超出时由调用线程执行 (CallerRunsPolicy)，天然背压。
     * </p>
     */
    @Bean("pushExecutor")
    public Executor pushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("push-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /**
     * AI 推理线程池 (Feign 调用 cloud-ai 预测告警)
     * <p>
     * AI 调用耗时较长（秒级），独立线程池避免阻塞推送和持久化。
     * 队列容量 200，应对传感器数据突发。
     * </p>
     */
    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-alert-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /**
     * TDengine 时序落库线程池
     * <p>
     * REST JDBC 写入(网络 IO 型),独立线程池与 MySQL 落库、MQ 转发隔离,
     * TDengine 慢或不可用时不拖累其他链路。
     * </p>
     */
    @Bean("tdengineExecutor")
    public Executor tdengineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("tdengine-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /**
     * RocketMQ 转发线程池
     * <p>
     * syncSend 为网络 IO,独立线程池隔离。broker 未部署阶段消息堆积在此队列,
     * 部署后自动恢复转发。
     * </p>
     */
    @Bean("mqExecutor")
    public Executor mqExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("mq-forward-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
