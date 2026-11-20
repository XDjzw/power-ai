package com.power.ai.biz.auth.service;

import com.power.ai.biz.auth.dto.LoginResponse;

/**
 * 登录鉴权服务（0-6）。
 */
public interface AuthService {

    /**
     * 登录：校验用户名密码 → 签发 JWT。
     *
     * @throws com.power.ai.common.exception.BizException 用户名或密码错误（40100）
     */
    LoginResponse login(String username, String password);
}
