package com.aies.service;

import com.aies.entity.EvaluationResult;
import com.aies.entity.LlmConfig;
import com.aies.entity.PromptTemplate;
import com.aies.entity.Submission;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.model.EvalMessage;
import com.aies.model.EvaluationOutcome;
import com.aies.service.EvaluationService.PreparedEvaluation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EvaluationWorker 单测（UC-20 MQ消费者四支路：脏消息跳过/幂等跳过/正常链/前置失败收敛FAILED）。
 */
class EvaluationWorkerTest {

    private SubmissionMapper submissionMapper;
    private EvaluationResultMapper evaluationResultMapper;
    private EvaluationService evaluationService;
    private TraceRecorder traceRecorder;
    private EvaluationWorker worker;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        submissionMapper = mock(SubmissionMapper.class);
        evaluationResultMapper = mock(EvaluationResultMapper.class);
        evaluationService = mock(EvaluationService.class);
        traceRecorder = mock(TraceRecorder.class);
        worker = new EvaluationWorker(submissionMapper, evaluationResultMapper,
                evaluationService, traceRecorder, mock(com.aies.realtime.RealtimeNotifier.class));
        when(evaluationResultMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
    }

    private Submission submission(String parseStatus) {
        Submission submission = new Submission();
        submission.setId(5L);
        submission.setTaskId(1L);
        submission.setStudentId(3L);
        submission.setParseStatus(parseStatus);
        submission.setParsedTextKey("SUCCESS".equals(parseStatus) ? "parsed/a.txt" : null);
        return submission;
    }

    private PreparedEvaluation prep(long submissionId) {
        PromptTemplate template = new PromptTemplate();
        template.setVersionNo(1);
        template.setContent("模板");
        template.setEnabled(1);
        LlmConfig cfg = new LlmConfig();
        cfg.setModelName("qwen2.5:7b");
        cfg.setTemperature(BigDecimal.ZERO);
        cfg.setTimeoutSeconds(60);
        cfg.setMaxRetry(3);
        return new PreparedEvaluation(submissionId, template, cfg, "prompt", Map.of(), "trace0001");
    }

    /** 脏消息：提交不存在→跳过（不评价不落库） */
    @Test
    void missingSubmissionSkipped() {
        when(submissionMapper.selectById(9L)).thenReturn(null);

        worker.onMessage(new EvalMessage(9L));

        verify(evaluationService, never()).runPipeline(any());
        verify(evaluationService, never()).persistEvaluation(anyLong(), anyString(), any(), anyString());
    }

    /** 解析未SUCCESS（含parsedTextKey空）→跳过（评价不入队口径） */
    @Test
    void unParsedSubmissionSkipped() {
        when(submissionMapper.selectById(5L)).thenReturn(submission("QUEUED"));

        worker.onMessage(new EvalMessage(5L));

        verify(evaluationService, never()).runPipeline(any());
    }

