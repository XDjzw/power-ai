package com.power.ai.biz.auth.dto;

import lombok.Data;

/**
 * 登录请求体。
 *
 * 为什么单独建 DTO 而不是直接收 SysUser 实体：
 * 登录只需要两个字段，用实体接收的话攻击者可以顺手提交 role/stationId 等字段，
 * 万一哪段代码偷懒直接 save 实体就出事了——"接口只暴露业务需要的最小字段集"是安全习惯。
 */
@Data
public class LoginRequest {

    /** 登录账号 */
    private String username;

    /** 明文密码（只在这一次请求中出现，服务端校验后即丢弃，不落任何日志） */
    private String password;
}
