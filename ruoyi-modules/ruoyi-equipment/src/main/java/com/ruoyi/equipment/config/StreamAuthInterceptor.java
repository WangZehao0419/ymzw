package com.ruoyi.equipment.config;

import com.ruoyi.common.core.constant.TokenConstants;
import com.ruoyi.common.core.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 流式推送端点鉴权拦截器
 * <p>
 * NDJSON 流式端点（/equipment/monitor/stream/**）是长连接，无法依赖常规
 * 请求-响应链路里的鉴权语义，这里在应用侧自行校验 Authorization 头中的
 * Bearer JWT，防止设备实时数据被未授权订阅。
 * </p>
 *
 * @author smartartisan
 */
@Component
public class StreamAuthInterceptor implements HandlerInterceptor {

    /**
     * 鉴权失败的统一 JSON 响应体，与 RuoYi AjaxResult 的 code/msg 结构对齐
     */
    private static final String UNAUTHORIZED_BODY = "{\"code\":401,\"msg\":\"Unauthorized\"}";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String authorization = request.getHeader("Authorization");

        // 令牌缺失或未携带 Bearer 前缀，直接拒绝
        if (authorization == null || !authorization.startsWith(TokenConstants.PREFIX)) {
            return reject(response);
        }

        // 去掉前缀后为空串同样是无效令牌
        String token = authorization.substring(TokenConstants.PREFIX.length());
        if (token.isBlank()) {
            return reject(response);
        }

        try {
            // 签名错误、过期、格式损坏等情况 parseToken 均抛异常，统一按未授权处理
            Claims claims = JwtUtils.parseToken(token);
            if (claims == null) {
                return reject(response);
            }
        } catch (Exception e) {
            return reject(response);
        }
        return true;
    }

    /**
     * 写出 401 响应并终止后续处理
     */
    private boolean reject(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(UNAUTHORIZED_BODY);
        return false;
    }
}
