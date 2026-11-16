package com.aies.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 自助修改个人资料（个人中心）：姓名与手机号。
 *
 * <p>用户名/邮箱/角色不可在此变更：邮箱是登录与找回密码的依据，变更需走验证流程（M3+）；
 * 手机号允许传空串以清除。</p>
 */
@Data
public class UpdateProfileRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 32, message = "姓名最长32字")
    private String realName;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
