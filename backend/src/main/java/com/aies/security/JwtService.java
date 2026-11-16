package com.aies.security;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.mapper.RbacMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Token 服务（03-04 §2.1 / 04-04 S-10）。
 *
 * <p>AT：JWT（HS512），claims 含 userId(sub) + role，2h；登出后按 jti 入 Redis 黑名单至自然过期。
 * RT：64位随机串（非JWT），存 {@code auth:rt:token:{token}} → userId（TTL 7d），
 * 并以 {@code auth:rt:user:{userId}} 集合作索引支持"改密作废全部RT"；旋转=验旧→发新→删旧。</p>
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String RT_TOKEN_KEY = "auth:rt:token:";
    private static final String RT_USER_KEY = "auth:rt:user:";
    private static final String AT_BLACKLIST_KEY = "auth:at:bl:";

    private final StringRedisTemplate redis;
    private final RbacMapper rbacMapper;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-minutes:120}")
    private long accessTokenMinutes;

    @Value("${jwt.refresh-token-days:7}")
    private long refreshTokenDays;

    private SecretKey signKey;
    private final SecureRandom random = new SecureRandom();

    /** 启动即校验密钥（S-14：零明文入库，.env 注入；HS512 需 ≥64 字节） */
    @PostConstruct
    void init() {
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 64) {
            throw new IllegalStateException(
                    "JWT密钥缺失或不足64字节：请在 .env 配置 JWT_SECRET（HS512要求≥64字节）");
        }
        this.signKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 登录成功后的双 Token 对（userId供A-03旋转后组装响应复用） */
    public record TokenPair(long userId, String accessToken, String refreshToken,
                            long accessExpiresInSeconds) {
    }

    /** 签发 AT + RT（登录 / 刷新共用） */
    public TokenPair issueTokens(long userId, String role) {
        long now = System.currentTimeMillis();
        long expiresAt = now + accessTokenMinutes * 60_000L;
        String accessToken = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(now))
                .expiration(new Date(expiresAt))
                .signWith(signKey)
                .compact();

        String refreshToken = newRefreshToken(userId);
        return new TokenPair(userId, accessToken, refreshToken, accessTokenMinutes * 60);
    }

    /**
     * 解析并校验 AT（签名+有效期），返回登录主体。
     *
     * @throws BusinessException 40101 签名不符 / 已过期 / 格式非法
     */
    public LoginPrincipal parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signKey).build()
                    .parseSignedClaims(token).getPayload();
            long userId = Long.parseLong(claims.getSubject());
            long expiresAt = claims.getExpiration().getTime();
            return new LoginPrincipal(userId, claims.getId(), token,
                    claims.get("role", String.class), expiresAt);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** AT 是否已在登出黑名单 */
    public boolean isAccessTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(AT_BLACKLIST_KEY + jti));
    }

    /** 登出：AT 入黑名单，TTL = 剩余有效期（S-10） */
    public void blacklistAccessToken(String jti, long expiresAtEpochMillis) {
        long remainingSeconds = (expiresAtEpochMillis - System.currentTimeMillis()) / 1000;
        if (remainingSeconds > 0) {
            redis.opsForValue().set(AT_BLACKLIST_KEY + jti, "1",
                    Duration.ofSeconds(remainingSeconds));
        }
    }

    /**
     * RT 旋转（A-03）：验旧 → 发新 AT+新RT → 删旧RT（防重放）。
     *
     * @throws BusinessException 40101 RT无效/已过期/已使用
     */
    public TokenPair rotateRefreshToken(String oldRefreshToken) {
        String tokenKey = RT_TOKEN_KEY + oldRefreshToken;
        String userIdRaw = redis.opsForValue().get(tokenKey);
        if (!StringUtils.hasText(userIdRaw)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        long userId = Long.parseLong(userIdRaw);
        redis.delete(tokenKey);
        redis.opsForSet().remove(RT_USER_KEY + userId, oldRefreshToken);

        return issueTokens(userId, primaryRole(userId));
    }

    /** 改密/重置成功 → 作废该用户全部 RT（S-10） */
    public void revokeAllRefreshTokens(long userId) {
        String userKey = RT_USER_KEY + userId;
        var members = redis.opsForSet().members(userKey);
        if (members != null) {
            members.forEach(token -> redis.delete(RT_TOKEN_KEY + token));
        }
        redis.delete(userKey);
    }

    /** 主角色：ADMIN &gt; TEACHER &gt; STUDENT（签发AT的role claim与登录响应共用） */
    public String primaryRole(long userId) {
        List<String> roles = rbacMapper.selectRoleCodes(userId);
        if (roles == null || roles.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return roles.stream()
                .filter(r -> List.of("ADMIN", "TEACHER", "STUDENT").contains(r))
                .findFirst()
                .orElse(roles.get(0));
    }

    /** 权限码全集（过滤器装载与缓存回源共用，@PreAuthorize 的 hasAuthority 即此） */
    public List<String> permissionsOf(long userId) {
        List<String> perms = rbacMapper.selectPermCodes(userId);
        return perms == null ? List.of() : perms;
    }

    private String newRefreshToken(long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        redis.opsForValue().set(RT_TOKEN_KEY + token, String.valueOf(userId),
                Duration.ofDays(refreshTokenDays));
        redis.opsForSet().add(RT_USER_KEY + userId, token);
        redis.expire(RT_USER_KEY + userId, Duration.ofDays(refreshTokenDays));
        return token;
    }
}
