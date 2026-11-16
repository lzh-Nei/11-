package com.aies.controller;

import com.aies.model.ReportOverviewDTO;
import com.aies.security.LoginPrincipal;
import com.aies.service.ReportQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 报表控制器 MockMvc 契约测试（@PreAuthorize负例归实弹，同 EvaluationControllerTest 口径）。 */
class ReportControllerTest {

    private MockMvc mockMvc;
    private ReportQueryService reportQueryService;

    @BeforeEach
    void setUp() {
        reportQueryService = Mockito.mock(ReportQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReportController(reportQueryService)).build();
        LoginPrincipal principal = new LoginPrincipal(2L, "jti", "raw", "TEACHER",
                Long.MAX_VALUE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    @DisplayName("教师overview：200+四图JSON形状")
    void overviewReturnsDto() throws Exception {
        when(reportQueryService.overviewForTeacher(2L)).thenReturn(ReportOverviewDTO.empty());

        mockMvc.perform(get("/api/reports/overview").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tasks").isArray())
                .andExpect(jsonPath("$.data.scoreDist.length()").value(5))
                .andExpect(jsonPath("$.data.scoreGapDist.length()").value(5));
    }

    @Test
    @DisplayName("学生overview/me：200（端点层无studentId参数——D-36确认点1）")
    void overviewMePassesPrincipalUserId() throws Exception {
        when(reportQueryService.overviewForMe(anyLong())).thenReturn(ReportOverviewDTO.empty());

        mockMvc.perform(get("/api/reports/overview/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(reportQueryService).overviewForMe(2L);
    }

    @Test
    @DisplayName("refresh：教师scope删键")
    void refreshDeletesTeacherScope() throws Exception {
        mockMvc.perform(post("/api/reports/refresh")).andExpect(status().isOk());
        verify(reportQueryService).refresh("teacher", 2L);
    }
}
