package com.aies.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * A-06 发送邮箱验证码请求（UC-42）。
 *
 * <p>scene：login/register/reset（03-04 §2.3 key规范含{scene}；契约要点未列此字段，
 * 属登记偏差——一个发送端点服务三条流程，scene 必传）。
 * 邮箱格式校验在 Service 层（→30005），不用 @Email（Bean Validation 只能→30000）。</p>
 */
@Data
public class EmailCodeSendRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    private String email;

    /** 场景：login / register / reset */
    @NotBlank(message = "验证码场景不能为空")
    private String scene;

    @NotBlank(message = "图形验证码ID不能为空")
    private String captchaId;

    @NotBlank(message = "图形验证码不能为空")
    private String captchaCode;
}
