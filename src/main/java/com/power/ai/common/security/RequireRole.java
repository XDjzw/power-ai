package com.power.ai.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色鉴权注解：标注哪些角色可以访问该接口。
 *
 * 用法：
 * - 方法级：@RequireRole(UserRole.ADMIN)  仅管理员
 * - 类级：整个 Controller 统一要求（方法级优先于类级，见 RoleInterceptor）
 * - 多角色：@RequireRole({UserRole.MANAGER, UserRole.OPERATOR})  满足其一即可
 *
 * 为什么不直接用 Spring Security 的 @PreAuthorize：教学项目要能讲清楚鉴权原理，
 * 自研注解 + 拦截器是"最小闭环"；简历上可以写"基于拦截器自研轻量鉴权，
 * 避免引入 Spring Security 全套的重量级配置"。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色（满足其一即可） */
    UserRole[] value();
}
