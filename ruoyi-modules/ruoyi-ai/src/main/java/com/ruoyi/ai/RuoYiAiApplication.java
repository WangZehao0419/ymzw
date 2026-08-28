package com.ruoyi.ai;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI对话服务模块（云眸智维）
 * <p>
 * 提供AI智能体管理、ReactAgent流式对话、预测性告警等能力
 * 基座：Spring Boot 4 + Spring AI 2.0-M1 + Spring AI Alibaba 2.0-M1.1
 * </p>
 *
 * @author ruoyi
 */
@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication
public class RuoYiAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuoYiAiApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  AI模块启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
