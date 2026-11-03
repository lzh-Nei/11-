package com.group11.aievaluate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证相关可配置项，对应 application.yml 中的 auth.*。
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private Captcha captcha = new Captcha();
    private Sms sms = new Sms();

    @Data
    public static class Captcha {
        /** 图形验证码有效期（秒） */
        private long expireSeconds = 300;
        private int width = 120;
        private int height = 40;
        private int codeCount = 4;
        private int interfereCount = 20;
    }

    @Data
    public static class Sms {
        /** 短信验证码有效期（秒） */
        private long expireSeconds = 300;
        /** 同一手机号同一场景的重发间隔（秒） */
        private long cooldownSeconds = 60;
        /** 最多允许输错次数，超过后验证码作废 */
        private int maxAttempts = 5;
        /** 是否模拟发送（真实项目接入短信服务商后改为 false） */
        private boolean mock = true;
        /** 是否在接口响应中返回验证码，仅用于课程演示/联调，生产环境务必关闭 */
        private boolean exposeCode = true;
    }
}
