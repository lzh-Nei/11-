package com.aies.security;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.mapper.RbacMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * JwtAuthenticationFilter 单测（03-04 §2.1 步骤5/6：有效AT装配LoginPrincipal与权限集 /
 * 权限缓存未命回源写缓存、空集不写缓存 / 黑名单与解析失败（过期·篡改）清上下文匿名放行 /
 * 无头与非Bearer不触碰JWT；40101应答体由entryPoint统一，过滤器自身不写响应）。
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef-unit-test-secret-0123456789abcdef";
    private static final String PERM_KEY = "auth:perm:3";

    private JwtService jwtService;
    private StringRedisTemplate redis;
    private SetOperations<String, String> setOps;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jwtService = mock(JwtService.class);
        redis = mock(StringRedisTemplate.class);
        setOps = mock(SetOperations.class);
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        filter = new JwtAuthenticationFilter(jwtService, redis);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 例1 有效AT：SecurityContext装配LoginPrincipal本体+权限集authorities（Redis缓存命中路径） */
    @Test
    void validTokenAttachesPrincipalWithCachedPermissions() throws Exception {
        LoginPrincipal principal = new LoginPrincipal(3L, "jti-1", "raw-token", "STUDENT", Long.MAX_VALUE);
        when(jwtService.parseAccessToken("raw-token")).thenReturn(principal);
        when(jwtService.isAccessTokenBlacklisted("jti-1")).thenReturn(false);
        when(setOps.members(PERM_KEY)).thenReturn(Set.of("task:create", "task:submit"));
        request.addHeader("Authorization", "Bearer raw-token");

        filter.doFilter(request, response, chain);

        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isSameAs(principal);
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("task:create", "task:submit");
        verify(chain).doFilter(request, response);
    }

    /** 例2 权限缓存未命中：回源RBAC并写缓存（key=auth:perm:{userId}，TTL 30min） */
    @Test
    void permissionCacheMissLoadsFromRbacAndWritesCache() throws Exception {
        LoginPrincipal principal = new LoginPrincipal(3L, "jti-1", "raw-token", "TEACHER", Long.MAX_VALUE);
        when(jwtService.parseAccessToken("raw-token")).thenReturn(principal);
        when(jwtService.isAccessTokenBlacklisted("jti-1")).thenReturn(false);
        when(setOps.members(PERM_KEY)).thenReturn(Set.of());
        when(jwtService.permissionsOf(3L)).thenReturn(List.of("task:create", "task:submit"));
        request.addHeader("Authorization", "Bearer raw-token");

        filter.doFilter(request, response, chain);

        verify(setOps).add(PERM_KEY, "task:create", "task:submit");
        verify(redis).expire(PERM_KEY, Duration.ofMinutes(30));
        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("task:create", "task:submit");
    }

    /** 例3 RBAC返回空权限集：仍装配认证（空authorities），但不写缓存 */
    @Test
    void emptyPermissionsSkipCacheWriteButStillAuthenticate() throws Exception {
        LoginPrincipal principal = new LoginPrincipal(3L, "jti-1", "raw-token", "STUDENT", Long.MAX_VALUE);
        when(jwtService.parseAccessToken("raw-token")).thenReturn(principal);
        when(jwtService.isAccessTokenBlacklisted("jti-1")).thenReturn(false);
        when(setOps.members(PERM_KEY)).thenReturn(Set.of());
        when(jwtService.permissionsOf(3L)).thenReturn(List.of());
        request.addHeader("Authorization", "Bearer raw-token");

        filter.doFilter(request, response, chain);

        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).isEmpty();
        verify(setOps, never()).add(anyString(), any(String[].class));
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    /** 例4 AT已在登出黑名单：清上下文匿名放行（不写40101响应体，由entryPoint统一应答） */
    @Test
    void blacklistedTokenClearsContextAndContinuesChain() throws Exception {
        LoginPrincipal principal = new LoginPrincipal(3L, "jti-1", "raw-token", "STUDENT", Long.MAX_VALUE);
        when(jwtService.parseAccessToken("raw-token")).thenReturn(principal);
        when(jwtService.isAccessTokenBlacklisted("jti-1")).thenReturn(true);
        request.addHeader("Authorization", "Bearer raw-token");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getContentAsString()).isEmpty();
        verify(jwtService, never()).permissionsOf(anyLong());
        verify(chain).doFilter(request, response);
    }

    /** 例5 解析失败（签名不符/过期/格式非法统一40101）：清上下文匿名放行且不再查黑名单 */
    @Test
    void unparseableTokenClearsContextAndContinuesChain() throws Exception {
        when(jwtService.parseAccessToken("bad-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));
        request.addHeader("Authorization", "Bearer bad-token");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getContentAsString()).isEmpty();
        verify(jwtService, never()).isAccessTokenBlacklisted(anyString());
        verify(chain).doFilter(request, response);
    }

    /** 例6 端到端（真实JwtService）：过期AT解析→40101异常被吞掉→匿名继续链路，不写响应体 */
    @Test
    void expiredRealTokenStaysAnonymous() throws Exception {
        JwtService real = realJwtService(-5L);
        String expiredToken = real.issueTokens(3L, "STUDENT").accessToken();
        request.addHeader("Authorization", "Bearer " + expiredToken);

        new JwtAuthenticationFilter(real, mock(StringRedisTemplate.class))
                .doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getContentAsString()).isEmpty();
        verify(chain).doFilter(request, response);
    }

    /** 例7 端到端（真实JwtService）：签名被篡改AT→40101→匿名继续（不向调用方泄露失败细节） */
    @Test
    void forgedRealTokenStaysAnonymous() throws Exception {
        JwtService real = realJwtService(120L);
        String token = real.issueTokens(3L, "STUDENT").accessToken();
        String forged = token.substring(0, token.length() - 2) + "xx";
        request.addHeader("Authorization", "Bearer " + forged);

        new JwtAuthenticationFilter(real, mock(StringRedisTemplate.class))
                .doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    /** 例8 无Authorization头：匿名放行，完全不触碰JWT解析与权限装载 */
    @Test
    void missingHeaderPassesAnonymously() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
        verify(setOps, never()).members(anyString());
        verify(chain).doFilter(request, response);
    }

    /** 例9 非Bearer头（如Basic）：不解析直接匿名放行 */
    @Test
    void nonBearerHeaderPassesAnonymously() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
        verify(setOps, never()).members(anyString());
        verify(chain).doFilter(request, response);
    }

    /** 构造真实JwtService（端到端过期/篡改用；密钥66字节满足HS512≥64字节启动校验） */
    @SuppressWarnings("unchecked")
    private JwtService realJwtService(long accessMinutes) {
        StringRedisTemplate realRedis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        SetOperations<String, String> realSetOps = mock(SetOperations.class);
        lenient().when(realRedis.opsForValue()).thenReturn(valueOps);
        lenient().when(realRedis.opsForSet()).thenReturn(realSetOps);
        JwtService real = new JwtService(realRedis, mock(RbacMapper.class));
        ReflectionTestUtils.setField(real, "secret", SECRET);
        ReflectionTestUtils.setField(real, "accessTokenMinutes", accessMinutes);
        ReflectionTestUtils.setField(real, "refreshTokenDays", 7L);
        real.init();
        return real;
    }
}
