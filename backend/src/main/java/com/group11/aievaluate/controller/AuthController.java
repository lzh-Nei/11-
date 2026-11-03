package com.group11.aievaluate.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.group11.aievaluate.common.Result;
import com.group11.aievaluate.dto.CaptchaResponse;
import com.group11.aievaluate.dto.ForgotPasswordRequest;
import com.group11.aievaluate.dto.LoginRequest;
import com.group11.aievaluate.dto.LoginResponse;
import com.group11.aievaluate.dto.RegisterRequest;
import com.group11.aievaluate.dto.ResetPasswordRequest;
import com.group11.aievaluate.dto.SmsLoginRequest;
import com.group11.aievaluate.dto.SmsSendRequest;
import com.group11.aievaluate.dto.SmsSendResponse;
import com.group11.aievaluate.dto.UserVO;
import com.group11.aievaluate.service.AuthService;
import com.group11.aievaluate.service.CaptchaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：验证码、注册、两种登录、找回密码、退出登录。
 *
 * <p>除 /logout、/me 外均为匿名接口，不经过 Sa-Token 登录校验。</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    /** 获取图形验证码，返回 captchaKey 与 Base64 图片 */
    @GetMapping("/captcha")
    public Result<CaptchaResponse> captcha() {
        return Result.success(captchaService.generate());
    }

    /** 账号密码登录（带图形验证码） */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /** 发送手机号登录验证码（模拟发送，验证码可在响应中查看） */
    @PostMapping("/sms/send")
    public Result<SmsSendResponse> sendLoginSms(@Valid @RequestBody SmsSendRequest request) {
        return Result.success(authService.sendLoginCode(request));
    }

    /** 手机号 + 短信验证码登录 */
    @PostMapping("/sms/login")
    public Result<LoginResponse> smsLogin(@Valid @RequestBody SmsLoginRequest request) {
        return Result.success(authService.loginBySms(request));
    }

    /** 注册 */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /** 忘记密码第一步：发送重置验证码 */
    @PostMapping("/password/forgot")
    public Result<SmsSendResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return Result.success(authService.sendResetCode(request));
    }

    /** 忘记密码第二步：校验验证码并重置密码 */
    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.success();
    }

    /** 退出登录 */
    @SaCheckLogin
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    /** 当前登录用户信息 */
    @SaCheckLogin
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.currentUser());
    }
}
