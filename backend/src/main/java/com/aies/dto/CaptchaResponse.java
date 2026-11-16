package com.aies.dto;

/**
 * 图形验证码响应（03-03 A-04：base64图片 + captchaId；Redis TTL 5min）。
 *
 * @param captchaId     验证码标识，登录/注册/找回时原样带回（key规范 auth:captcha:{captchaId}）
 * @param image         Base64 图片，已带 data:image/png;base64, 前缀，可直接作为 img 的 src
 * @param expireSeconds 有效期（秒）
 */
public record CaptchaResponse(String captchaId, String image, long expireSeconds) {
}
