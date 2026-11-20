package com.power.ai.common.security;

/**
 * 系统角色。
 *
 * 为什么放 common/security 而不是 biz/auth 下：
 * 角色是横切概念——SysUser 实体、JwtUtil 签发、@RequireRole 注解都要引用它。
 * 依赖方向约定 biz → common，绝不能反过来；放 biz 下 common 就得反向依赖，层次就乱了。
 *
 * MyBatis-Plus 默认按枚举名映射数据库字符串（与 OrderStatus 一致），DB 里存 "ADMIN" 等。
 */
public enum UserRole {

    /** 管理员：电站信息维护 */
    ADMIN,

    /** 电站负责人：接单 / 完工 */
    MANAGER,

    /** 运营：创建工单 / 看考核告警 */
    OPERATOR
}
