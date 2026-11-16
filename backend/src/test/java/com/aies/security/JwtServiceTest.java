package com.aies.security;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.mapper.RbacMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JwtService 单测（S-10/S-14 三例：签发回读 / 过期拒绝 / 篡改拒绝；M1覆盖率储备）。
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef-unit-test-secret-0123456789abcdef";

    private JwtService jwtService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.opsForSet()).thenReturn(setOps);

        RbacMapper rbacMapper = mock(RbacMapper.class);
        when(rbacMapper.selectRoleCodes(anyLong())).thenReturn(List.of("STUDENT"));

        jwtService = new JwtService(redis, rbacMapper);
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenMinutes", 120L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenDays", 7L);
        jwtService.init();
    }

    /** 例1 签发回读：AT可解析回同一主体与角色 */
    @Test
    void issueThenParseReturnsSamePrincipal() {
        JwtService.TokenPair pair = jwtService.issueTokens(3L, "STUDENT");

        LoginPrincipal principal = jwtService.parseAccessToken(pair.accessToken());
        assertThat(principal.userId()).isEqualTo(3L);
        assertThat(principal.role()).isEqualTo("STUDENT");
        assertThat(pair.refreshToken()).hasSize(64);
    }

    /** 例2 过期拒绝：exp早于当前 → 40101 */
    @Test
    void expiredTokenRejected() {
        ReflectionTestUtils.setField(jwtService, "accessTokenMinutes", -5L);
        JwtService.TokenPair pair = jwtService.issueTokens(3L, "STUDENT");

        assertThatThrownBy(() -> jwtService.parseAccessToken(pair.accessToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode()));
    }

    /** 例3 篡改拒绝：签名被改 → 40101 */
    @Test
    void tamperedTokenRejected() {
        JwtService.TokenPair pair = jwtService.issueTokens(3L, "STUDENT");
        String tampered = pair.accessToken().substring(0, pair.accessToken().length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parseAccessToken(tampered))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode()));
    }
}
