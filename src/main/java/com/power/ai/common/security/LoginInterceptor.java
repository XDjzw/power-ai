package com.power.ai.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.power.ai.common.exception.ErrorCode;
import com.power.ai.common.result.Result;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 认证拦截器：只回答一个问题——"你是谁？"
 *
 * 职责边界（与 RoleInterceptor 分工）：
 * - 本拦截器：解析 token → 放进 UserContext（认证）；
 * - RoleInterceptor：看 @RequireRole 注解 → 判断角色够不够（授权）。
 * 分开的理由：认证是每个请求都要做的，授权只有少数接口需要；混在一起时
 * 改一处权限规则就要动整个认证链路，职责不清。
 *
 * 拦截器 vs 过滤器的面试区分：
 * - Filter 是 Servlet 容器概念，先于 Spring MVC，只能拿到原始 request/response；
 * - Interceptor 在 Spring MVC 内，能拿到 handler（哪个 Controller 方法），
 *   所以做"看方法上的注解"这类事必须用拦截器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 浏览器跨域预检请求没有业务含义，直接放行（否则前端会拿不到 CORS 响应头）
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.NOT_LOGIN.getCode(), "未登录或缺少令牌");
            return false;
        }
        try {
            UserContext.set(jwtUtil.parse(header.substring(BEARER_PREFIX.length())));
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("token 解析失败: {}", e.getMessage());
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.NOT_LOGIN.getCode(), "登录已过期或令牌无效，请重新登录");
            return false;
        }
    }

    /** 请求结束必须清除 ThreadLocal（防 Tomcat 线程池复用导致用户串号，见 UserContext 注释） */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeError(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
    }
}
