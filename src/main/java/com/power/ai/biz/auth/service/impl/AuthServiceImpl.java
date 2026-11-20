package com.power.ai.biz.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.power.ai.biz.auth.dto.LoginResponse;
import com.power.ai.biz.auth.entity.SysUser;
import com.power.ai.biz.auth.mapper.SysUserMapper;
import com.power.ai.biz.auth.service.AuthService;
import com.power.ai.common.exception.BizException;
import com.power.ai.common.exception.ErrorCode;
import com.power.ai.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 登录鉴权实现。
 *
 * 两个安全细节（面试点）：
 * 1. BCrypt：密码加盐哈希。同一密码两次哈希结果都不同（盐随机），
 *    数据库泄露后攻击者无法用彩虹表反查；校验用 checkpw 而不是把明文再哈希一遍比较；
 * 2. "用户名或密码错误"不区分到底是哪个错——如果提示"用户名不存在"，
 *    攻击者就能用枚举法探测出哪些账号是真实存在的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(String username, String password) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null || !BCrypt.checkpw(password, user.getPassword())) {
            throw new BizException(ErrorCode.NOT_LOGIN.getCode(), "用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole(), user.getStationId());
        log.info("用户登录成功: username={}, role={}", username, user.getRole());
        return new LoginResponse(token, user.getUsername(), user.getRealName(), user.getRole());
    }
}
