package com.ruoyi.alert.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置
 * <p>
 * 为告警流连接池的心跳广播(@Scheduled)提供调度支持。
 * 说明:模块入口 RuoYiAlertApplication 上的 EnableCustomConfig 只开启了 @EnableAsync,
 * 未开启 @EnableScheduling,故此处单独定义;异步能力已由公共注解提供,无需重复开启。
 * </p>
 *
 * @author smartartisan
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
