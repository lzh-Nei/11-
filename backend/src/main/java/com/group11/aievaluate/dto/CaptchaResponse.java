package com.group11.aievaluate.dto;

/**
 * 图形验证码响应。
 *
 * @param captchaKey  验证码标识，登录时原样带回
 * @param image       Base64 图片，已带 data:image/png;base64, 前缀，可直接作为 img 的 src
 * @param expireSeconds 有效期（秒）
 */
public record CaptchaResponse(String captchaKey, String image, long expireSeconds) {
}
