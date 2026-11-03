package com.group11.aievaluate.common;

import lombok.Getter;

/**
 * 短信验证码使用场景。
 *
 * <p>不同场景使用不同的 Redis key，登录验证码不能拿去重置密码。</p>
 */
@Getter
public enum SmsScene {

    LOGIN("login", "登录"),
    RESET_PASSWORD("reset", "重置密码");

    private final String key;
    private final String label;

    SmsScene(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
