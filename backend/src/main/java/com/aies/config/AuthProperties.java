package com.aies.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证相关可配置项，对应 application.yml 中的 auth.*。
 *
 * <p>D-19：SMS 配置段已随脚手架死代码一并清除，验证码通道唯一为邮箱（Email）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private Captcha captcha = new Captcha();
    private Email email = new Email();

    @Data
    public static class Captcha {
        /** 图形验证码有效期（秒） */
        private long expireSeconds = 300;
        private int width = 120;
        private int height = 40;
        private int codeCount = 4;
        private int interfereCount = 20;
    }

    /**
     * 邮箱验证码（A-06/A-07/A-08/A-10，UC-42/43/44；基线认证通道=邮箱SMTP）。
     */
    @Data
    public static class Email {
        /** 邮箱验证码有效期（秒） */
        private long expireSeconds = 300;
        /** 同一邮箱同一场景的重发冷却（秒，S-12） */
        private long cooldownSeconds = 60;
        /** 单邮箱每小时发送上限（S-12） */
        private int hourlyLimit = 5;
        /** 最多允许输错次数，超过后验证码作废 */
        private int maxAttempts = 5;
        /** 模拟发送（未配置SMTP_HOST）时是否在接口响应中返回验证码，仅dev联调，生产关闭 */
        private boolean exposeCode = true;
    }
}
