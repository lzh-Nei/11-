package com.group11.aievaluate.common;

import lombok.Getter;

/**
 * 统一业务错误码。
 *
 * <p>code == 0 表示成功，其余均为失败。HTTP 状态码由
 * {@code ApiExceptionHandler} 根据异常类型决定，响应体始终是统一的 Result 结构。</p>
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "success"),

    PARAM_ERROR(40000, "参数校验失败"),
    BAD_CREDENTIALS(40001, "用户名或密码错误"),
    CAPTCHA_INVALID(40002, "图形验证码错误或已过期"),
    SMS_CODE_INVALID(40003, "短信验证码错误或已过期"),
    USER_EXISTS(40004, "用户名或手机号已被注册"),
    USER_NOT_FOUND(40005, "用户不存在"),
    ACCOUNT_DISABLED(40006, "账号已被禁用"),
    SMS_TOO_FREQUENT(40007, "验证码发送过于频繁，请稍后再试"),
    PASSWORD_MISMATCH(40008, "两次输入的密码不一致"),

    UNAUTHORIZED(40100, "未登录或登录已过期"),
    FORBIDDEN(40300, "无操作权限"),
    NOT_FOUND(40400, "请求的资源不存在"),

    SYSTEM_ERROR(50000, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
