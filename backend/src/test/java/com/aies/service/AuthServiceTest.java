package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 单测（A-01/A-02/A-03/A-06/A-08/A-09/A-10 + S-02防枚举/S-04锁定/S-10作废RT/S-13统一文案）。
 */
class AuthServiceTest {

    private UserMapper userMapper;
    private RbacMapper rbacMapper;
    private TaskMapper taskMapper;
    private PasswordEncoder passwordEncoder;
    private CaptchaService captchaService;
    private EmailCodeService emailCodeService;
    private JwtService jwtService;
    private com.aies.common.RateLimiter rateLimiter;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        rbacMapper = mock(RbacMapper.class);
        taskMapper = mock(TaskMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        captchaService = mock(CaptchaService.class);
        emailCodeService = mock(EmailCodeService.class);
        jwtService = mock(JwtService.class);
        rateLimiter = mock(com.aies.common.RateLimiter.class);
        authService = new AuthService(userMapper, rbacMapper, taskMapper, passwordEncoder,
                captchaService, emailCodeService, jwtService, rateLimiter, new AuthProperties());
        when(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(rateLimiter.exists(anyString())).thenReturn(false);
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("21010001");
        request.setPassword("Passw0rd");
        request.setCaptchaId("c1");
        request.setCaptchaCode("abcd");
        return request;
    }

    private User enabledUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("21010001");
        user.setPassword("$2a$10$hash");
        user.setRealName("张三");
        user.setEmail("stu@example.com");
        user.setStatus(1);
        user.setFirstLogin(0);
        return user;
    }

    private void stubTokens() {
        when(jwtService.primaryRole(1L)).thenReturn("STUDENT");
        when(jwtService.issueTokens(1L, "STUDENT"))
                .thenReturn(new JwtService.TokenPair(1L, "at-token", "rt-token", 7200));
    }

    /* ==================== A-01 账号密码登录 ==================== */

