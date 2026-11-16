package com.aies.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis INCR+EXPIRE 原子计数限流器（04-04 S-03/S-12）。
 *
 * <p>首次计数时设置窗口过期，窗口内超过上限即拒绝。
 * 登录 10次/分/IP、图形码 20次/分/IP；超限统一 30010/429。</p>
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redis;

    /**
     * 尝试占用一次配额。
     *
     * @param key           计数键（建议带维度前缀，如 auth:limit:login:{ip}）
     * @param limit         窗口内允许次数
     * @param windowSeconds 窗口秒数
     * @return true=放行；false=超限
     */
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(windowSeconds));
        }
        return count == null || count <= limit;
    }

    /** 键是否存在（S-04 锁定检查） */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    /** 计数+1，首次计数时设置TTL（S-04 失败计数） */
    public Long increment(String key, int ttlSeconds) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(ttlSeconds));
        }
        return count;
    }

    /** 写入带TTL的键（S-04 锁定标记） */
    public void putWithTtl(String key, String value, int ttlSeconds) {
        redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    /** 删除键（S-04 成功即DEL计数） */
    public void delete(String key) {
        redis.delete(key);
    }
}
