package com.aies.security;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * JWT 认证过滤器（03-04 §2.1 步骤5/6）：Bearer AT 解析 → 黑名单校验 → 权限集装载 → SecurityContext。
 *
 * <p>权限集缓存 {@code auth:perm:{userId}} TTL 30min（LLD 行6；Y-02 变更广播失效在 UC-32 接入）。
 * 无凭证或凭证非法时不阻断链路，由 SecurityConfig 的 entryPoint 统一按 40101 应答。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PERM_CACHE_KEY = "auth:perm:";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final StringRedisTemplate redis;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            try {
                LoginPrincipal principal = jwtService.parseAccessToken(
                        header.substring(BEARER_PREFIX.length()));
                if (jwtService.isAccessTokenBlacklisted(principal.jti())) {
                    throw new BusinessException(ErrorCode.UNAUTHORIZED);
                }
                attachAuthentication(principal);
            } catch (BusinessException ignored) {
                // 匿名继续：由 entryPoint 统一 401 应答，避免过滤器内重复写响应
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private void attachAuthentication(LoginPrincipal principal) {
        List<GrantedAuthority> authorities = loadPermissions(principal.userId()).stream()
                .map(SimpleGrantedAuthority::new)
                .map(a -> (GrantedAuthority) a)
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** 权限集：Redis 缓存 TTL 30min，未命中回源五表RBAC */
    private List<String> loadPermissions(long userId) {
        String cacheKey = PERM_CACHE_KEY + userId;
        var cached = redis.opsForSet().members(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return List.copyOf(cached);
        }
        List<String> loaded = jwtService.permissionsOf(userId);
        if (!loaded.isEmpty()) {
            redis.opsForSet().add(cacheKey, loaded.toArray(new String[0]));
            redis.expire(cacheKey, Duration.ofMinutes(30));
        }
        return loaded;
    }
}
