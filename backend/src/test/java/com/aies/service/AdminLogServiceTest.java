package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.dto.admin.AdminDtos.AuditStatsVO;
import com.aies.dto.admin.AdminDtos.RuntimeLogPageVO;
import com.aies.log.RuntimeLogCollector;
import com.aies.mapper.AuditLogMapper;
import com.aies.security.LoginPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日志采集与审计窗口服务单测：管理员专属校验、运行日志映射、审计概览统计。
 */
class AdminLogServiceTest {

    private final LoginPrincipal admin = new LoginPrincipal(1L, "jti", "raw", "ADMIN", Long.MAX_VALUE);
    private final LoginPrincipal teacher = new LoginPrincipal(2L, "jti", "raw", "TEACHER",
            Long.MAX_VALUE);

    private RuntimeLogCollector collector;
    private AuditLogMapper auditLogMapper;
    private AdminAuditService auditService;
    private AdminLogService service;

    @BeforeEach
    void setUp() {
        collector = mock(RuntimeLogCollector.class);
        auditLogMapper = mock(AuditLogMapper.class);
        auditService = mock(AdminAuditService.class);
        service = new AdminLogService(collector, auditLogMapper, auditService);
    }

    @Test
    void nonAdminCannotAccessLogWindow() {
        assertThatThrownBy(() -> service.runtimeLogs(1, 20, null, null, null, teacher))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅管理员");
        assertThatThrownBy(() -> service.auditStats(teacher))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.clearRuntimeLogs(teacher))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void runtimeLogsMapsCollectorSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        RuntimeLogCollector.LogEntry entry = new RuntimeLogCollector.LogEntry(7L, now, "ERROR",
                "com.aies.test.Logger", "main", "采集测试", "stack");
        when(collector.query(1, 20, "ERROR", "采集", "aies"))
                .thenReturn(new RuntimeLogCollector.LogPage(List.of(entry), 1));
        when(collector.stats()).thenReturn(new RuntimeLogCollector.LogStats(2000, 1, 9, 2, 3,
                now, true));

        RuntimeLogPageVO result = service.runtimeLogs(1, 20, "ERROR", "采集", "aies", admin);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).level()).isEqualTo("ERROR");
        assertThat(result.stats().capacity()).isEqualTo(2000);
        assertThat(result.stats().errorCount()).isEqualTo(2);
    }

    @Test
    void clearRuntimeLogsWritesAuditRecord() {
        when(collector.clear()).thenReturn(12);

        service.clearRuntimeLogs(admin);

        verify(auditService).record(eq(admin), eq("system"), eq("CLEAR_RUNTIME_LOG"),
                eq("runtime_log"), eq(Map.of("removed", 12)));
    }

    @Test
    void auditStatsAggregatesBucketsAndTrend() {
        when(auditLogMapper.selectCount(any())).thenReturn(10L, 2L, 5L);
        Map<String, Object> bucket = Map.of("label", "system", "total", 4L);
        Map<String, Object> trendRow = Map.of("day", java.sql.Date.valueOf(LocalDate.now()),
                "total", 3L);
        when(auditLogMapper.selectMaps(any()))
                .thenReturn(List.of(bucket), List.of(bucket), List.of(trendRow));

        AuditStatsVO stats = service.auditStats(admin);

        assertThat(stats.total()).isEqualTo(10);
        assertThat(stats.today()).isEqualTo(2);
        assertThat(stats.last24h()).isEqualTo(5);
        assertThat(stats.byModule()).hasSize(1);
        assertThat(stats.byModule().get(0).label()).isEqualTo("system");
        assertThat(stats.byModule().get(0).count()).isEqualTo(4);
        assertThat(stats.trend()).hasSize(7);
        assertThat(stats.trend().get(6).count()).isEqualTo(3);
    }
}
