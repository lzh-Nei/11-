package com.group11.aievaluate.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.group11.aievaluate.common.BusinessException;
import com.group11.aievaluate.common.ErrorCode;
import com.group11.aievaluate.config.AuthProperties;
import com.group11.aievaluate.dto.CaptchaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 图形验证码：Hutool 生成图片，Redis 保存答案。
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String CAPTCHA_KEY_PREFIX = "auth:captcha:";

    private final StringRedisTemplate redis;
    private final AuthProperties authProperties;

    /**
     * 生成一张干扰线验证码，答案写入 Redis，key 为随机 UUID。
     */
    public CaptchaResponse generate() {
        AuthProperties.Captcha config = authProperties.getCaptcha();
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(
                config.getWidth(), config.getHeight(), config.getCodeCount(), config.getInterfereCount());

        String captchaKey = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(
                CAPTCHA_KEY_PREFIX + captchaKey,
                captcha.getCode(),
                Duration.ofSeconds(config.getExpireSeconds()));

        return new CaptchaResponse(captchaKey, captcha.getImageBase64Data(), config.getExpireSeconds());
    }

    /**
     * 校验并一次性消费验证码。
     *
     * <p>无论校验成功与否都从 Redis 删除，避免同一个验证码被重放；
     * 用户刷新验证码即可重新获取。</p>
     */
    public void verify(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaKey.isBlank()
                || captchaCode == null || captchaCode.isBlank()) {
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
        }

        String redisKey = CAPTCHA_KEY_PREFIX + captchaKey;
        String expected = redis.opsForValue().get(redisKey);
        // 先读后删，兼容 Redis 6.2 以下版本（GETDEL 需要 6.2+）
        redis.delete(redisKey);
        if (expected == null || !expected.equalsIgnoreCase(captchaCode.trim())) {
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
        }
    }
}
