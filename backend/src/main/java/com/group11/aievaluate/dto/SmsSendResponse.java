package com.group11.aievaluate.dto;

/**
 * 短信验证码发送结果。
 *
 * @param phone        手机号
 * @param expireSeconds 有效期（秒）
 * @param mockCode     模拟发送时的验证码；真实短信通道下为 null
 */
public record SmsSendResponse(String phone, long expireSeconds, String mockCode) {
}
