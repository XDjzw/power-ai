package com.power.ai.common.security;

/**
 * 用户上下文持有器（ThreadLocal 实现）。
 *
 * ThreadLocal 原理：每个线程一份独立副本。Tomcat 的线程池会复用线程——
 * 如果请求结束时不清除，A 用户请求结束后线程回到池子，B 用户拿到同一个线程，
 * 就会读到 A 的上下文（"串号"）。这是真实生产事故，所以：
 * LoginInterceptor.afterCompletion 里必须 clear，这里的方法故意设计成静态三件套，
 * 让"set 了就必须有人 clear"这件事一眼可见。
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /** 请求进入时由 LoginInterceptor 放入 */
    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /** 业务代码任意位置取当前登录人（如 /api/auth/me、阶段3 的"我的工单"） */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 请求结束时由 LoginInterceptor 清除，防线程池复用串号 */
    public static void clear() {
        HOLDER.remove();
    }
}
