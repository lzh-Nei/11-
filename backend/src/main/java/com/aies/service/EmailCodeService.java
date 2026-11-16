package com.aies.service;

import cn.hutool.core.util.RandomUtil;
import com.aies.common.BusinessException;
import com.aies.common.EmailScene;
import com.aies.common.ErrorCode;
import com.aies.config.AuthProperties;
import com.aies.dto.EmailCodeSendResponse;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 邮箱验证码（03-03 A-06/A-07/A-10 / 03-04 §2.3 / 04-04 S-11、S-12）。
 *
 * <p>Redis key 规范（03-04 §2.3）：验证码 {@code auth:emailcode:{username}:{scene}}；
 * 频控 S-12 双维度：单邮箱冷却 {@code email:send:{email}}（60s）+ 每小时上限
 * {@code email:hour:{email}}（5次/h）；IP 维度限流（A-06 5/min/IP）在 AuthService 层。</p>
 *
 * <p>发送通道：未配置 SMTP_HOST 时模拟发送（打日志，dev 联调，R-06 账号申请中）；
 * 已配置时真实 SMTP 投递，失败→40004 降级提示（UC-42 契约）。
 * S-11 一次性：校验通过即 DEL；未通过不 DEL 但 TTL 自然过期；连错≥5次作废。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    private static final String CODE_KEY_PREFIX = "auth:emailcode:";
    private static final String ATTEMPT_KEY_PREFIX = "auth:emailcode:attempts:";
    private static final String SEND_COOLDOWN_KEY = "email:send:";
    private static final String SEND_HOURLY_KEY = "email:hour:";

    private final StringRedisTemplate redis;
    private final AuthProperties authProperties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /** 模拟/真实通道判定：SMTP_HOST有值才走真实投递（compose恒注入空串，以hasText判定，不能依赖bean存在性） */
    @Value("${spring.mail.host:}")
    private String smtpHost;

    /** 发件人取 spring.mail.username（SMTP_USER）；未配置SMTP时不用该值 */
    @Value("${spring.mail.username:}")
    private String smtpFrom;

    /**
     * 发送 6 位数字验证码（S-12 频控 → 生成 → 存储 → 投递）。
     *
     * @param mockCode 仅模拟模式下按 expose-code 返回；真实SMTP通道恒为 null
     */
    public EmailCodeSendResponse send(String username, String email, EmailScene scene) {
        AuthProperties.Email config = authProperties.getEmail();

        // S-12：单邮箱冷却 60s（key维度 email:send:{email}）
        Boolean acquired = redis.opsForValue().setIfAbsent(
                SEND_COOLDOWN_KEY + email, "1", Duration.ofSeconds(config.getCooldownSeconds()));
        if (Boolean.FALSE.equals(acquired)) {
            throw new BusinessException(ErrorCode.CODE_SEND_TOO_FREQUENT);
        }
        // S-12：单邮箱每小时上限
        Long hourly = redis.opsForValue().increment(SEND_HOURLY_KEY + email);
        if (hourly != null && hourly == 1) {
            redis.expire(SEND_HOURLY_KEY + email, Duration.ofHours(1));
        }
        if (hourly != null && hourly > config.getHourlyLimit()) {
            throw new BusinessException(ErrorCode.CODE_SEND_TOO_FREQUENT);
        }

        String code = RandomUtil.randomNumbers(6);
        String codeKey = CODE_KEY_PREFIX + username + ":" + scene.getKey();
        redis.opsForValue().set(codeKey, code, Duration.ofSeconds(config.getExpireSeconds()));
        // 重新发送后，之前的错误次数作废
        redis.delete(ATTEMPT_KEY_PREFIX + username + ":" + scene.getKey());

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        boolean mock = !StringUtils.hasText(smtpHost) || sender == null;
        if (mock) {
            log.info("[模拟邮件] 收件={} 场景={} 验证码={} 有效期={}秒（SMTP_HOST未配置，R-06申请中）",
                    email, scene.getLabel(), code, config.getExpireSeconds());
        } else {
            try {
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setFrom(StringUtils.hasText(smtpFrom) ? smtpFrom : "aies@noreply.local");
                helper.setTo(email);
                helper.setSubject("AIES实训评价系统 验证码");
                helper.setText("您的验证码是 " + code + "，" + config.getExpireSeconds()
                        + " 秒内有效。请勿泄露给他人。");
                sender.send(message);
            } catch (Exception e) {
                // UC-42契约：SMTP不可用→40004降级提示（日志不含收件人PII以外的敏感信息）
                log.warn("SMTP投递失败: {}", e.getMessage());
                throw new BusinessException(ErrorCode.SMTP_UNAVAILABLE);
            }
        }

        String mockCode = mock && config.isExposeCode() ? code : null;
        return new EmailCodeSendResponse(username, config.getExpireSeconds(), mockCode);
    }

    /**
     * 校验并消费邮箱验证码（S-11 一次性：通过即DEL）。
     *
     * <p>错误次数达到上限后直接删除验证码，必须重新发送，防止暴力枚举 6 位数字。</p>
     */
    public void verify(String username, String code, EmailScene scene) {
        String codeKey = CODE_KEY_PREFIX + username + ":" + scene.getKey();
        String attemptKey = ATTEMPT_KEY_PREFIX + username + ":" + scene.getKey();

        String expected = redis.opsForValue().get(codeKey);
        if (expected == null) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        }

        if (code == null || !expected.equals(code.trim())) {
            recordFailure(codeKey, attemptKey);
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        }

        redis.delete(codeKey);
        redis.delete(attemptKey);
    }

    private void recordFailure(String codeKey, String attemptKey) {
        Long attempts = redis.opsForValue().increment(attemptKey);
        if (attempts == null) {
            return;
        }
        if (attempts == 1) {
            redis.expire(attemptKey, Duration.ofSeconds(authProperties.getEmail().getExpireSeconds()));
        }
        if (attempts >= authProperties.getEmail().getMaxAttempts()) {
            redis.delete(codeKey);
            redis.delete(attemptKey);
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID, "验证码错误次数过多，请重新获取");
        }
    }
}
