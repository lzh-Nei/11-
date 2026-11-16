package com.aies.service;

import com.aies.entity.PromptTemplate;
import com.aies.model.EvaluationOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TraceRecorder 单测（04-05 §六留痕字段口径 + 红线R2旁证：写失败不阻塞主链路；
 * W8-M1 V6：写失败→补偿表入队闭环）。
 */
class TraceRecorderTest {

    private FileStorage fileStorage;
    private TraceCompensationService traceCompensationService;
    private TraceRecorder recorder;

    @BeforeEach
    void setUp() {
        fileStorage = mock(FileStorage.class);
        traceCompensationService = mock(TraceCompensationService.class);
        recorder = new TraceRecorder(fileStorage, new ObjectMapper(), traceCompensationService);
    }

    private EvaluationOutcome outcome(boolean success) {
        return new EvaluationOutcome(success, success ? new BigDecimal("85") : null,
                success ? "{}" : null, success ? "评语" : null, 2, 1500L,
                "qwen2.5:7b", success ? null : "Schema校验失败", "raw-output");
    }

    private PromptTemplate template() {
        PromptTemplate template = new PromptTemplate();
        template.setVersionNo(1);
        return template;
    }

    /** 正常写冷桶：key=trace/{traceId}.json，字段全口径（trace_id/版本/temperature=0/valid/retry/elapsed） */
    @Test
    void recordWritesTraceJsonToColdBucket() throws Exception {
        ArgumentCaptor<InputStream> streamCap = ArgumentCaptor.forClass(InputStream.class);
        ArgumentCaptor<Long> sizeCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);

        recorder.record("abc123def456", template(), outcome(true), "len=10; head=p");

        verify(fileStorage).storeCold(keyCap.capture(), streamCap.capture(),
                sizeCap.capture());
        assertThat(keyCap.getValue()).isEqualTo("trace/abc123def456.json");
        JsonNode node = new ObjectMapper().readTree(
                new String(streamCap.getValue().readAllBytes(), StandardCharsets.UTF_8));
        assertThat(node.get("trace_id").asText()).isEqualTo("abc123def456");
        assertThat(node.get("prompt_template_version").asInt()).isEqualTo(1);
        assertThat(node.get("model_name").asText()).isEqualTo("qwen2.5:7b");
        assertThat(node.get("temperature").asDouble()).isZero();
        assertThat(node.get("schema_valid").asBoolean()).isTrue();
        assertThat(node.get("retry_count").asInt()).isEqualTo(2);
        assertThat(node.get("elapsed_ms").asLong()).isEqualTo(1500L);
        assertThat(node.get("request_payload_digest").asText()).isEqualTo("len=10; head=p");
    }

    /** FAILED也留痕：schema_valid=false + 失败原因（审计证据链需要失败轨迹） */
    @Test
    void failedOutcomeRecordedWithReason() throws Exception {
        ArgumentCaptor<InputStream> streamCap = ArgumentCaptor.forClass(InputStream.class);

        recorder.record("t2", template(), outcome(false), "d");

        verify(fileStorage).storeCold(eq("trace/t2.json"), streamCap.capture(), anyLong());
        JsonNode node = new ObjectMapper().readTree(
                new String(streamCap.getValue().readAllBytes(), StandardCharsets.UTF_8));
        assertThat(node.get("schema_valid").asBoolean()).isFalse();
        assertThat(node.get("schema_fail_reason").asText()).contains("Schema校验失败");
    }

    /** 冷桶写失败→不抛（R2不阻塞主链路）且不误入补偿表 */
    @Test
    void coldBucketFailureDoesNotPropagate() {
        doThrow(new RuntimeException("minio down"))
                .when(fileStorage).storeCold(contains("trace/"), any(InputStream.class), anyLong());

        assertThatCode(() -> recorder.record("t3", template(), outcome(true), "d"))
                .doesNotThrowAnyException();
    }

    /** V6闭环：冷桶写失败→补偿表入队（key同形+留痕JSON原文+失败原因） */
    @Test
    void coldBucketFailureEnqueuesCompensationRow() throws Exception {
        doThrow(new RuntimeException("minio down"))
                .when(fileStorage).storeCold(contains("trace/"), any(InputStream.class), anyLong());

        recorder.record("t3", template(), outcome(true), "d");

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> payloadCap = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> reasonCap = ArgumentCaptor.forClass(String.class);
        verify(traceCompensationService).enqueue(eq("t3"), keyCap.capture(),
                payloadCap.capture(), reasonCap.capture());
        assertThat(keyCap.getValue()).isEqualTo("trace/t3.json");
        JsonNode node = new ObjectMapper().readTree(new String(payloadCap.getValue(), StandardCharsets.UTF_8));
        assertThat(node.get("trace_id").asText()).isEqualTo("t3");
        assertThat(reasonCap.getValue()).contains("minio down");
    }

    /** 冷桶成功路径不触发补偿入队 */
    @Test
    void successfulRecordSkipsCompensation() {
        recorder.record("t5", template(), outcome(true), "d");

        verify(traceCompensationService, never()).enqueue(anyString(), anyString(), any(), anyString());
    }

    /** 前置失败形态（template/outcome字段null）也不抛 */
    @Test
    void nullFieldsTolerated() {
        assertThatCode(() -> recorder.record("t4", null,
                new EvaluationOutcome(false, null, null, null, 0, 0, null, "prompt_template缺失", null), ""))
                .doesNotThrowAnyException();
    }
}