    /** 例1 限流（10/min/IP）→ 30010 */
    @Test
    void loginRateLimitedRejects() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest(), "1.2.3.4"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.CODE_SEND_TOO_FREQUENT.getCode()));
    }

    /** 例2 锁定中账号 → 40103（S-04） */
    @Test
    void loginLockedAccountRejects() {
        when(rateLimiter.exists("auth:login:lock:21010001")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest(), "1.2.3.4"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED.getCode()));
    }

    /** 例3 用户不存在 → 40101统一文案（S-02防枚举，不泄露存在性） */
    @Test
    void loginUnknownUsernameUniformFailure() {
        when(userMapper.selectByUsername("21010001")).thenReturn(null);

        assertThatThrownBy(() -> authService.login(loginRequest(), "1.2.3.4"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.AUTH_FAIL.getCode());
                    assertThat(e.getMessage()).isEqualTo("账号或密码错误");
                });
        verify(rateLimiter).increment(eq("auth:login:fail:21010001"), eq(900));
    }

    /** 例4 密码错误 → 40101 + 失败计数 */
    @Test
    void loginWrongPasswordRecordsFailure() {
        when(userMapper.selectByUsername("21010001")).thenReturn(enabledUser());
        when(passwordEncoder.matches("Passw0rd", "$2a$10$hash")).thenReturn(false);
        when(rateLimiter.increment(anyString(), anyInt())).thenReturn(1L);

        assertThatThrownBy(() -> authService.login(loginRequest(), "1.2.3.4"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AUTH_FAIL.getCode()));
        verify(rateLimiter).increment(eq("auth:login:fail:21010001"), eq(900));
    }

    /** 例5 达5次失败 → 锁定15分钟+计数清零（S-04阈值） */
    @Test
    void loginFailureAtThresholdLocksAccount() {
        when(userMapper.selectByUsername("21010001")).thenReturn(enabledUser());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(rateLimiter.increment(anyString(), anyInt())).thenReturn(5L);

        assertThatThrownBy(() -> authService.login(loginRequest(), "1.2.3.4"))
                .isInstanceOf(BusinessException.class);
        verify(rateLimiter).putWithTtl("auth:login:lock:21010001", "1", 900);
        verify(rateLimiter).delete("auth:login:fail:21010001");
    }

    /** 例6 正例：图形码校验+签发AT/RT+角色/首登标记映射 */
    @Test
    void loginSuccessIssuesTokenPair() {
        User user = enabledUser();
        when(userMapper.selectByUsername("21010001")).thenReturn(user);
        when(passwordEncoder.matches("Passw0rd", "$2a$10$hash")).thenReturn(true);
        stubTokens();

        LoginResponse response = authService.login(loginRequest(), "1.2.3.4");

        assertThat(response.accessToken()).isEqualTo("at-token");
        assertThat(response.refreshToken()).isEqualTo("rt-token");
        assertThat(response.role()).isEqualTo("STUDENT");
        assertThat(response.firstLogin()).isFalse();
        verify(captchaService).verify("c1", "abcd");
    }

    /** 例7 首登标记：firstLogin=1 → 前端强制改密引导（UC-07） */
    @Test
    void loginMapsFirstLoginFlag() {
        User user = enabledUser();
        user.setFirstLogin(1);
        when(userMapper.selectByUsername("21010001")).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        stubTokens();

        assertThat(authService.login(loginRequest(), "1.2.3.4").firstLogin()).isTrue();
    }

    /** 例8 账号禁用（status≠1）→ 40103（UC-01 E1） */
    @Test
    void loginDisabledAccountRejects() {
        User disabled = enabledUser();
        disabled.setStatus(0);
        when(userMapper.selectByUsername("21010001")).thenReturn(disabled);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest(), "1.2.3.4"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED.getCode()));
    }

    /* ==================== A-06 发送邮箱验证码（UC-42） ==================== */

    private EmailCodeSendRequest emailCodeRequest(String scene, String email) {
        EmailCodeSendRequest request = new EmailCodeSendRequest();
        request.setUsername("21010001");
        request.setEmail(email);
        request.setScene(scene);
        request.setCaptchaId("c1");
        request.setCaptchaCode("abcd");
        return request;
    }

    /** 例9 scene取值非法 → 30000 */
    @Test
    void sendEmailCodeInvalidSceneRejected() {
        assertThatThrownBy(() -> authService.sendEmailCode(emailCodeRequest("sms", "stu@example.com"), "ip"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    /** 例10 LOGIN场景邮箱不匹配 → 统一成功但mockCode=null且不实发（防枚举） */
    @Test
    void sendEmailCodeLoginSceneEmailMismatchStaysSilent() {
        when(userMapper.selectByUsername("21010001")).thenReturn(enabledUser());

        EmailCodeSendResponse response =
                authService.sendEmailCode(emailCodeRequest("login", "other@example.com"), "ip");

        assertThat(response.username()).isEqualTo("21010001");
        assertThat(response.mockCode()).isNull();
        verify(emailCodeService, never()).send(anyString(), anyString(), any());
    }

    /** 例11 LOGIN场景命中 → 分发EmailCodeService（S-12频控/SMTP在服务内） */
    @Test
    void sendEmailCodeLoginSceneMatchedDispatches() {
        when(userMapper.selectByUsername("21010001")).thenReturn(enabledUser());
        when(emailCodeService.send("21010001", "stu@example.com", com.aies.common.EmailScene.LOGIN))
                .thenReturn(new EmailCodeSendResponse("21010001", 300, "123456"));

        EmailCodeSendResponse response =
                authService.sendEmailCode(emailCodeRequest("LOGIN", "stu@example.com"), "ip");

        assertThat(response.mockCode()).isEqualTo("123456");
    }

    /* ==================== A-07 邮箱码登录 ==================== */

    /** 例12 邮箱码登录：码校验→用户查得→签发；用户不存在→40101统一文案 */
    @Test
    void loginByEmailVerifiesCodeThenIssuesTokens() {
        when(userMapper.selectByUsername("21010001")).thenReturn(enabledUser());
        stubTokens();
        EmailLoginRequest request = new EmailLoginRequest();
        request.setUsername("21010001");
        request.setEmailCode("123456");

        LoginResponse response = authService.loginByEmail(request, "ip");

        verify(emailCodeService).verify("21010001", "123456", com.aies.common.EmailScene.LOGIN);
        assertThat(response.accessToken()).isEqualTo("at-token");
    }

    /** 例13 邮箱码登录用户不存在 → 40101（S-02） */
    @Test
    void loginByEmailUnknownUserUniformFailure() {
        when(userMapper.selectByUsername("21010001")).thenReturn(null);
        EmailLoginRequest request = new EmailLoginRequest();
        request.setUsername("21010001");
        request.setEmailCode("123456");

        assertThatThrownBy(() -> authService.loginByEmail(request, "ip"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AUTH_FAIL.getCode()));
    }

    /* ==================== A-08 注册（UC-43） ==================== */

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("21010002");
        request.setPassword("Passw0rd");
        request.setRealName(" ");
        request.setEmail("new@example.com");
        request.setClassCode("CS2025A");
        request.setCaptchaId("c1");
        request.setCaptchaCode("abcd");
        request.setEmailCode("123456");
        return request;
    }

    /** 例14 注册正例：BCrypt入库+绑定STUDENT角色+姓名缺省用学号+UserVO形状 */
    @Test
    void registerSuccessBindsStudentRole() {
        when(taskMapper.selectClassIdByCode("CS2025A")).thenReturn(1L);
        when(userMapper.selectByUsername("21010002")).thenReturn(null);
        when(passwordEncoder.encode("Passw0rd")).thenReturn("bcrypt-encoded");
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(55L);
            return 1;
        });
        when(rbacMapper.selectRoleCodes(55L)).thenReturn(List.of("STUDENT"));
        when(rbacMapper.selectPermCodes(55L)).thenReturn(List.of("task:view"));

        UserVO vo = authService.register(registerRequest(), "ip");

        assertThat(vo.id()).isEqualTo(55L);
        assertThat(vo.roles()).containsExactly("STUDENT");
        assertThat(vo.permissions()).containsExactly("task:view");
        verify(rbacMapper).insertUserRole(55L, "STUDENT");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("bcrypt-encoded");
        assertThat(saved.getValue().getRealName()).isEqualTo("21010002");
        assertThat(saved.getValue().getFirstLogin()).isEqualTo(1);
        assertThat(saved.getValue().getStatus()).isEqualTo(1);
        verify(emailCodeService).verify("21010002", "123456", com.aies.common.EmailScene.REGISTER);
    }

    /** 例15 弱密码 → 30013（先廉后贵：不消耗邮箱码） */
    @Test
    void registerWeakPasswordRejected() {
        RegisterRequest weak = registerRequest();
        weak.setPassword("12345678");

        assertThatThrownBy(() -> authService.register(weak, "ip"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PASSWORD_WEAK.getCode()));
        verify(emailCodeService, never()).verify(anyString(), anyString(), any());
    }

    /** 例16 班级码无效 → 30011 */
    @Test
    void registerInvalidClassCodeRejected() {
        when(taskMapper.selectClassIdByCode("CS2025A")).thenReturn(null);

        assertThatThrownBy(() -> authService.register(registerRequest(), "ip"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.CLASS_CODE_INVALID.getCode()));
    }

    /** 例17 学号已注册 → 30012 */
    @Test
    void registerDuplicateUsernameRejected() {
        when(taskMapper.selectClassIdByCode("CS2025A")).thenReturn(1L);
        when(userMapper.selectByUsername("21010002")).thenReturn(new User());

        assertThatThrownBy(() -> authService.register(registerRequest(), "ip"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.USERNAME_REGISTERED.getCode()));
    }

    /* ==================== A-09/A-10 忘记与重置（UC-44，S-13） ==================== */

    private ForgotPasswordRequest forgotRequest() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setUsername("21010001");
        request.setEmail("stu@example.com");
        request.setCaptchaId("c1");
        request.setCaptchaCode("abcd");
        return request;
    }

    /** 例18 命中（用户名+邮箱匹配）才实发RESET_PASSWORD场景码 */
    @Test
    void forgotPasswordSendsOnlyWhenMatched() {
        when(userMapper.selectByUsername("21010001")).thenReturn(enabledUser());

        authService.forgotPassword(forgotRequest(), "ip");

        verify(emailCodeService).send("21010001", "stu@example.com",
                com.aies.common.EmailScene.RESET_PASSWORD);
    }

    /** 例19 未命中静默（不泄露账号存在性，S-13） */
    @Test
    void forgotPasswordUnmatchedStaysSilent() {
        when(userMapper.selectByUsername("21010001")).thenReturn(null);

        authService.forgotPassword(forgotRequest(), "ip");

        verify(emailCodeService, never()).send(anyString(), anyString(), any());
    }

    /** 例20 重置：弱密码 → 30013 */
    @Test
    void resetPasswordWeakRejected() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setUsername("21010001");
        request.setEmailCode("123456");
        request.setNewPassword("short1");

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PASSWORD_WEAK.getCode()));
    }

    /** 例21 重置：账号不存在 → 统一30009（S-13不区分） */
    @Test
    void resetPasswordUnknownUserUniformCode() {
        when(userMapper.selectByUsername("21010001")).thenReturn(null);
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setUsername("21010001");
        request.setEmailCode("123456");
        request.setNewPassword("Passw0rd9");

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.EMAIL_CODE_INVALID.getCode()));
    }

    /** 例22 重置正例：更新密码+作废该用户全部RT（S-10决定性证据的接口层锚点） */
    @Test
    void resetPasswordSuccessRevokesAllRefreshTokens() {
        when(userMapper.selectByUsername("21010001")).thenReturn(enabledUser());
        when(passwordEncoder.encode("Passw0rd9")).thenReturn("bcrypt-new");
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setUsername("21010001");
        request.setEmailCode("123456");
        request.setNewPassword("Passw0rd9");

        authService.resetPassword(request);

        verify(emailCodeService).verify("21010001", "123456",
                com.aies.common.EmailScene.RESET_PASSWORD);
        verify(userMapper).updatePassword(1L, "bcrypt-new");
        verify(jwtService).revokeAllRefreshTokens(1L);
    }

    /* ==================== A-02/A-03 登出与刷新 ==================== */

    /** 例23 登出：AT入黑名单（jti+自然过期时长） */
    @Test
    void logoutBlacklistsAccessToken() {
        authService.logout(new com.aies.security.LoginPrincipal(1L, "jti-1", "raw", "STUDENT",
                System.currentTimeMillis() + 600_000));

        verify(jwtService).blacklistAccessToken(eq("jti-1"), anyLong());
    }

    /** 例24 刷新：RT旋转→新对+主角色+首登标记 */
    @Test
    void refreshRotatesTokenPair() {
        when(jwtService.rotateRefreshToken("rt-old"))
                .thenReturn(new JwtService.TokenPair(1L, "at-new", "rt-new", 7200));
        when(jwtService.primaryRole(1L)).thenReturn("TEACHER");
        when(userMapper.selectById(1L)).thenReturn(enabledUser());

        LoginResponse response = authService.refresh("rt-old");

        assertThat(response.accessToken()).isEqualTo("at-new");
        assertThat(response.refreshToken()).isEqualTo("rt-new");
        assertThat(response.role()).isEqualTo("TEACHER");
        assertThat(response.firstLogin()).isFalse();
    }
}
