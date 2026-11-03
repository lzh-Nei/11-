package com.group11.aievaluate.dto;

/**
 * 登录成功响应。
 *
 * <p>前端后续请求携带请求头：{@code Authorization: Bearer <token>}。</p>
 *
 * @param token       登录凭证
 * @param tokenName   请求头名称，当前为 Authorization
 * @param tokenPrefix 凭证前缀，当前为 Bearer
 * @param expireIn    token 剩余有效期（秒），-1 表示永不过期
 * @param user        当前登录用户
 */
public record LoginResponse(String token,
                            String tokenName,
                            String tokenPrefix,
                            long expireIn,
                            UserVO user) {
}
