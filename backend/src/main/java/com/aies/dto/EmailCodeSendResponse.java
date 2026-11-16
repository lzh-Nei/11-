package com.aies.dto;

/**
 * A-06 邮箱验证码发送结果。
 *
 * <p>mockCode 仅在【未配置SMTP的模拟模式 + expose-code开启 + 命中】时返回（dev联调）；
 * 防枚举：A-06未命中（用户名不存在/邮箱不匹配/用户名已占用）统一成功但 mockCode=null；
 * A-09 忘记密码路径不使用本响应（S-13 统一文案，重置码永不回显）。</p>
 *
 * @param username      请求的用户名
 * @param expireSeconds 验证码有效期（秒）
 * @param mockCode      模拟模式下的验证码；真实SMTP通道或未命中时为 null
 */
public record EmailCodeSendResponse(String username, long expireSeconds, String mockCode) {
}
