package com.ruoyi.ai.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 服务间调用 RestTemplate 配置
 * <p>
 * 提供 @LoadBalanced RestTemplate：URL 中使用 Nacos 服务名（如 http://ruoyi-alert），
 * 由 Spring Cloud LoadBalancer 拦截解析为真实实例地址。
 * 超时约定与 PdmServerClient 对齐（连接 3s / 读取 10s），
 * 防止被调服务无响应时挂死诊断调用线程。
 * 注意：该 Bean 仅用于服务名调用，直连固定地址的场景（如 pdm-server）应自建实例，勿复用。
 * </p>
 *
 * @author smartartisan
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate balancedRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }
}
