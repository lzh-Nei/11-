package com.aies.common;

import lombok.Getter;

/**
 * 统一业务错误码——与权威错误码总表逐一对齐（02-02 §九 + 增补一 §三，23个；
 * 另含编码期登记的 30000/50000，全局25个，04-03口径）。
 *
 * <p>code == 0 表示成功。分段语义：3xxxx=请求/校验类，4xxxx=处理失败类，
 * 401xx=认证失败，40301=无权限。HTTP 状态映射见 {@code ApiExceptionHandler}。</p>
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "success"),

    /* ===== 3xxxx 请求/校验类 ===== */
    PARAM_ERROR(30000, "参数校验失败"),
    FILE_TYPE_UNSUPPORTED(30001, "文件格式不支持"),
    FILE_PARSE_FAILED(30002, "文件解析失败"),
    FILE_TOO_LARGE(30003, "文件超过大小上限"),
    USERNAME_EXISTS(30004, "学号已存在"),
    EMAIL_FORMAT_INVALID(30005, "邮箱格式非法"),
    DEADLINE_INVALID(30006, "截止时间非法"),
    REVIEW_REASON_REQUIRED(30007, "复核理由为空"),
    CAPTCHA_INVALID(30008, "图形验证码错误或已过期"),
    EMAIL_CODE_INVALID(30009, "邮箱验证码错误或已过期"),
    CODE_SEND_TOO_FREQUENT(30010, "操作过于频繁，请稍后再试"),
    CLASS_CODE_INVALID(30011, "班级码无效"),
    USERNAME_REGISTERED(30012, "学号已注册"),
    PASSWORD_WEAK(30013, "密码强度不足（至少8位且含字母与数字）"),
    EMAIL_NOT_BOUND(30014, "邮箱未绑定"),

    /* ===== 4xxxx 处理失败类 ===== */
    METRIC_WEIGHT_SUM_INVALID(40001, "指标权重之和必须等于100"),
    TASK_NOT_OPEN(40002, "任务未发布或已截止"),
    AI_EVALUATION_RETRIES_EXHAUSTED(40003, "AI评价重试耗尽"),
    SMTP_UNAVAILABLE(40004, "邮件服务暂不可用，请改用账号密码登录"),
    CAS_UNAVAILABLE(40005, "统一身份认证暂未开启"),
    ILLEGAL_SORT_FIELD(40006, "非法排序字段"),

    /* ===== 401xx 认证失败 / 40301 无权限 ===== */
    /** Token 无效/过期；同时承载登录失败统一文案（S-02 防枚举） */
    AUTH_FAIL(40101, "账号或密码错误"),
    UNAUTHORIZED(40101, "未登录或登录已过期"),
    /** 账号禁用/锁定（UC-01 E1/E2） */
    ACCOUNT_LOCKED(40103, "账号已被禁用或锁定，请稍后再试"),
    /** @PreAuthorize 拦截（S-06） */
    FORBIDDEN(40301, "无操作权限"),
    /** 404 兜底（表外补充，待04-03登记） */
    NOT_FOUND(40400, "请求的资源不存在"),

    SYSTEM_ERROR(50000, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
