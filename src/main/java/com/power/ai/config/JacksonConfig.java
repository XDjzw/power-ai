package com.power.ai.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局序列化配置。
 *
 * 核心问题：雪花 ID（19 位）超过 JS Number 安全整数上限 2^53-1（16 位）。
 * 后端把 Long 原样输出成 JSON 数字，浏览器 JSON.parse 时就丢精度——
 * 实测 9 个电站的 ID 在 JS 里只剩 2 个值：下拉框塌缩成 1 个选项、按 ID 查询查空。
 *
 * 解法（业界标准）：Long 一律序列化成字符串，JS 端天然无损；
 * 前端把字符串原样带回，Spring 参数绑定（@PathVariable/@RequestParam/@RequestBody）
 * 会自动把 "2122..." 转回 Long，业务代码零感知。
 *
 * 代价：前端凡是"当数字用"的 Long（如分页 total）要 Number() 转一下，
 * 而"当主键用"的 Long 反而更安全了（字符串比较永远精确）。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        // Long.TYPE 管 long 基本类型（如 MyBatis-Plus Page.total），Long.class 管包装类型（实体 id）
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
