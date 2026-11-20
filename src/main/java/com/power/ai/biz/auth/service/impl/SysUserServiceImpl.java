package com.power.ai.biz.auth.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.power.ai.biz.auth.entity.SysUser;
import com.power.ai.biz.auth.mapper.SysUserMapper;
import com.power.ai.biz.auth.service.SysUserService;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
}
