package com.aies.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A-10 重置密码请求（UC-44）：邮箱验证码 + 新密码；成功作废该用户全部RT（S-10）。
 *
 * <p>密码强度（≥8位含字母与数字）在 Service 层校验→30013；账号不存在与验证码错误
 * 统一返回30009（S-13 防枚举）。</p>
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "邮箱验证码不能为空")
    private String emailCode;

    @NotBlank(message = "新密码不能为空")
    @Size(max = 32, message = "密码长度需为 8-32 位")
    private String newPassword;
}
