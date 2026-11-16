package com.aies.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * A-07 邮箱验证码登录请求（UC-42）：username + 邮箱验证码，签发AT/RT同A-01。
 */
@Data
public class EmailLoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "邮箱验证码不能为空")
    private String emailCode;
}
