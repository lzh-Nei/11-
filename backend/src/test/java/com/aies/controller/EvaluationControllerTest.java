package com.aies.controller;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.EvaluationHistoryPageVO;
import com.aies.dto.EvaluationHistoryVO;
import com.aies.dto.EvaluationVO;
import com.aies.exception.ApiExceptionHandler;
import com.aies.security.LoginPrincipal;
import com.aies.service.EvaluationAppealService;
import com.aies.service.EvaluationCommandService;
import com.aies.service.EvaluationQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EvaluationController MockMvc 单测（03-03 §七 E-01~E-06契约形状：
 * E-01学生/教师视角trace_id、IDOR 40400同形——D-17映射；E-02~E-06 W8-M1五端点。
 * 注：@PreAuthorize负例归实弹覆盖（W6八步+登记口径），standalone不载方法安全）。
 */
class EvaluationControllerTest {

    private EvaluationQueryService evaluationQueryService;
    private EvaluationCommandService evaluationCommandService;
    private EvaluationAppealService evaluationAppealService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        evaluationQueryService = mock(EvaluationQueryService.class);
        evaluationCommandService = mock(EvaluationCommandService.class);
        evaluationAppealService = mock(EvaluationAppealService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new EvaluationController(evaluationQueryService, evaluationCommandService,
                                evaluationAppealService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void login(long userId, String role) {
        LoginPrincipal principal = new LoginPrincipal(userId, "jti", "raw", role, Long.MAX_VALUE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A"));
    }

    /** E-01形态（AI_DONE v1）；E-02/E-06响应另用reviewVo */
    private EvaluationVO vo(boolean withTraceId) {
        EvaluationVO vo = new EvaluationVO();
        vo.setId(7L);
        vo.setSubmissionId(5L);
        vo.setStatus("AI_DONE");
        vo.setCurrentVersionNo(1);
        vo.setTotalScore(new BigDecimal("80"));
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("功能正确性", new BigDecimal("40"));
        vo.setMetricScores(metrics);
        vo.setAiComment("评语");
        vo.setTraceId(withTraceId ? "trace0001" : null);
        return vo;
    }

    /** 复核/人工评分响应形态（REVIEWED v2） */
    private EvaluationVO reviewVo(boolean withTraceId) {
        EvaluationVO vo = vo(withTraceId);
        vo.setStatus("REVIEWED");
        vo.setCurrentVersionNo(2);
        return vo;
    }

    private static final String REVIEW_BODY = """
            {"totalScore":80,"metricScores":{"功能正确性":40,"代码质量":25,"文档规范":15},"reviewComment":"评分调整理由已核实作业内容"}
            """;

    /* ==================== E-01（既有） ==================== */

    /** 例1 学生视角：本人评价当前值全字段，trace_id 不出现（E-01教师视角限定） */
    @Test
    void studentViewOmitsTraceId() throws Exception {
        login(3L, "STUDENT");
        when(evaluationQueryService.getById(eq(7L), any())).thenReturn(vo(false));

        mockMvc.perform(get("/api/evaluations/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("AI_DONE"))
                .andExpect(jsonPath("$.data.currentVersionNo").value(1))
                .andExpect(jsonPath("$.data.totalScore").value(80))
                .andExpect(jsonPath("$.data.metricScores['功能正确性']").value(40))
                .andExpect(jsonPath("$.data.traceId").doesNotExist());
    }

    /** 例2 教师视角：trace_id 返回（E-01响应要点"教师视角另含trace_id"） */
    @Test
    void teacherViewIncludesTraceId() throws Exception {
        login(2L, "TEACHER");
        when(evaluationQueryService.getById(eq(7L), any())).thenReturn(vo(true));

        mockMvc.perform(get("/api/evaluations/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.traceId").value("trace0001"));
    }

    /** 例3 IDOR：学生查他人评价 → 40400同形（HTTP 404，D-17映射） */
    @Test
    void studentAccessingOthersEvaluationGets40400() throws Exception {
        login(3L, "STUDENT");
        when(evaluationQueryService.getById(eq(7L), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/evaluations/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /** 例4 不存在 → 404/40400同形 */
    @Test
    void missingEvaluationGets40400() throws Exception {
        login(3L, "STUDENT");
        when(evaluationQueryService.getById(eq(99L), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/evaluations/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /* ==================== E-02 复核（W8-M1） ==================== */

    /** E-02 复核成功：PUT /{id}/review → REVIEWED+新版本号（UC-22） */
    @Test
    void reviewReturnsUpdatedVo() throws Exception {
        login(2L, "TEACHER");
        when(evaluationCommandService.review(eq(7L), any(), any())).thenReturn(reviewVo(true));

        mockMvc.perform(put("/api/evaluations/7/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REVIEW_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.currentVersionNo").value(2));
    }

    /** E-02 理由为空 → 30007（HTTP 200业务码，异常流E1） */
    @Test
    void reviewBlankCommentGets30007() throws Exception {
        login(2L, "TEACHER");
        when(evaluationCommandService.review(eq(7L), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.REVIEW_REASON_REQUIRED));

        mockMvc.perform(put("/api/evaluations/7/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REVIEW_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30007));
    }

    /* ==================== E-03 重评（W8-M1） ==================== */

    /** E-03 重评触发：POST /{id}/re-evaluate → code=0（UC-23） */
    @Test
    void reEvaluateReturnsSuccess() throws Exception {
        login(2L, "TEACHER");

        mockMvc.perform(post("/api/evaluations/7/re-evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(evaluationCommandService).reEvaluate(eq(7L), any());
    }

    /** E-03 状态前置违规 → 30000（AI_DONE不可重评） */
    @Test
    void reEvaluateOnAiDoneGets30000() throws Exception {
        login(2L, "TEACHER");
        doThrow(new BusinessException(ErrorCode.PARAM_ERROR, "当前状态不允许重评"))
                .when(evaluationCommandService).reEvaluate(eq(7L), any());

        mockMvc.perform(post("/api/evaluations/7/re-evaluate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000));
    }

    /* ==================== E-04/E-05 历史（W8-M1） ==================== */

    /** E-04 历史分页：cursor/limit透传（UC-25；limit缺省传null走服务端默认20） */
    @Test
    void historyPagePassesCursorAndLimit() throws Exception {
        login(2L, "TEACHER");
        EvaluationHistoryVO item = new EvaluationHistoryVO();
        item.setVersionNo(2);
        item.setSource("TEACHER_REVIEW");
        item.setTotalScore(new BigDecimal("80"));
        EvaluationHistoryPageVO page = new EvaluationHistoryPageVO();
        page.setList(List.of(item));
        page.setHasMore(false);
        when(evaluationQueryService.getHistoryPage(eq(7L), isNull(), isNull(), any())).thenReturn(page);

        mockMvc.perform(get("/api/evaluations/7/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].versionNo").value(2))
                .andExpect(jsonPath("$.data.list[0].source").value("TEACHER_REVIEW"))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    /** E-05 单版本快照：完整字段（UC-25；教师视角traceId可见） */
    @Test
    void historyVersionReturnsSnapshot() throws Exception {
        login(2L, "TEACHER");
        EvaluationHistoryVO snapshot = new EvaluationHistoryVO();
        snapshot.setVersionNo(1);
        snapshot.setSource("AI_INITIAL");
        snapshot.setTotalScore(new BigDecimal("58"));
        snapshot.setAiComment("AI评语");
        snapshot.setTraceId("trace0001");
        when(evaluationQueryService.getVersion(eq(7L), eq(1), any())).thenReturn(snapshot);

        mockMvc.perform(get("/api/evaluations/7/history/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.source").value("AI_INITIAL"))
                .andExpect(jsonPath("$.data.totalScore").value(58))
                .andExpect(jsonPath("$.data.aiComment").value("AI评语"))
                .andExpect(jsonPath("$.data.traceId").value("trace0001"));
    }

    /* ==================== E-06 手动评分旁路（W8-M1） ==================== */

    /** E-06 manual成功：PUT /{id}/manual → REVIEWED（UC-24旁路） */
    @Test
    void manualScoreReturnsUpdatedVo() throws Exception {
        login(2L, "TEACHER");
        when(evaluationCommandService.manualScore(eq(7L), any(), any())).thenReturn(reviewVo(true));

        mockMvc.perform(put("/api/evaluations/7/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REVIEW_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("REVIEWED"));
    }

    /** E-06 前置违规（status非FAILED）→ 30000 */
    @Test
    void manualScoreOnWrongStatusGets30000() throws Exception {
        login(2L, "TEACHER");
        when(evaluationCommandService.manualScore(eq(7L), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "评价状态不允许该操作"));

        mockMvc.perform(put("/api/evaluations/7/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REVIEW_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000));
    }
}
