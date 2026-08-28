package com.ruoyi.alert.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * <p>
 * 注册告警流鉴权拦截器,只拦截 /api/alert-events/stream 一个路径:
 * 其余 REST 接口维持网关统一鉴权,模块内不重复校验,避免双重鉴权带来的
 * 维护成本与行为不一致。
 * </p>
 *
 * @author smartartisan
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final StreamAuthInterceptor streamAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(streamAuthInterceptor)
                .addPathPatterns("/api/alert-events/stream");
    }
}
