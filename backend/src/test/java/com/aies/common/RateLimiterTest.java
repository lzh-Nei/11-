package com.aies.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * RateLimiter 单测（S-03/S-12）：窗口内放行、超限拒绝、失败计数、Lua 原子脚本。
 */
class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = spy(new RateLimiter(mock(StringRedisTemplate.class)));
    }

    /** 首次计数（INCR=1）在阈值内放行 */
    @Test
    void firstIncrementPasses() {
        doReturn(1L).when(rateLimiter).executeIncrement("k", 60);

        assertThat(rateLimiter.tryAcquire("k", 5, 60)).isTrue();
    }

    /** 窗口内（INCR≤limit）放行 */
    @Test
    void withinLimitPasses() {
        doReturn(3L).when(rateLimiter).executeIncrement("k", 60);

        assertThat(rateLimiter.tryAcquire("k", 5, 60)).isTrue();
    }

    /** 超限（INCR>limit）拒绝 */
    @Test
    void overLimitRejected() {
        doReturn(6L).when(rateLimiter).executeIncrement("k", 60);

        assertThat(rateLimiter.tryAcquire("k", 5, 60)).isFalse();
    }

    /** S-04 失败计数直接返回自增结果 */
    @Test
    void incrementReturnsAtomicCounter() {
        doReturn(4L).when(rateLimiter).executeIncrement("k", 900);

        assertThat(rateLimiter.increment("k", 900)).isEqualTo(4L);
    }

    /** 计数与过期必须在同一段 Lua 内：仅首次计数设置 TTL */
    @Test
    void scriptSetsTtlOnFirstIncrementOnly() {
        assertThat(RateLimiter.INCR_WITH_TTL.getScriptAsString())
                .contains("INCR", "EXPIRE", "c == 1");
    }
}
