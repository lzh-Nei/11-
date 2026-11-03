package com.group11.aievaluate.service;

import cn.hutool.core.util.RandomUtil;
import com.group11.aievaluate.common.BusinessException;
import com.group11.aievaluate.common.ErrorCode;
import com.group11.aievaluate.common.SmsScene;
import com.group11.aievaluate.config.AuthProperties;
import com.group11.aievaluate.dto.SmsSendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 短信验证码：发送（模拟）、重发冷却、错误次数限制、一次性校验。
 *
 * <p>Redis key 设计：</p>
 * <ul>
 *   <li>auth:sms:code:{scene}:{phone}    验证码答案，5 分钟过期</li>
 *   <li>auth:sms:cooldown:{scene}:{phone} 发送冷却，60 秒</li>
 *   <li>auth:sms:attempts:{scene}:{phone} 连续输错次数，最多 5 次</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private static final String CODE_KEY_PREFIX = "auth:sms:code:";
    private static final String COOLDOWN_KEY_PREFIX = "auth:sms:cooldown:";
    private static final String ATTEMPT_KEY_PREFIX = "auth:sms:attempts:";

    private final StringRedisTemplate redis;
    private final AuthProperties authProperties;

    /**
     * 发送 6 位数字验证码。
     *
     * <p>没有真实短信通道时只写日志；开启 expose-code 后同时把验证码返回给前端，
     * 方便课程演示和联调，生产环境必须关闭。</p>
     */
    public SmsSendResponse send(String phone, SmsScene scene) {
        AuthProperties.Sms config = authProperties.getSms();
        String cooldownKey = COOLDOWN_KEY_PREFIX + scene.getKey() + ":" + phone;

        Boolean acquired = redis.opsForValue().setIfAbsent(
                cooldownKey, "1", Duration.ofSeconds(config.getCooldownSeconds()));
        if (Boolean.FALSE.equals(acquired)) {
            throw new BusinessException(ErrorCode.SMS_TOO_FREQUENT);
        }

        String code = RandomUtil.randomNumbers(6);
        String codeKey = CODE_KEY_PREFIX + scene.getKey() + ":" + phone;
        redis.opsForValue().set(codeKey, code, Duration.ofSeconds(config.getExpireSeconds()));
        // 重新发送后，之前的错误次数作废
        redis.delete(ATTEMPT_KEY_PREFIX + scene.getKey() + ":" + phone);

        if (config.isMock()) {
            // 真实项目这里替换为短信服务商 SDK 调用
            log.info("[模拟短信] 手机号={} 场景={} 验证码={} 有效期={}秒",
                    phone, scene.getLabel(), code, config.getExpireSeconds());
        } else {
            log.warn("[短信通道未接入] 手机号={} 场景={}，请接入真实短信服务商后重试",
                    phone, scene.getLabel());
        }

        String mockCode = config.isMock() && config.isExposeCode() ? code : null;
        return new SmsSendResponse(phone, config.getExpireSeconds(), mockCode);
    }

    /**
     * 校验并消费短信验证码。
     *
     * <p>错误次数达到上限后直接删除验证码，必须重新发送，防止暴力枚举 6 位数字。</p>
     */
    public void verify(String phone, String code, SmsScene scene) {
        String codeKey = CODE_KEY_PREFIX + scene.getKey() + ":" + phone;
        String attemptKey = ATTEMPT_KEY_PREFIX + scene.getKey() + ":" + phone;

        String expected = redis.opsForValue().get(codeKey);
        if (expected == null) {
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID, "短信验证码已过期，请重新获取");
        }

        if (code == null || !expected.equals(code.trim())) {
            recordFailure(codeKey, attemptKey);
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID);
        }

        // 验证码一次性使用
        redis.delete(codeKey);
        redis.delete(attemptKey);
    }

    private void recordFailure(String codeKey, String attemptKey) {
        Long attempts = redis.opsForValue().increment(attemptKey);
        if (attempts == null) {
            return;
        }
        if (attempts == 1) {
            redis.expire(attemptKey, Duration.ofSeconds(authProperties.getSms().getExpireSeconds()));
        }
        if (attempts >= authProperties.getSms().getMaxAttempts()) {
            redis.delete(codeKey);
            redis.delete(attemptKey);
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID, "验证码错误次数过多，请重新获取");
        }
    }
}
