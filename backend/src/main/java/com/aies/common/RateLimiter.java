package com.aies.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis 计数限流器（04-04 S-03/S-12）。
 *
 * <p>计数与窗口过期由 Lua 脚本在 Redis 内一次性原子完成：
 * 首次 INCR 为 1 时立即 EXPIRE，避免“INCR 成功但 EXPIRE 未执行 → 键永不过期”的中间态；
 * 返回值来自同一次原子执行，并发请求不会超发。
 * 登录 10次/分/IP、图形码 20次/分/IP；超限统一 30010/429。</p>
 */
@Component
public class RateLimiter {

    /** 原子计数脚本：KEYS[1]=计数键，ARGV[1]=窗口秒数；返回本次自增后的计数 */
    static final DefaultRedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 尝试占用一次配额。
     *
     * @param key           计数键（建议带维度前缀，如 auth:limit:login:{ip}）
     * @param limit         窗口内允许次数
     * @param windowSeconds 窗口秒数
     * @return true=放行；false=超限
     */
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        Long count = executeIncrement(key, windowSeconds);
        return count == null || count <= limit;
    }

    /** 键是否存在（S-04 锁定检查） */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    /** 计数+1（原子设置首次 TTL），用于 S-04 登录失败计数 */
    public Long increment(String key, int ttlSeconds) {
        return executeIncrement(key, ttlSeconds);
    }

    /** 写入带TTL的键（S-04 锁定标记） */
    public void putWithTtl(String key, String value, int ttlSeconds) {
        redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    /** 删除键（S-04 成功即DEL计数） */
    public void delete(String key) {
        redis.delete(key);
    }

    /** 包级可见便于单测替换；生产路径始终走 Redis Lua 原子执行 */
    Long executeIncrement(String key, int ttlSeconds) {
        return redis.execute(INCR_WITH_TTL, List.of(key), String.valueOf(ttlSeconds));
    }
}
