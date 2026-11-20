package com.power.ai.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析工具（jjwt 0.12.x API）。
 *
 * 面试常问的三个点，都在实现里对应：
 * 1. 无状态：token 自带身份信息（sub + claims），服务端不存 session，
 *    多实例部署时任何一台都能校验——这就是 JWT 适合分布式的原因；
 * 2. 防篡改：HS256 对称签名，改了任何内容签名就对不上；
 * 3. 过期：expiration 由服务端校验，即使 token 泄露也有止损窗口
 *    （无状态的代价：签发后无法主动"踢下线"，长会话场景要加 Redis 黑名单，阶段 3 可展开）。
 *
 * 注意：jjwt 0.12 相比 0.9/0.11 的 API 变化很大（setSubject→subject、
 * parserBuilder→parser().verifyWith()），网上大部分旧教程已不适用。
 */
@Component
public class JwtUtil {

    /** HS256 签名密钥，至少 32 字节（jjwt 强制）。生产环境必须走环境变量/配置中心注入 */
    @Value("${app.jwt.secret}")
    private String secret;

    /** token 有效期（小时） */
    @Value("${app.jwt.expire-hours:24}")
    private long expireHours;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 token。
     * 字段安排：sub 放 userId（JWT 规范里 sub 就是"主体唯一标识"）；
     * username/role/stationId 是业务身份，放自定义 claim。
     */
    public String generate(Long userId, String username, UserRole role, Long stationId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role.name())
                .claim("stationId", stationId == null ? "" : String.valueOf(stationId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireHours * 3600_000L))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验 token（签名 + 过期时间）。
     * 任何不合法（篡改/过期/格式错）都抛 JwtException，由拦截器统一转 401。
     */
    public CurrentUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String stationIdStr = claims.get("stationId", String.class);
        Long stationId = (stationIdStr == null || stationIdStr.isEmpty()) ? null : Long.valueOf(stationIdStr);
        return new CurrentUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                UserRole.valueOf(claims.get("role", String.class)),
                stationId);
    }
}
