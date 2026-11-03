package com.group11.aievaluate.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import com.group11.aievaluate.common.BusinessException;
import com.group11.aievaluate.common.ErrorCode;
import com.group11.aievaluate.common.SmsScene;
import com.group11.aievaluate.dto.ForgotPasswordRequest;
import com.group11.aievaluate.dto.LoginRequest;
import com.group11.aievaluate.dto.LoginResponse;
import com.group11.aievaluate.dto.RegisterRequest;
import com.group11.aievaluate.dto.ResetPasswordRequest;
import com.group11.aievaluate.dto.SmsLoginRequest;
import com.group11.aievaluate.dto.SmsSendRequest;
import com.group11.aievaluate.dto.SmsSendResponse;
import com.group11.aievaluate.dto.UserVO;
import com.group11.aievaluate.entity.User;
import com.group11.aievaluate.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证核心业务：注册、两种登录方式、找回密码、当前用户。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_ROLE = "student";
    private static final int STATUS_ENABLED = 1;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final SmsCodeService smsCodeService;

    /**
     * 账号密码登录：先校验一次性图形验证码，再用 BCrypt 比对密码。
     */
    public LoginResponse login(LoginRequest request) {
        captchaService.verify(request.getCaptchaKey(), request.getCaptchaCode());

        User user = userMapper.selectByUsername(request.getUsername().trim());
        // 用户不存在与密码错误返回同一提示，避免被枚举账号
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        return doLogin(user);
    }

    /**
     * 手机号短信验证码登录。
     */
    public LoginResponse loginBySms(SmsLoginRequest request) {
        smsCodeService.verify(request.getPhone(), request.getCode(), SmsScene.LOGIN);

        User user = userMapper.selectByPhone(request.getPhone());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "该手机号尚未注册");
        }
        return doLogin(user);
    }

    /**
     * 发送登录短信验证码，手机号必须已注册。
     */
    public SmsSendResponse sendLoginCode(SmsSendRequest request) {
        ensurePhoneRegistered(request.getPhone());
        return smsCodeService.send(request.getPhone(), SmsScene.LOGIN);
    }

    /**
     * 注册：密码 BCrypt 慢哈希后入库，新用户固定为 student 角色。
     */
    public UserVO register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        String username = request.getUsername().trim();
        String phone = request.getPhone().trim();
        if (userMapper.selectByUsername(username) != null || userMapper.selectByPhone(phone) != null) {
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(phone);
        user.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname().trim() : username);
        user.setRole(DEFAULT_ROLE);
        user.setStatus(STATUS_ENABLED);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册时由数据库唯一索引兜底
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }
        return UserVO.from(user);
    }

    /**
     * 忘记密码第一步：向已注册手机号发送重置验证码。
     */
    public SmsSendResponse sendResetCode(ForgotPasswordRequest request) {
        ensurePhoneRegistered(request.getPhone());
        return smsCodeService.send(request.getPhone(), SmsScene.RESET_PASSWORD);
    }

    /**
     * 忘记密码第二步：校验验证码后更新密码。
     *
     * <p>密码变更后调用 kickout 让该账号所有已签发的 token 立即失效，
     * 防止旧密码泄漏后攻击者继续使用旧会话。</p>
     */
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        smsCodeService.verify(request.getPhone(), request.getCode(), SmsScene.RESET_PASSWORD);

        User user = userMapper.selectByPhone(request.getPhone());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "该手机号尚未注册");
        }

        userMapper.updatePassword(user.getId(), passwordEncoder.encode(request.getNewPassword()));
        StpUtil.kickout(user.getId());
    }

    /**
     * 当前登录用户信息。
     */
    public UserVO currentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return UserVO.from(user);
    }

    /**
     * 退出登录：删除当前 token。
     */
    public void logout() {
        StpUtil.logout();
    }

    private LoginResponse doLogin(User user) {
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        StpUtil.login(user.getId());
        return new LoginResponse(
                StpUtil.getTokenValue(),
                StpUtil.getTokenName(),
                SaManager.getConfig().getTokenPrefix(),
                StpUtil.getTokenTimeout(),
                UserVO.from(user));
    }

    private void ensurePhoneRegistered(String phone) {
        if (userMapper.selectByPhone(phone) == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "该手机号尚未注册");
        }
    }
}
