package com.ruoyi.alert.config;

import com.ruoyi.common.core.constant.TokenConstants;
import com.ruoyi.common.core.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 告警流接口鉴权拦截器
 * <p>
 * 为什么单独鉴权:常规 REST 接口由网关 AuthFilter 统一校验 JWT,
 * 而 /api/alert-events/stream 是长连接(可能直连服务或经特殊代理),
 * 无法依赖网关的请求级过滤,故在本模块内用与网关一致的规则自查:
 * Authorization: Bearer <token> + JwtUtils.parseToken 静态解析(密钥内置
 * 于 TokenConstants.SECRET,与网关同源,无需任何配置注入)。
 * </p>
 *
 * @author smartartisan
 */
@Component
public class StreamAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (checkToken(request)) {
            return true;
        }
        // 鉴权失败:返回 401 与 JSON 错误体,格式与 RuoYi 前端错误处理约定一致
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"Unauthorized\"}");
        return false;
    }

    /**
     * 校验 Authorization 头:存在、Bearer 前缀、token 非空、JwtUtils 可解析
     */
    private boolean checkToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(TokenConstants.PREFIX)) {
            return false;
        }
        String token = authorization.substring(TokenConstants.PREFIX.length());
        if (token.isEmpty()) {
            return false;
        }
        try {
            Claims claims = JwtUtils.parseToken(token);
            // parseToken 失败通常直接抛异常,这里防御性判空兜底
            return claims != null;
        } catch (Exception e) {
            return false;
        }
    }
}
