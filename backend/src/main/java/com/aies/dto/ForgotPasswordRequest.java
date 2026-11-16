package com.aies.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * A-09 忘记密码请求（UC-44，S-13 防枚举）。
 *
 * <p>响应恒为 code=0 + 统一文案"如该账号存在，重置邮件已发送"，
 * 不泄露账号存在性与邮箱归属；重置验证码不回显（含dev模拟模式）。</p>
 */
@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "图形验证码ID不能为空")
    private String captchaId;

    @NotBlank(message = "图形验证码不能为空")
    private String captchaCode;
}
