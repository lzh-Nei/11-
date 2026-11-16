package com.aies.controller;

import com.aies.dto.LoginResponse;
import com.aies.dto.UserVO;
import com.aies.exception.ApiExceptionHandler;
import com.aies.security.LoginPrincipal;
import com.aies.service.AuthService;
import com.aies.service.CaptchaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController MockMvc 单测（A-01/A-03/A-09/A-02/user:me 契约形状：
 * S-13 统一文案、204登出、XFF取IP、未登录401/40101）。
 */
class AuthControllerTest {

    private AuthService authService;
    private CaptchaService captchaService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        captchaService = mock(CaptchaService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, captchaService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void login() {
        LoginPrincipal principal = new LoginPrincipal(3L, "jti", "raw", "STUDENT", Long.MAX_VALUE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A"));
    }

    /** 例1 A-01 登录：XFF首段取IP + 双token/role/firstLogin 契约形状 */
    @Test
    void loginReturnsTokenPairWithRole() throws Exception {
        when(authService.login(any(), eq("203.0.113.9")))
                .thenReturn(new LoginResponse("at-token", "rt-token", "STUDENT", true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.9, 10.10.0.1")
                        .content("""
                                {"username":"21010001","password":"Passw0rd",
                                 "captchaId":"c1","captchaCode":"abcd"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("at-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("rt-token"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.firstLogin").value(true));
    }

    /** 例2 A-03 刷新：RT旋转契约形状 */
    @Test
    void refreshReturnsRotatedTokenPair() throws Exception {
        when(authService.refresh("rt-old"))
                .thenReturn(new LoginResponse("at-new", "rt-new", "STUDENT", false));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"rt-old\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("at-new"))
                .andExpect(jsonPath("$.data.refreshToken").value("rt-new"));
    }

    /** 例3 A-09 忘记密码：S-13 统一文案（code=0，无论命中与否同形） */
    @Test
    void forgotPasswordReturnsUniformMessage() throws Exception {
        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"21010001","email":"stu@example.com",
                                 "captchaId":"c1","captchaCode":"abcd"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("如该账号存在，重置邮件已发送"));
    }

    /** 例4 A-02 登出：成功即204空体 */
    @Test
    void logoutReturns204() throws Exception {
        login();

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(authService).logout(any(LoginPrincipal.class));
    }

    /** 例5 user:me：角色+权限清单（D-21 增补字段契约形状） */
    @Test
    void meReturnsRolesAndPermissions() throws Exception {
        login();
        when(authService.currentUser()).thenReturn(new UserVO(3L, "21010001", "张三",
                null, "stu@example.com", List.of("STUDENT"), List.of("task:view")));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("21010001"))
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.permissions[0]").value("task:view"));
    }

    /** 例6 未登录（主体非LoginPrincipal）→ 控制器内currentPrincipal守卫 → 401/40101
     *  （口径：/me的鉴权在@PreAuthorize方法安全层，standalone不启用，负例由生产实弹W6覆盖） */
    @Test
    void logoutWithoutLoginPrincipalYields40101() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", "N/A"));

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }
}