    /** 幂等：result已AI_DONE（重复投递）→跳过（ADR-004：重评只走显式E-03路径） */
    @Test
    void duplicateDeliverySkippedWhenAlreadyEvaluated() {
        when(submissionMapper.selectById(5L)).thenReturn(submission("SUCCESS"));
        EvaluationResult done = new EvaluationResult();
        done.setSubmissionId(5L);
        done.setStatus("AI_DONE");
        when(evaluationResultMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(done);

        worker.onMessage(new EvalMessage(5L));

        verify(evaluationService, never()).runPipeline(any());
        verify(evaluationService, never()).persistEvaluation(anyLong(), anyString(), any(), anyString());
    }

    /** 正常链：prepare→runPipeline→persistEvaluation(AI_INITIAL)→trace（事务后留痕，R2） */
    @Test
    void happyPathRunsPipelineThenPersistsAndTraces() {
        when(submissionMapper.selectById(5L)).thenReturn(submission("SUCCESS"));
        PreparedEvaluation prepared = prep(5L);
        when(evaluationService.loadTemplate()).thenReturn(prepared.template());
        when(evaluationService.loadLlmConfig()).thenReturn(prepared.cfg());
        when(evaluationService.prepare(any(Submission.class), any(PromptTemplate.class), any(LlmConfig.class)))
                .thenReturn(prepared);
        when(evaluationService.digest(anyString())).thenReturn("len=6; head=prompt");
        when(evaluationService.runPipeline(prepared)).thenReturn(new EvaluationOutcome(
                true, new BigDecimal("80"), "{}", "评语", 0, 500L, "qwen2.5:7b", null, "raw"));
        when(evaluationService.persistEvaluation(eq(5L), eq("trace0001"), any(), anyString()))
                .thenReturn(new EvaluationResult());

        worker.onMessage(new EvalMessage(5L));

        ArgumentCaptor<EvaluationOutcome> outcomeCap = ArgumentCaptor.forClass(EvaluationOutcome.class);
        verify(evaluationService).persistEvaluation(eq(5L), eq("trace0001"),
                outcomeCap.capture(), eq(EvaluationService.SOURCE_AI_INITIAL));
        assertThat(outcomeCap.getValue().success()).isTrue();
        // 留痕在persist之后调用（红线R2：留痕禁入主事务——mock层面时序断言）
        verify(traceRecorder).record(eq("trace0001"), any(PromptTemplate.class),
                outcomeCap.capture(), eq("len=6; head=prompt"));
    }

    /** 模板缺失（LLD步骤1）：告警+FAILED落库（40003语义），不发起LLM调用 */
    @Test
    void missingTemplatePersistsFailedWithoutLlmCall() {
        when(submissionMapper.selectById(5L)).thenReturn(submission("SUCCESS"));
        when(evaluationService.loadTemplate()).thenReturn(null);
        when(evaluationService.persistEvaluation(eq(5L), anyString(), any(), anyString()))
                .thenReturn(new EvaluationResult());

        worker.onMessage(new EvalMessage(5L));

        ArgumentCaptor<EvaluationOutcome> outcomeCap = ArgumentCaptor.forClass(EvaluationOutcome.class);
        verify(evaluationService).persistEvaluation(eq(5L), anyString(),
                outcomeCap.capture(), anyString());
        assertThat(outcomeCap.getValue().success()).isFalse();
        assertThat(outcomeCap.getValue().failReason()).contains("prompt_template");
        verify(evaluationService, never()).runPipeline(any());
    }

    /** 前置失败（文本读取/配置缺失）→收敛FAILED落库（毒消息不死循环，ack口径）；留痕随行 */
    @Test
    void prepareFailureConvergedToFailedPersist() {
        when(submissionMapper.selectById(5L)).thenReturn(submission("SUCCESS"));
        PromptTemplate template = new PromptTemplate();
        template.setVersionNo(1);
        template.setEnabled(1);
        when(evaluationService.loadTemplate()).thenReturn(template);
        when(evaluationService.loadLlmConfig()).thenThrow(new IllegalStateException("llm_config未初始化"));
        when(evaluationService.persistEvaluation(eq(5L), anyString(), any(), anyString()))
                .thenReturn(new EvaluationResult());

        worker.onMessage(new EvalMessage(5L));

        ArgumentCaptor<EvaluationOutcome> outcomeCap = ArgumentCaptor.forClass(EvaluationOutcome.class);
        verify(evaluationService).persistEvaluation(eq(5L), anyString(),
                outcomeCap.capture(), anyString());
        assertThat(outcomeCap.getValue().success()).isFalse();
        assertThat(outcomeCap.getValue().failReason()).contains("前置失败");
        // 前置失败同样留痕（审计证据链需要失败轨迹；template=null由TraceRecorder容忍）
        verify(traceRecorder).record(anyString(), any(), outcomeCap.capture(), anyString());
    }
}
