package com.aies.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.common.RateLimiter;
import com.aies.config.AuthProperties;
import com.aies.dto.CaptchaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 图形验证码（03-04 §2.3 / 04-04 S-03、S-11）。
 *
 * <p>key 规范 {@code auth:captcha:{captchaId}}，TTL 5分钟；
 * 校验通过立即DEL（Lua原子"比对即删"，杜绝并发重放窗口）；未通过不DEL，靠TTL自然过期。</p>
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String CAPTCHA_KEY_PREFIX = "auth:captcha:";
    private static final String LIMIT_KEY_PREFIX = "auth:limit:captcha:";
    private static final int LIMIT_PER_MINUTE = 20;

    /**
     * S-11：比对成功才DEL（原子），失败保留至TTL过期。
     */
    private static final DefaultRedisScript<Long> VERIFY_AND_CONSUME = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) "
            + "if v and string.upper(v) == string.upper(ARGV[1]) then "
            + "redis.call('DEL', KEYS[1]) return 1 else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final AuthProperties authProperties;
    private final RateLimiter rateLimiter;

    /**
     * 生成验证码（A-04）：单IP限流 20次/分钟（S-03），答案写入 Redis。
     */
    public CaptchaResponse generate(String clientIp) {
        if (!rateLimiter.tryAcquire(LIMIT_KEY_PREFIX + clientIp, LIMIT_PER_MINUTE, 60)) {
            throw new BusinessException(ErrorCode.CODE_SEND_TOO_FREQUENT);
        }

        AuthProperties.Captcha config = authProperties.getCaptcha();
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(
                config.getWidth(), config.getHeight(), config.getCodeCount(),
                config.getInterfereCount());

        String captchaId = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(
                CAPTCHA_KEY_PREFIX + captchaId,
                captcha.getCode(),
                Duration.ofSeconds(config.getExpireSeconds()));

        return new CaptchaResponse(captchaId, captcha.getImageBase64Data(),
                config.getExpireSeconds());
    }

    /**
     * 校验并一次性消费验证码（Lua原子：成功即DEL；失败不DEL，TTL自然过期）。
     */
    public void verify(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank()
                || captchaCode == null || captchaCode.isBlank()) {
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
        }

        String redisKey = CAPTCHA_KEY_PREFIX + captchaId;
        Long matched = redis.execute(VERIFY_AND_CONSUME,
                List.of(redisKey), captchaCode.trim());
        if (matched == null || matched != 1L) {
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
        }
    }
}
