package com.aies.service;

import com.aies.entity.TraceCompensation;
import com.aies.mapper.TraceCompensationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 留痕补偿服务（V6：LLD §4.3步骤6"补偿日志表+定时任务重试补写"完整设计）。
 *
 * <p>入口：TraceRecorder冷桶写失败→enqueue（独立try/catch——补偿通道自身
 * DB故障降级ERROR日志，绝不反噬评价主链路，R2红线同源）。
 * 补写：@Scheduled 60s扫描PENDING且到期行→storeCold→DONE；失败指数退避
 * next_retry_time=now+2^(n-1)分钟（上限60分钟）；≥MAX_RETRY保留PENDING
 * 供人工兜底（ERROR级WARN日志，答辩证据链在表不在日志）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceCompensationService {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_DONE = "DONE";
    /** 重试上限（≥后保留PENDING人工兜底） */
    static final int MAX_RETRY = 10;
    /** 入队后首次补写延迟（秒） */
    static final long INITIAL_DELAY_SECONDS = 30;
    /** 退避上限（分钟） */
    static final long BACKOFF_CAP_MINUTES = 60;
    /** last_error截断长度（列宽512） */
    static final int ERROR_MAX_LEN = 500;

    private final TraceCompensationMapper traceCompensationMapper;
    private final FileStorage fileStorage;

    /** 补偿入队（TraceRecorder冷桶写失败调用；uk冲突/DB故障一律降级日志不抛） */
    public void enqueue(String traceId, String objectKey, byte[] payload, String reason) {
        try {
            TraceCompensation row = new TraceCompensation();
            row.setTraceId(traceId);
            row.setObjectKey(objectKey);
            row.setPayload(new String(payload, StandardCharsets.UTF_8));
            row.setStatus(STATUS_PENDING);
            row.setRetryCount(0);
            row.setLastError(abbreviate(reason));
            row.setNextRetryTime(LocalDateTime.now().plusSeconds(INITIAL_DELAY_SECONDS));
            traceCompensationMapper.insert(row);
            log.info("留痕补偿行已入队: traceId={} reason={}", traceId, reason);
        } catch (Exception e) {
            // 补偿通道自身故障：降级ERROR日志留traceId（人工兜底终点），不抛
            log.error("留痕补偿行入队失败(降级日志人工兜底): traceId={} err={}", traceId, e.getMessage());
        }
    }

    /** 定时补写（每60s扫描PENDING且到期行；单测可直接调retryOne验证单行流转） */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void retryPending() {
        List<TraceCompensation> rows = traceCompensationMapper.selectList(
                new LambdaQueryWrapper<TraceCompensation>()
                        .eq(TraceCompensation::getStatus, STATUS_PENDING)
                        .le(TraceCompensation::getNextRetryTime, LocalDateTime.now()));
        for (TraceCompensation row : rows) {
            retryOne(row);
        }
        if (!rows.isEmpty()) {
            log.info("留痕补偿扫描完成: 本轮处理{}行", rows.size());
        }
    }

    /** 单行补写（package-private供单测直证状态流转） */
    void retryOne(TraceCompensation row) {
        byte[] bytes = row.getPayload().getBytes(StandardCharsets.UTF_8);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            fileStorage.storeCold(row.getObjectKey(), in, bytes.length);
            row.setStatus(STATUS_DONE);
            row.setLastError(null);
            traceCompensationMapper.updateById(row);
            log.info("留痕补偿补写成功: traceId={} retryCount={}", row.getTraceId(), row.getRetryCount());
        } catch (Exception e) {
            int nextCount = row.getRetryCount() + 1;
            row.setRetryCount(nextCount);
            row.setLastError(abbreviate(e.getMessage()));
            row.setNextRetryTime(LocalDateTime.now().plusMinutes(nextBackoffMinutes(nextCount)));
            traceCompensationMapper.updateById(row);
            if (nextCount >= MAX_RETRY) {
                log.warn("留痕补偿重试达上限({})保留PENDING人工兜底: traceId={} err={}",
                        MAX_RETRY, row.getTraceId(), e.getMessage());
            }
        }
    }

    /** 指数退避2^(n-1)分钟，上限60分钟（防长等待溢出到天级） */
    static long nextBackoffMinutes(int retryCount) {
        return Math.min(1L << (retryCount - 1), BACKOFF_CAP_MINUTES);
    }

    private static String abbreviate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= ERROR_MAX_LEN ? message : message.substring(0, ERROR_MAX_LEN);
    }
}
