package com.aies.service;

import com.aies.entity.TraceCompensation;
import com.aies.mapper.TraceCompensationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TraceCompensationService 单测（V6：入队降级不抛 + 补写DONE流转 + 失败指数退避 + 上限兜底）。
 */
class TraceCompensationServiceTest {

    private TraceCompensationMapper traceCompensationMapper;
    private FileStorage fileStorage;
    private TraceCompensationService service;

    @BeforeEach
    void setUp() {
        traceCompensationMapper = mock(TraceCompensationMapper.class);
        fileStorage = mock(FileStorage.class);
        service = new TraceCompensationService(traceCompensationMapper, fileStorage);
    }

    private TraceCompensation row() {
        TraceCompensation row = new TraceCompensation();
        row.setId(1L);
        row.setTraceId("t1");
        row.setObjectKey("trace/t1.json");
        row.setPayload("{\"trace_id\":\"t1\",\"schema_valid\":true}");
        row.setStatus("PENDING");
        row.setRetryCount(0);
        row.setNextRetryTime(LocalDateTime.now().minusMinutes(1));
        return row;
    }

    /** 入队：PENDING行落库（payload原文/30s后首补/原因截断留存） */
    @Test
    void enqueueInsertsPendingRow() {
        byte[] payload = "{\"trace_id\":\"t1\"}".getBytes(StandardCharsets.UTF_8);

        service.enqueue("t1", "trace/t1.json", payload, "minio down");

        ArgumentCaptor<TraceCompensation> captor = ArgumentCaptor.forClass(TraceCompensation.class);
        verify(traceCompensationMapper).insert(captor.capture());
        TraceCompensation inserted = captor.getValue();
        assertThat(inserted.getTraceId()).isEqualTo("t1");
        assertThat(inserted.getObjectKey()).isEqualTo("trace/t1.json");
        assertThat(inserted.getPayload()).isEqualTo("{\"trace_id\":\"t1\"}");
        assertThat(inserted.getStatus()).isEqualTo("PENDING");
        assertThat(inserted.getRetryCount()).isZero();
        assertThat(inserted.getNextRetryTime()).isAfter(LocalDateTime.now());
        assertThat(inserted.getLastError()).isEqualTo("minio down");
    }

    /** uk_comp_trace冲突（重复入队）→不抛（补偿通道绝不反噬主链路） */
    @Test
    void enqueueDuplicateTraceDoesNotThrow() {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        doThrow(new DuplicateKeyException("uk_comp_trace"))
                .when(traceCompensationMapper).insert(any(TraceCompensation.class));

        assertThatCode(() -> service.enqueue("t1", "trace/t1.json", payload, "r"))
                .doesNotThrowAnyException();
    }

    /** 补偿通道DB故障→降级不抛（R2同源纪律） */
    @Test
    void enqueueDbFailureDoesNotThrow() {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        doThrow(new RuntimeException("mysql down"))
                .when(traceCompensationMapper).insert(any(TraceCompensation.class));

        assertThatCode(() -> service.enqueue("t1", "trace/t1.json", payload, "r"))
                .doesNotThrowAnyException();
    }

    /** 补写成功：storeCold→DONE落库 */
    @Test
    void retryOneSuccessMarksDone() {
        TraceCompensation target = row();

        service.retryOne(target);

        ArgumentCaptor<InputStream> streamCap = ArgumentCaptor.forClass(InputStream.class);
        verify(fileStorage).storeCold(eq("trace/t1.json"), streamCap.capture(), anyLong());
        assertThat(target.getStatus()).isEqualTo("DONE");
        assertThat(target.getLastError()).isNull();
        verify(traceCompensationMapper).updateById(target);
    }

    /** 补写失败：retryCount+1 + PENDING保留 + 退避时间后移 + 失败原因留存 */
    @Test
    void retryOneFailureBacksOffAndKeepsPending() {
        TraceCompensation target = row();
        doThrow(new RuntimeException("minio still down"))
                .when(fileStorage).storeCold(contains("trace/"), any(InputStream.class), anyLong());
        LocalDateTime before = LocalDateTime.now();

        service.retryOne(target);

        assertThat(target.getStatus()).isEqualTo("PENDING");
        assertThat(target.getRetryCount()).isEqualTo(1);
        assertThat(target.getLastError()).isEqualTo("minio still down");
        assertThat(target.getNextRetryTime()).isAfter(before);
        verify(traceCompensationMapper).updateById(target);
    }

    /** 重试达上限（MAX_RETRY）→仍保留PENDING人工兜底（不删不改payload） */
    @Test
    void retryOneAtMaxRetryStillKeepsPending() {
        TraceCompensation target = row();
        target.setRetryCount(TraceCompensationService.MAX_RETRY);
        doThrow(new RuntimeException("minio down"))
                .when(fileStorage).storeCold(contains("trace/"), any(InputStream.class), anyLong());

        service.retryOne(target);

        assertThat(target.getStatus()).isEqualTo("PENDING");
        assertThat(target.getRetryCount()).isEqualTo(TraceCompensationService.MAX_RETRY + 1);
    }

    /** 指数退避：2^(n-1)分钟且上限60（1→1, 2→2, 7→64→cap 60） */
    @Test
    void backoffMinutesExponentialWithCap() {
        assertThat(TraceCompensationService.nextBackoffMinutes(1)).isEqualTo(1);
        assertThat(TraceCompensationService.nextBackoffMinutes(2)).isEqualTo(2);
        assertThat(TraceCompensationService.nextBackoffMinutes(7)).isEqualTo(60);
        assertThat(TraceCompensationService.nextBackoffMinutes(20)).isEqualTo(60);
    }

    /** 定时扫描：仅处理到期PENDING行（wrapper条件由SQL承载，此处验证批量流转） */
    @Test
    void retryPendingProcessesAllDueRows() {
        TraceCompensation due1 = row();
        TraceCompensation due2 = row();
        due2.setId(2L);
        due2.setTraceId("t2");
        due2.setObjectKey("trace/t2.json");
        when(traceCompensationMapper.selectList(any())).thenReturn(java.util.List.of(due1, due2));

        service.retryPending();

        verify(fileStorage).storeCold(eq("trace/t1.json"), any(InputStream.class), anyLong());
        verify(fileStorage).storeCold(eq("trace/t2.json"), any(InputStream.class), anyLong());
        assertThat(due1.getStatus()).isEqualTo("DONE");
        assertThat(due2.getStatus()).isEqualTo("DONE");
    }
}
