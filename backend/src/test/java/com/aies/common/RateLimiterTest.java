package com.aies.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RateLimiter 单测（S-03/S-12 三例：首次计数设TTL / 窗口内放行 / 超限拒绝）。
 */
class RateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RateLimiter rateLimiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        rateLimiter = new RateLimiter(redis);
    }

    /** 例1 首次计数（INCR=1）时设置窗口TTL并放行 */
    @Test
    void firstIncrementSetsExpireAndPasses() {
        when(valueOps.increment("k")).thenReturn(1L);

        assertThat(rateLimiter.tryAcquire("k", 5, 60)).isTrue();
        verify(redis).expire("k", Duration.ofSeconds(60));
    }

    /** 例2 窗口内（INCR≤limit）放行且不重复设TTL */
    @Test
    void withinLimitPassesWithoutReexpire() {
        when(valueOps.increment("k")).thenReturn(3L);

        assertThat(rateLimiter.tryAcquire("k", 5, 60)).isTrue();
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    /** 例3 超限（INCR>limit）拒绝 */
    @Test
    void overLimitRejected() {
        when(valueOps.increment("k")).thenReturn(6L);

        assertThat(rateLimiter.tryAcquire("k", 5, 60)).isFalse();
    }
}
