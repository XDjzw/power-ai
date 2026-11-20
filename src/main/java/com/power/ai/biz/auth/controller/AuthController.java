package com.power.ai.biz.auth.controller;

import com.power.ai.biz.auth.dto.LoginRequest;
import com.power.ai.biz.auth.dto.LoginResponse;
import com.power.ai.biz.auth.service.AuthService;
import com.power.ai.common.result.Result;
import com.power.ai.common.security.CurrentUser;
import com.power.ai.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * /api/auth/login 是拦截器白名单（WebConfig），其余全部要求登录。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录：校验用户名密码，签发 JWT（白名单接口） */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.ok(authService.login(request.getUsername(), request.getPassword()));
    }

    /**
     * 当前登录人信息。
     * 演示 UserContext 的用法——身份信息由拦截器放进 ThreadLocal，
     * 业务代码任何位置一行就能拿到；阶段 3 的"我的工单"就是这种用法。
     */
    @GetMapping("/me")
    public Result<CurrentUser> me() {
        return Result.ok(UserContext.get());
    }
}
