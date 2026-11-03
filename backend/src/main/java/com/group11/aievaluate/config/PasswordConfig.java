package com.group11.aievaluate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码慢哈希配置。
 *
 * <p>BCrypt 每次 encode 都会生成随机盐，密文形如
 * {@code $2a$10$...}；数据库中只保存密文，绝不保存明文。</p>
 */
@Configuration
public class PasswordConfig {

    /** strength = 10（2^10 轮），兼顾安全性与登录耗时 */
    private static final int BCRYPT_STRENGTH = 10;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
