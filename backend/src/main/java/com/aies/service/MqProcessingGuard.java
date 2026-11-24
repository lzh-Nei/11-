package com.aies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * MQ 消费单飞锁：同一提交在锁有效期内只允许一个实例处理。
 *
 * <p>用于抑制“至少一次投递 + 超时补偿重投”叠加时的重复解析与重复 LLM 计费。
 * 锁随 TTL 自动释放；解锁前比对 token，避免误删其它实例重新获取的锁。
 * Redis 不可用时降级为无锁执行，优先保证消息不积压。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqProcessingGuard {

    private static final String PREFIX = "aies:mq:lock:";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    /** 获取锁：成功返回 token，未获取（已有实例处理中）返回 null */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(PREFIX + key, token, ttl);
            return Boolean.TRUE.equals(acquired) ? token : null;
        } catch (RuntimeException e) {
            // Redis 故障不阻断消费：降级无锁执行，保证可用性优先
            log.warn("MQ处理锁获取失败，降级无锁执行: key={} err={}", key, e.getMessage());
            return token;
        }
    }

    /** 释放锁（仅当 token 匹配） */
    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(PREFIX + key), token);
        } catch (RuntimeException e) {
            log.warn("MQ处理锁释放失败(TTL将自动过期): key={} err={}", key, e.getMessage());
        }
    }
}
