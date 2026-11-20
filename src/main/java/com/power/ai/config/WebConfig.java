package com.power.ai.config;

import com.power.ai.common.security.LoginInterceptor;
import com.power.ai.common.security.RoleInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置：注册 0-6 的认证/授权拦截器。
 *
 * 保护策略（安全默认值）：
 * - /api/** 默认全部要求登录，只有 /api/auth/login 是白名单——
 *   新加的接口自动受保护，忘配权限不会裸奔；反过来（默认全放开）漏一个就是一个洞；
 * - knife4j（/doc.html、/webjars、/v3/api-docs）不在 /api 下，天然不受影响。
 *
 * 顺序：认证(1) 在前、授权(2) 在后——先确认"你是谁"，再判断"你能干什么"。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final RoleInterceptor roleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login")
                .order(1);
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login")
                .order(2);
    }
}
