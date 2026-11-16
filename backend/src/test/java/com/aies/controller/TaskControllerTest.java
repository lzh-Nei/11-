package com.aies.controller;

import com.aies.dto.TaskVO;
import com.aies.exception.ApiExceptionHandler;
import com.aies.security.LoginPrincipal;
import com.aies.service.TaskService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TaskController MockMvc 单测（T-01/T-03/T-04/T-05 契约形状 + @Valid 参数校验
 * BindException→400/30000 映射 + 未登录 401/40101）。
 */
class TaskControllerTest {

    private TaskService taskService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        // 与Boot生产配置对齐：LocalDateTime序列化为ISO串（默认mapper会给数组）
        mockMvc = MockMvcBuilders.standaloneSetup(new TaskController(taskService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json()
                                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                .build()))
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void login(String role) {
        LoginPrincipal principal = new LoginPrincipal(2L, "jti", "raw", role, Long.MAX_VALUE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A"));
    }

    private TaskVO taskVO() {
        TaskVO vo = new TaskVO();
        vo.setId(1L);
        vo.setTitle("单测任务");
        vo.setClassId(1L);
        vo.setStatus("DRAFT");
        vo.setDeadline(LocalDateTime.of(2026, 12, 1, 23, 59, 59));
        vo.setMetrics(List.of());
        return vo;
    }

    private String createBody() {
        return """
                {"title":"单测任务","classId":1,"deadline":"2026-12-01T23:59:59",
                 "metrics":[{"metricName":"文档规范","weight":50,"sortOrder":1},
                            {"metricName":"功能实现","weight":50,"sortOrder":2}]}
                """;
    }

    /** 例1 T-01 创建：契约形状 code=0 + DRAFT */
    @Test
    void createReturnsDraftTask() throws Exception {
        login("TEACHER");
        when(taskService.create(any(), any())).thenReturn(taskVO());

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.title").value("单测任务"));
    }

    /** 例2 @Valid 失败（标题缺失）→ BindException → 400/30000 */
    @Test
    void createWithBlankTitleMapsTo400And30000() throws Exception {
        login("TEACHER");

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":1,\"metrics\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000));
    }

    /** 例3 T-03 发布：POST /{id}/publish */
    @Test
    void publishReturnsPublishedTask() throws Exception {
        login("TEACHER");
        TaskVO published = taskVO();
        published.setStatus("PUBLISHED");
        when(taskService.publish(eq(1L), any())).thenReturn(published);

        mockMvc.perform(post("/api/tasks/1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    /** 例4 T-04 详情 */
    @Test
    void detailReturnsContractFields() throws Exception {
        login("TEACHER");
        when(taskService.getById(eq(1L), any())).thenReturn(taskVO());

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.deadline").value("2026-12-01T23:59:59"));
    }

    /** 例5 T-05 列表：分页+筛选参数透传 */
    @Test
    void listReturnsPagedPayload() throws Exception {
        login("STUDENT");
        Page<TaskVO> page = new Page<>(1, 10);
        page.setRecords(List.of(taskVO()));
        page.setTotal(1);
        when(taskService.list(eq(1L), eq(10L), eq(1L), eq("PUBLISHED"), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/tasks")
                        .param("page", "1").param("size", "10")
                        .param("classId", "1").param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("单测任务"));
    }

    /** 例6 未登录 → 401/40101 */
    @Test
    void unauthenticatedYields40101() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }
}
