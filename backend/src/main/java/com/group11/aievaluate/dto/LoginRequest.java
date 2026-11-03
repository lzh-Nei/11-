package com.group11.aievaluate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 账号密码登录请求。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 获取图形验证码时返回的 captchaKey */
    @NotBlank(message = "验证码标识不能为空")
    private String captchaKey;

    @NotBlank(message = "图形验证码不能为空")
    private String captchaCode;
}
