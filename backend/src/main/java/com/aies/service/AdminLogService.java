package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.admin.AdminDtos.AuditBucketVO;
import com.aies.dto.admin.AdminDtos.AuditStatsVO;
import com.aies.dto.admin.AdminDtos.AuditTrendVO;
import com.aies.dto.admin.AdminDtos.RuntimeLogPageVO;
import com.aies.dto.admin.AdminDtos.RuntimeLogStatsVO;
import com.aies.dto.admin.AdminDtos.RuntimeLogVO;
import com.aies.entity.AuditLog;
import com.aies.log.RuntimeLogCollector;
import com.aies.mapper.AuditLogMapper;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员日志采集与审计窗口（专属于 ADMIN）。
 *
 * <p>运行日志来自 {@link RuntimeLogCollector} 的内存采集缓冲；审计数据来自只追加的
 * audit_log 表。所有入口先校验 ADMIN 角色，越权返回 40301。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminLogService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final int TREND_DAYS = 7;
    private static final int ACTION_BUCKET_LIMIT = 8;

    private final RuntimeLogCollector logCollector;
    private final AuditLogMapper auditLogMapper;
    private final AdminAuditService auditService;

    /** 运行日志分页（级别阈值/关键字/logger 过滤，附带采集指标） */
    public RuntimeLogPageVO runtimeLogs(long page, long size, String level, String keyword,
                                        String logger, LoginPrincipal principal) {
        requireAdmin(principal);
        RuntimeLogCollector.LogPage result = logCollector.query(page, size, level, keyword, logger);
        List<RuntimeLogVO> records = result.records().stream()
                .map(entry -> new RuntimeLogVO(entry.seq(), entry.time(), entry.level(),
                        entry.logger(), entry.thread(), entry.message(), entry.detail()))
                .toList();
        return new RuntimeLogPageVO(toStatsVO(logCollector.stats()), records, result.total(),
                page, size);
    }

    /** 清空内存采集缓冲（写入审计留痕） */
    public void clearRuntimeLogs(LoginPrincipal principal) {
        requireAdmin(principal);
        int removed = logCollector.clear();
        auditService.record(principal, "system", "CLEAR_RUNTIME_LOG", "runtime_log",
                Map.of("removed", removed));
    }

    /** 审计窗口概览：总量/今日/近24h + 模块/动作分布 + 近7日趋势 */
    public AuditStatsVO auditStats(LoginPrincipal principal) {
        requireAdmin(principal);
        LocalDateTime now = LocalDateTime.now();
        long total = auditLogMapper.selectCount(null);
        long today = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>()
                .ge(AuditLog::getCreateTime, now.toLocalDate().atStartOfDay()));
        long last24h = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>()
                .ge(AuditLog::getCreateTime, now.minusHours(24)));
        return new AuditStatsVO(total, today, last24h, buckets("module", 0),
                buckets("action", ACTION_BUCKET_LIMIT), trend());
    }

    private RuntimeLogStatsVO toStatsVO(RuntimeLogCollector.LogStats stats) {
        return new RuntimeLogStatsVO(stats.capacity(), stats.captured(), stats.total(),
                stats.errorCount(), stats.warnCount(), stats.startedAt(), stats.capturing());
    }

    private List<AuditBucketVO> buckets(String column, int limit) {
        QueryWrapper<AuditLog> wrapper = new QueryWrapper<>();
        wrapper.select(column + " AS label", "COUNT(*) AS total")
                .groupBy(column)
                .orderByDesc("COUNT(*)");
        if (limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return auditLogMapper.selectMaps(wrapper).stream()
                .map(row -> new AuditBucketVO(String.valueOf(pick(row, "label")),
                        number(pick(row, "total"))))
                .toList();
    }

    private List<AuditTrendVO> trend() {
        LocalDate today = LocalDate.now();
        QueryWrapper<AuditLog> wrapper = new QueryWrapper<>();
        wrapper.select("DATE(create_time) AS day", "COUNT(*) AS total")
                .ge("create_time", today.minusDays(TREND_DAYS - 1L).atStartOfDay())
                .groupBy("DATE(create_time)")
                .orderByAsc("DATE(create_time)");
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : auditLogMapper.selectMaps(wrapper)) {
            String day = String.valueOf(pick(row, "day"));
            counts.put(day.length() > 10 ? day.substring(0, 10) : day,
                    number(pick(row, "total")));
        }
        List<AuditTrendVO> result = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            result.add(new AuditTrendVO(day.toString(), counts.getOrDefault(day.toString(), 0L)));
        }
        return result;
    }

    private Object pick(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long number(Object value) {
        return value instanceof Number parsed ? parsed.longValue() : 0L;
    }

    private void requireAdmin(LoginPrincipal principal) {
        if (principal == null || !ROLE_ADMIN.equals(principal.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "日志与审计窗口仅管理员可访问");
        }
    }
}
