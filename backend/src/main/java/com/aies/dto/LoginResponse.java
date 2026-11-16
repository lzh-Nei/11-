package com.aies.dto;

/**
 * 登录/刷新成功响应（03-03 A-01/A-03 契约形状：accessToken + refreshToken + role + 首登标记）。
 *
 * <p>前端后续请求携带请求头：{@code Authorization: Bearer <accessToken>}。
 * AT 2h；RT 7d 旋转（旧RT即用即废）；用户详情经 GET /api/auth/me 获取。</p>
 *
 * @param accessToken  访问令牌（JWT，2h）
 * @param refreshToken 刷新令牌（随机串，7d，旋转一次性）
 * @param role         主角色（ADMIN/TEACHER/STUDENT）
 * @param firstLogin   首登标记（true 时前端引导强制改密，UC-07）
 */
public record LoginResponse(String accessToken,
                            String refreshToken,
                            String role,
                            boolean firstLogin) {
}
