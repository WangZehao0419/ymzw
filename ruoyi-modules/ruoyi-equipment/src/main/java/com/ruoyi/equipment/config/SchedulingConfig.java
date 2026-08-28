package com.ruoyi.equipment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置
 * <p>
 * 为流式连接池的 @Scheduled 心跳广播提供调度能力。
 * 无此注解时 @Scheduled 方法不会被执行，长连接会被代理按空闲超时掐断。
 * </p>
 *
 * @author smartartisan
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
