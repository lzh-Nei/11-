package com.aies.controller;

import com.aies.common.PermissionConstants;
import com.aies.common.Result;
import com.aies.dto.CaptchaResponse;
import com.aies.dto.EmailCodeSendRequest;
import com.aies.dto.EmailCodeSendResponse;
import com.aies.dto.EmailLoginRequest;
import com.aies.dto.ForgotPasswordRequest;
import com.aies.dto.LoginRequest;
import com.aies.dto.LoginResponse;
import com.aies.dto.RefreshRequest;
import com.aies.dto.RegisterRequest;
import com.aies.dto.ResetPasswordRequest;
import com.aies.dto.UserVO;
import com.aies.service.AuthService;
import com.aies.service.CaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（03-03 A-01~A-10 契约；D-19 后验证码通道唯一为邮箱）。
 *
 * <p>登录状态经 JWT 过滤器装配；@PreAuthorize 权限点对应 PermissionConstants（S-06）。</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    /** A-04 获取图形验证码：返回 captchaId 与 Base64 图片（限流20/min/IP） */
    @GetMapping("/captcha")
    public Result<CaptchaResponse> captcha(HttpServletRequest request) {
        return Result.success(captchaService.generate(clientIp(request)));
    }

    /** A-01 账号密码登录（带一次性图形验证码；限流10/min/IP） */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        return Result.success(authService.login(request, clientIp(httpRequest)));
    }

    /** A-03 刷新令牌：RT旋转，旧RT即废（无效→40101） */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request.refreshToken()));
    }

    /** A-06 发送邮箱验证码（UC-42）：限流5/min/IP+图形码；频控→30010、SMTP不可用→40004降级 */
    @PostMapping("/email-code")
    public Result<EmailCodeSendResponse> emailCode(@Valid @RequestBody EmailCodeSendRequest request,
                                                   HttpServletRequest httpRequest) {
        return Result.success(authService.sendEmailCode(request, clientIp(httpRequest)));
    }

    /** A-07 邮箱验证码登录（UC-42）：签发AT/RT同A-01；码无效→30009 */
    @PostMapping("/login/email")
    public Result<LoginResponse> loginByEmail(@Valid @RequestBody EmailLoginRequest request,
                                              HttpServletRequest httpRequest) {
        return Result.success(authService.loginByEmail(request, clientIp(httpRequest)));
    }

    /** A-08 注册（UC-43）：学号重复→30012、班级码无效→30011、弱密码→30013 */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request,
                                   HttpServletRequest httpRequest) {
        return Result.success(authService.register(request, clientIp(httpRequest)));
    }

    /** A-09 忘记密码（UC-44）：S-13 统一文案防枚举——无论命中与否 code=0+同形响应 */
    @PostMapping("/password/forgot")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                       HttpServletRequest httpRequest) {
        authService.forgotPassword(request, clientIp(httpRequest));
        Result<Void> result = Result.success();
        result.setMessage(AuthService.FORGOT_UNIFORM_MSG);
        return result;
    }

    /** A-10 重置密码（UC-44）：弱密码→30013；成功作废该用户全部RT（S-10）+ 审计 */
    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.success();
    }

    /** A-02 退出登录：AT入Redis黑名单；权限点 auth:logout（PermissionConstants.AUTH_LOGOUT）；成功即204 */
    @PreAuthorize("hasAuthority('" + PermissionConstants.AUTH_LOGOUT + "')")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authService.logout(currentPrincipal());
        return ResponseEntity.noContent().build();
    }

    /** 当前登录用户信息；权限点 user:me（PermissionConstants.USER_ME） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_ME + "')")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.currentUser());
    }

    /** 登出/详情接口的当前主体（JWT过滤器装配；未登录时此路径不可达） */
    private com.aies.security.LoginPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof com.aies.security.LoginPrincipal p)) {
            throw new com.aies.common.BusinessException(com.aies.common.ErrorCode.UNAUTHORIZED);
        }
        return p;
    }

    /** 客户端IP：优先取网关X-Forwarded-For首段，回退remoteAddr */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
