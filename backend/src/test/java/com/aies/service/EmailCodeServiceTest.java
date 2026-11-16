package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.EmailScene;
import com.aies.common.ErrorCode;
import com.aies.config.AuthProperties;
import com.aies.dto.EmailCodeSendResponse;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmailCodeService 单测（S-11/S-12：冷却拦截 / 小时上限 / 错码计数 / 超限作废 / 正确码一次性消费 /
 * 发码成功落key+错次重置+小时TTL / 模拟通道mockCode回显与expose-code关闭 / 真实SMTP成功不回显 /
 * SMTP投递失败40004降级 / 过期与错码防枚举同文案）。
 */
class EmailCodeServiceTest {

    private static final String CODE_KEY = "auth:emailcode:newuser01:login";
    private static final String ATTEMPT_KEY = "auth:emailcode:attempts:newuser01:login";
    private static final String COOLDOWN_KEY = "email:send:test01@example.com";
    private static final String SEND_HOURLY_KEY = "email:hour:test01@example.com";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private AuthProperties authProperties;
    private ObjectProvider<JavaMailSender> mailSenderProvider;
    private EmailCodeService emailCodeService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);

        authProperties = new AuthProperties();

        mailSenderProvider = mock(ObjectProvider.class);
        lenient().when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        emailCodeService = new EmailCodeService(redis, authProperties, mailSenderProvider);
        ReflectionTestUtils.setField(emailCodeService, "smtpHost", "");
        ReflectionTestUtils.setField(emailCodeService, "smtpFrom", "");
    }

    /** 例1 冷却窗口内重发 → 30010（S-12：key维度 email:send:{email}） */
    @Test
    void sendBlockedDuringCooldown() {
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> emailCodeService.send("newuser01", "test01@example.com", EmailScene.LOGIN))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.CODE_SEND_TOO_FREQUENT.getCode()));
    }

    /** 例2 错码 → 计数+1并拒绝30009，验证码保留（未通过不DEL） */
    @Test
    void wrongCodeIncrementsAttemptAndRejected() {
        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        when(valueOps.increment(ATTEMPT_KEY)).thenReturn(1L);

        assertThatThrownBy(() -> emailCodeService.verify("newuser01", "000000", EmailScene.LOGIN))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.EMAIL_CODE_INVALID.getCode()));
        verify(redis, org.mockito.Mockito.never()).delete(CODE_KEY);
    }

    /** 例3 连错达上限 → 验证码作废（必须重发，防暴力枚举6位数字） */
    @Test
    void tooManyAttemptsInvalidatesCode() {
        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        when(valueOps.increment(ATTEMPT_KEY)).thenReturn(5L);

        assertThatThrownBy(() -> emailCodeService.verify("newuser01", "000000", EmailScene.LOGIN))
                .isInstanceOf(BusinessException.class);
        verify(redis).delete(CODE_KEY);
        verify(redis).delete(ATTEMPT_KEY);
    }

    /** 例4 正确码 → 一次性消费：通过即DEL（S-11 GETDEL语义） */
    @Test
    void correctCodeConsumedImmediately() {
        when(valueOps.get(CODE_KEY)).thenReturn("123456");

        emailCodeService.verify("newuser01", "123456", EmailScene.LOGIN);

        verify(redis).delete(CODE_KEY);
        verify(redis).delete(ATTEMPT_KEY);
    }

    /** 例5 发码成功：冷却key占位+小时计数首置TTL+codeKey按有效期写入+错次重置+模拟通道mockCode回显 */
    @Test
    void sendSuccessStoresCodeResetsAttemptsAndEchoesMockCode() {
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(SEND_HOURLY_KEY)).thenReturn(1L);

        EmailCodeSendResponse response =
                emailCodeService.send("newuser01", "test01@example.com", EmailScene.LOGIN);

        verify(valueOps).setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class));
        verify(redis).expire(SEND_HOURLY_KEY, Duration.ofHours(1));
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(CODE_KEY), codeCaptor.capture(), eq(Duration.ofSeconds(300)));
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        verify(redis).delete(ATTEMPT_KEY);
        assertThat(response.username()).isEqualTo("newuser01");
        assertThat(response.expireSeconds()).isEqualTo(300L);
        assertThat(response.mockCode()).isEqualTo(codeCaptor.getValue());
    }

    /** 例6 每小时上限命中（第6次>5）→ 30010，且不落验证码（S-12） */
    @Test
    void sendHourlyLimitExceededRejected() {
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(SEND_HOURLY_KEY)).thenReturn(6L);

        assertThatThrownBy(() -> emailCodeService.send("newuser01", "test01@example.com", EmailScene.LOGIN))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.CODE_SEND_TOO_FREQUENT.getCode()));
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    /** 例7 防枚举口径：验证码过期与错码拒绝同code同文案（不泄露验证码是否存在） */
    @Test
    void expiredCodeDeniedSameAsWrongCode() {
        when(valueOps.get(CODE_KEY)).thenReturn(null);
        BusinessException expired = verifyAndCatch("000000");

        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        when(valueOps.increment(ATTEMPT_KEY)).thenReturn(1L);
        BusinessException wrong = verifyAndCatch("000000");

        assertThat(expired.getCode()).isEqualTo(ErrorCode.EMAIL_CODE_INVALID.getCode());
        assertThat(wrong.getCode()).isEqualTo(expired.getCode());
        assertThat(expired.getMessage()).isEqualTo(wrong.getMessage());
    }

    /** 例8 SMTP已配置但投递失败 → 40004降级提示（UC-42契约） */
    @Test
    void smtpDeliveryFailureDegradesToUnavailable() {
        ReflectionTestUtils.setField(emailCodeService, "smtpHost", "smtp.example.com");
        JavaMailSender sender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(sender);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        org.mockito.Mockito.doThrow(new RuntimeException("connection refused"))
                .when(sender).send(any(MimeMessage.class));
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(SEND_HOURLY_KEY)).thenReturn(1L);

        assertThatThrownBy(() -> emailCodeService.send("newuser01", "test01@example.com", EmailScene.LOGIN))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.SMTP_UNAVAILABLE.getCode()));
    }

    /** 例9 真实SMTP通道成功：mockCode恒null（expose-code只作用于模拟通道，防验证码外泄） */
    @Test
    void realSmtpChannelNeverEchoesCode() {
        ReflectionTestUtils.setField(emailCodeService, "smtpHost", "smtp.example.com");
        JavaMailSender sender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(sender);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(SEND_HOURLY_KEY)).thenReturn(1L);

        EmailCodeSendResponse response =
                emailCodeService.send("newuser01", "test01@example.com", EmailScene.LOGIN);

        verify(sender).send(any(MimeMessage.class));
        assertThat(response.mockCode()).isNull();
    }

    /** 例10 模拟通道但expose-code关闭（生产口径）→ mockCode=null */
    @Test
    void mockChannelWithoutExposeCodeReturnsNull() {
        authProperties.getEmail().setExposeCode(false);
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(SEND_HOURLY_KEY)).thenReturn(1L);

        EmailCodeSendResponse response =
                emailCodeService.send("newuser01", "test01@example.com", EmailScene.LOGIN);

        assertThat(response.mockCode()).isNull();
    }

    /** 捕获校验失败抛出的业务异常（防枚举口径断言用） */
    private BusinessException verifyAndCatch(String code) {
        try {
            emailCodeService.verify("newuser01", code, EmailScene.LOGIN);
            throw new IllegalStateException("预期抛出BusinessException");
        } catch (BusinessException e) {
            return e;
        }
    }
}
