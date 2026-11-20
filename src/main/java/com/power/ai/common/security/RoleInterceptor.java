package com.power.ai.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.power.ai.common.exception.ErrorCode;
import com.power.ai.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;

/**
 * 授权拦截器：只回答一个问题——"你的角色能不能干这件事？"
 *
 * 规则：
 * 1. 接口没标 @RequireRole → 登录即可访问（默认全部 /api/** 已要求登录）；
 * 2. 方法级注解优先于类级注解（类级用于"整个 Controller 统一角色"的场景）；
 * 3. 注解里多个角色是"或"的关系（满足其一即可）。
 *
 * 依赖 LoginInterceptor 先执行（WebConfig 里注册顺序 1、2），
 * 走到这里时 UserContext 里一定有值——身份在，才有资格谈权限。
 */
@Component
@RequiredArgsConstructor
public class RoleInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 非 Controller 请求（如静态资源）没有角色概念，直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRole annotation = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (annotation == null) {
            return true;
        }
        CurrentUser user = UserContext.get();
        for (UserRole allowed : annotation.value()) {
            if (user.getRole() == allowed) {
                return true;
            }
        }
        writeError(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.NO_AUTH.getCode(),
                "无权限：该操作需要角色 " + Arrays.toString(annotation.value()) + "，当前角色 " + user.getRole());
        return false;
    }

    private void writeError(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
    }
}
