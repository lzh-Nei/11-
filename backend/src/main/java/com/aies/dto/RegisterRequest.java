package com.aies.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A-08 注册请求（UC-43，D-19后邮箱通道口径）。
 *
 * <p>phone 不在请求体内（03-02 sys_user.phone 可空，展示脱敏；UC-43 契约无手机号）。
 * role 不在请求体中：五表RBAC标准下新注册用户一律绑定内置 STUDENT 角色，防止伪造角色提权。
 * 邮箱格式→30005、密码强度→30013 由 Service 层校验（Bean Validation 只能给30000）。</p>
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,20}$", message = "学号需为 4-20 位字母、数字或下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度需为 8-32 位")
    private String password;

    @Size(max = 32, message = "姓名不能超过 32 个字符")
    private String realName;

    /** 绑定邮箱（登录/找回通道，A-06/A-07/A-09前置） */
    @NotBlank(message = "邮箱不能为空")
    private String email;

    /** 班级码（class.class_code，无效→30011） */
    @NotBlank(message = "班级码不能为空")
    private String classCode;

    @NotBlank(message = "邮箱验证码不能为空")
    private String emailCode;

    @NotBlank(message = "图形验证码ID不能为空")
    private String captchaId;

    @NotBlank(message = "图形验证码不能为空")
    private String captchaCode;
}
