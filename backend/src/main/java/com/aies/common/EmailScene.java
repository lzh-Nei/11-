package com.aies.common;

import lombok.Getter;

/**
 * 邮箱验证码使用场景（03-04 §2.3：key = auth:emailcode:{username}:{scene}）。
 *
 * <p>不同场景使用不同的 Redis key，登录验证码不能拿去注册/重置密码。</p>
 */
@Getter
public enum EmailScene {

    LOGIN("login", "邮箱登录"),
    REGISTER("register", "注册"),
    RESET_PASSWORD("reset", "重置密码");

    private final String key;
    private final String label;

    EmailScene(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
