package com.aies.controller;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.SubmissionDetailVO;
import com.aies.dto.SubmissionVO;
import com.aies.exception.ApiExceptionHandler;
import com.aies.security.LoginPrincipal;
import com.aies.service.SubmissionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SubmissionController MockMvc 单测（S-01~S-04 契约形状 + 302跳转 + 全局异常映射：
 * 未登录401/40101、资源不存在404/40400——D-17 同形映射）。
 */
class SubmissionControllerTest {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private SubmissionService submissionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        submissionService = mock(SubmissionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SubmissionController(submissionService))
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

    private SubmissionVO vo() {
        SubmissionVO vo = new SubmissionVO();
        vo.setId(100L);
        vo.setTaskId(1L);
        vo.setStudentId(3L);
        vo.setVersionNo(3);
        vo.setFileName("a.docx");
        vo.setParseStatus("PENDING");
        return vo;
    }

    /** 例1 S-01 multipart 提交：契约形状 code=0 + 版本号 */
    @Test
    void submitReturnsRecordAndVersion() throws Exception {
        login();
        when(submissionService.submit(eq(1L), any(), any())).thenReturn(vo());

        mockMvc.perform(multipart("/api/submissions")
                        .file(new MockMultipartFile("file", "a.docx", DOCX_MIME, "x".getBytes()))
                        .param("taskId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.versionNo").value(3))
                .andExpect(jsonPath("$.data.parseStatus").value("PENDING"));
    }

    /** 例2 S-02 我的提交列表：task筛选+分页参数透传 */
    @Test
    void mineReturnsPagedPayload() throws Exception {
        login();
        when(submissionService.mine(eq(3L), eq(1L), eq(1L), eq(10L)))
                .thenReturn(new Page<>());

        mockMvc.perform(get("/api/submissions/mine")
                        .param("taskId", "1").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    /** 例3 S-03 详情契约：签名URL+预览+字符数 */
    @Test
    void detailReturnsContractFields() throws Exception {
        login();
        SubmissionDetailVO detail = new SubmissionDetailVO();
        detail.setId(5L);
        detail.setParseStatus("SUCCESS");
        detail.setDownloadUrl("https://minio.example/signed");
        detail.setTextPreview("正文");
        detail.setTextChars(2);
        when(submissionService.detail(eq(5L), any())).thenReturn(detail);

        mockMvc.perform(get("/api/submissions/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").value("https://minio.example/signed"))
                .andExpect(jsonPath("$.data.textChars").value(2));
    }

    /** 例4 S-04 下载：302 + Location=预签名URL */
    @Test
    void downloadRedirectsToSignedUrl302() throws Exception {
        login();
        when(submissionService.downloadUrl(eq(5L), any()))
                .thenReturn("https://minio.example/signed");

        mockMvc.perform(get("/api/submissions/5/download"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://minio.example/signed"));
    }

    /** 例5 未登录（无JWT主体）→ 401/40101 */
    @Test
    void unauthenticatedYields40101() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/submissions/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    /** 例6 业务40400 → HTTP 404（D-17 同形映射） */
    @Test
    void missingSubmissionMapsTo404With40400() throws Exception {
        login();
        when(submissionService.detail(eq(9L), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/submissions/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("请求的资源不存在"));
    }
}
