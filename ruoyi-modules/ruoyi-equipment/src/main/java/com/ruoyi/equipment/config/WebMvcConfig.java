package com.ruoyi.equipment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * <p>
 * 仅对流式推送端点注册鉴权拦截器，其余接口沿用网关侧统一鉴权链路。
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
        // NDJSON 流式端点为长连接，需在应用侧自行校验 Bearer JWT
        registry.addInterceptor(streamAuthInterceptor)
                .addPathPatterns("/equipment/monitor/stream/**");
    }
}
