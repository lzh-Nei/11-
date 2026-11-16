package com.aies.service;

/**
 * LLM调用异常（04-05 §三：网络/超时/非200统一封装为内部异常，不外泄；
 * 由评价管道重试逻辑处置——超时计为一次失败重试）。
 */
public class LlmInvocationException extends RuntimeException {

    public LlmInvocationException(String message) {
        super(message);
    }

    public LlmInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
