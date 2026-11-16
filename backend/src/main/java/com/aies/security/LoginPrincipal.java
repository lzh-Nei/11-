package com.aies.security;

/**
 * 已认证主体（JWT解析产物），由 {@link JwtAuthenticationFilter} 放入 SecurityContext。
 *
 * @param userId          用户ID（AT subject）
 * @param jti             AT唯一标识（登出黑名单键）
 * @param rawToken        原始AT（登出黑名单计算剩余TTL用）
 * @param role            主角色（AT role claim）
 * @param expiresAtMillis AT过期时间戳（毫秒）
 */
public record LoginPrincipal(long userId,
                             String jti,
                             String rawToken,
                             String role,
                             long expiresAtMillis) {
}
