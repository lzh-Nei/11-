package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.EmailScene;
import com.aies.common.ErrorCode;
import com.aies.common.RateLimiter;
import com.aies.config.AuthProperties;
import com.aies.dto.EmailCodeSendRequest;
import com.aies.dto.EmailCodeSendResponse;
import com.aies.dto.EmailLoginRequest;
import com.aies.dto.ForgotPasswordRequest;
import com.aies.dto.LoginRequest;
import com.aies.dto.LoginResponse;
import com.aies.dto.RegisterRequest;
import com.aies.dto.ResetPasswordRequest;
import com.aies.dto.UserVO;
import com.aies.entity.User;
import com.aies.mapper.RbacMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.UserMapper;
import com.aies.security.JwtService;
import com.aies.security.LoginPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 认证核心业务（03-03 A系列 / 03-04 §2.1 / 04-04 S-01~S-04、S-10~S-13）。
 *
 * <p>验证码通道统一为邮箱（D-19：SMS为脚手架遗留死代码已清除，基线认证通道=邮箱SMTP）。
 * A-06/A-07 邮箱码登录（UC-42）、A-08 注册（UC-43）、A-09/A-10 找回（UC-44，S-13防枚举）。</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** S-02 防枚举统一文案（登录/找回失败禁分叉提示） */
    public static final String AUTH_FAIL_MSG = "账号或密码错误";

    /** S-13 找回统一文案（A-09 无论命中与否同形返回） */
    public static final String FORGOT_UNIFORM_MSG = "如该账号存在，重置邮件已发送";

    private static final String LOGIN_FAIL_KEY = "auth:login:fail:";
    private static final String LOGIN_LOCK_KEY = "auth:login:lock:";
    private static final String LOGIN_LIMIT_KEY = "auth:limit:login:";
    private static final String EMAIL_CODE_LIMIT_KEY = "auth:limit:emailcode:";
    private static final String REGISTER_LIMIT_KEY = "auth:limit:register:";
    private static final int FAIL_LIMIT = 5;
    private static final int LOCK_SECONDS = 900;
    private static final int STATUS_ENABLED = 1;
    /** S-13/30013 密码强度：至少8位且同时含字母与数字 */
    private static final Pattern PASSWORD_STRONG = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private final UserMapper userMapper;
    private final RbacMapper rbacMapper;
    private final TaskMapper taskMapper;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final EmailCodeService emailCodeService;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;
    private final AuthProperties authProperties;

    /**
     * A-01 账号密码登录：锁定检查 → 限流(10/min/IP) → 图形码(一次性) → BCrypt比对 → 签发AT/RT。
     */
    public LoginResponse login(LoginRequest request, String clientIp) {
        if (!rateLimiter.tryAcquire(LOGIN_LIMIT_KEY + clientIp, 10, 60)) {
            throw new BusinessException(ErrorCode.CODE_SEND_TOO_FREQUENT);
        }
        checkNotLocked(request.getUsername());

        captchaService.verify(request.getCaptchaId(), request.getCaptchaCode());

        User user = userMapper.selectByUsername(request.getUsername().trim());
        // S-02：用户不存在与密码错误同一文案，防账号枚举
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordLoginFailure(request.getUsername());
            throw new BusinessException(ErrorCode.AUTH_FAIL.getCode(), AUTH_FAIL_MSG);
        }
        return doLogin(user);
    }

    /**
     * A-06 发送邮箱验证码（UC-42）：限流5/min/IP → 图形码 → 场景分发。
     *
     * <p>LOGIN/RESET场景仅向 username 绑定邮箱发送（用户名不存在或邮箱不匹配→统一成功
     * 但不实际发送，防枚举）；REGISTER场景要求用户名未被占用。邮箱码频控（S-12）与
     * SMTP投递（失败→40004降级）由 EmailCodeService 执行。</p>
     */
    public EmailCodeSendResponse sendEmailCode(EmailCodeSendRequest request, String clientIp) {
        if (!rateLimiter.tryAcquire(EMAIL_CODE_LIMIT_KEY + clientIp, 5, 60)) {
            throw new BusinessException(ErrorCode.CODE_SEND_TOO_FREQUENT);
        }
        captchaService.verify(request.getCaptchaId(), request.getCaptchaCode());

        EmailScene scene;
        try {
            scene = EmailScene.valueOf(request.getScene().trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "scene取值非法");
        }
        String username = request.getUsername().trim();
        String email = request.getEmail().trim();
        ensureEmailValid(email);

        boolean matched;
        if (scene == EmailScene.REGISTER) {
            matched = userMapper.selectByUsername(username) == null;
        } else {
            User user = userMapper.selectByUsername(username);
            matched = user != null && user.getEmail() != null
                    && user.getEmail().equalsIgnoreCase(email);
        }
        // 防枚举：未命中统一成功响应但不实际发送（mockCode=null）
        if (!matched) {
            return new EmailCodeSendResponse(username,
                    authProperties.getEmail().getExpireSeconds(), null);
        }
        return emailCodeService.send(username, email, scene);
    }

    /**
     * A-07 邮箱验证码登录（UC-42）：限流(10/min/IP) → 锁定检查 → 邮箱码(一次性) → 签发AT/RT。
     */
    public LoginResponse loginByEmail(EmailLoginRequest request, String clientIp) {
        if (!rateLimiter.tryAcquire(LOGIN_LIMIT_KEY + clientIp, 10, 60)) {
            throw new BusinessException(ErrorCode.CODE_SEND_TOO_FREQUENT);
        }
        String username = request.getUsername().trim();
        checkNotLocked(username);

        emailCodeService.verify(username, request.getEmailCode(), EmailScene.LOGIN);

        User user = userMapper.selectByUsername(username);
        if (user == null) {
            // S-02：登录失败统一文案
            throw new BusinessException(ErrorCode.AUTH_FAIL.getCode(), AUTH_FAIL_MSG);
        }
        return doLogin(user);
    }

    /**
     * A-08 注册（UC-43）：限流 → 图形码 → 邮箱格式/密码强度/班级码/学号唯一 →
     * 邮箱码(一次性) → BCrypt入库 → 绑定内置STUDENT角色。
     *
     * <p>校验顺序刻意先廉后贵：一次性邮箱码在全部前置校验通过后才消费，避免弱密码等
     * 低级失败白白消耗用户的一次发送冷却。</p>
     */
    public UserVO register(RegisterRequest request, String clientIp) {
        if (!rateLimiter.tryAcquire(REGISTER_LIMIT_KEY + clientIp, 10, 60)) {
            throw new BusinessException(ErrorCode.CODE_SEND_TOO_FREQUENT);
        }
        captchaService.verify(request.getCaptchaId(), request.getCaptchaCode());

        String username = request.getUsername().trim();
        String email = request.getEmail().trim();
        ensureEmailValid(email);
        ensurePasswordStrong(request.getPassword());

        Long classId = taskMapper.selectClassIdByCode(request.getClassCode().trim());
        if (classId == null) {
            throw new BusinessException(ErrorCode.CLASS_CODE_INVALID);
        }
        if (userMapper.selectByUsername(username) != null) {
            throw new BusinessException(ErrorCode.USERNAME_REGISTERED);
        }

        emailCodeService.verify(username, request.getEmailCode(), EmailScene.REGISTER);

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(StringUtils.hasText(request.getRealName())
                ? request.getRealName().trim() : username);
        user.setEmail(email);
        user.setClassId(classId);
        user.setStatus(STATUS_ENABLED);
        user.setFirstLogin(1);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册时由数据库唯一索引兜底
            throw new BusinessException(ErrorCode.USERNAME_REGISTERED);
        }
        // 五表RBAC标准：新注册用户绑定内置 STUDENT 角色（sys_user_role）
        rbacMapper.insertUserRole(user.getId(), "STUDENT");
        return UserVO.from(user, rbacMapper.selectRoleCodes(user.getId()),
                rbacMapper.selectPermCodes(user.getId()));
    }

    /**
     * A-09 忘记密码（UC-44，S-13 防枚举）：仅命中（用户名存在且邮箱匹配）才实际发送；
     * 无论命中与否由 Controller 以统一文案返回 code=0；重置码不回显（含dev模拟模式）。
     */
    public void forgotPassword(ForgotPasswordRequest request, String clientIp) {
        if (!rateLimiter.tryAcquire(EMAIL_CODE_LIMIT_KEY + clientIp, 5, 60)) {
            throw new BusinessException(ErrorCode.CODE_SEND_TOO_FREQUENT);
        }
        captchaService.verify(request.getCaptchaId(), request.getCaptchaCode());

        String username = request.getUsername().trim();
        String email = request.getEmail().trim();
        User user = userMapper.selectByUsername(username);
        boolean matched = user != null && user.getEmail() != null
                && user.getEmail().equalsIgnoreCase(email);
        if (matched) {
            emailCodeService.send(username, email, EmailScene.RESET_PASSWORD);
        }
    }

    /**
     * A-10 重置密码（UC-44）：弱密码→30013；账号不存在与验证码错误统一30009（S-13）；
     * 邮箱码一次性消费；成功即作废该用户全部RT（S-10）。
     */
    public void resetPassword(ResetPasswordRequest request) {
        ensurePasswordStrong(request.getNewPassword());

        String username = request.getUsername().trim();
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        }
        emailCodeService.verify(username, request.getEmailCode(), EmailScene.RESET_PASSWORD);

        userMapper.updatePassword(user.getId(), passwordEncoder.encode(request.getNewPassword()));
        jwtService.revokeAllRefreshTokens(user.getId());
    }

    /**
     * 当前登录用户信息（principal 由 JWT 过滤器装配）。
     */
    public UserVO currentUser() {
        long userId = currentPrincipal().userId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return UserVO.from(user, rbacMapper.selectRoleCodes(userId),
                rbacMapper.selectPermCodes(userId));
    }

    /**
     * A-02 退出登录：AT 入 Redis 黑名单至自然过期（成功即 HTTP 204）。
     */
    public void logout(LoginPrincipal principal) {
        jwtService.blacklistAccessToken(principal.jti(), principal.expiresAtMillis());
    }

    /**
     * A-03 刷新：RT 旋转（验旧→发新AT+新RT→删旧），无效→40101。
     */
    public LoginResponse refresh(String refreshToken) {
        JwtService.TokenPair pair = jwtService.rotateRefreshToken(refreshToken);
        return new LoginResponse(pair.accessToken(), pair.refreshToken(),
                jwtService.primaryRole(pair.userId()), isFirstLogin(pair.userId()));
    }

    private LoginResponse doLogin(User user) {
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            // UC-01 E1：账号禁用 → 40103
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        String role = jwtService.primaryRole(user.getId());
        JwtService.TokenPair pair = jwtService.issueTokens(user.getId(), role);
        return new LoginResponse(pair.accessToken(), pair.refreshToken(), role,
                user.getFirstLogin() != null && user.getFirstLogin() == 1);
    }

    /* ===== 密码强度 / 邮箱格式（30013 / 30005） ===== */

    private void ensureEmailValid(String email) {
        if (!StringUtils.hasText(email) || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.EMAIL_FORMAT_INVALID);
        }
    }

    private void ensurePasswordStrong(String password) {
        if (!StringUtils.hasText(password) || !PASSWORD_STRONG.matcher(password).matches()) {
            throw new BusinessException(ErrorCode.PASSWORD_WEAK);
        }
    }

    /* ===== S-04 登录失败锁定 ===== */

    private void checkNotLocked(String username) {
        if (Boolean.TRUE.equals(redisHasLock(username))) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
    }

    private Boolean redisHasLock(String username) {
        return rateLimiter.exists(LOGIN_LOCK_KEY + username);
    }

    private void recordLoginFailure(String username) {
        String failKey = LOGIN_FAIL_KEY + username;
        Long fails = rateLimiter.increment(failKey, LOCK_SECONDS);
        if (fails != null && fails >= FAIL_LIMIT) {
            // 达5次 → 锁定15分钟（计数键与锁键同步15分钟自然过期）
            rateLimiter.putWithTtl(LOGIN_LOCK_KEY + username, "1", LOCK_SECONDS);
            rateLimiter.delete(failKey);
        }
    }

    private boolean isFirstLogin(long userId) {
        User user = userMapper.selectById(userId);
        return user != null && user.getFirstLogin() != null && user.getFirstLogin() == 1;
    }

    private LoginPrincipal currentPrincipal() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }
}
