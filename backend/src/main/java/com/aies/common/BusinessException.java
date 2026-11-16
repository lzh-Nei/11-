package com.aies.common;

import lombok.Getter;

/**
 * 业务异常：由统一异常处理器转换为标准 Result 返回。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 使用自定义提示覆盖错误码默认文案（例如“验证码已过期，请重新获取”）。
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /**
     * 直接以错误码数值构造（用于同一 code 在不同场景承载不同文案，如 40101）。
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
