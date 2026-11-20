package com.power.ai.biz.auth.dto;

import com.power.ai.common.security.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应体。
 *
 * 注意没有 password（连哈希都不给）——前端只需要 token 和展示用信息。
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    /** JWT 令牌（前端后续请求放 Authorization: Bearer <token>） */
    private String token;

    private String username;

    private String realName;

    private UserRole role;
}
