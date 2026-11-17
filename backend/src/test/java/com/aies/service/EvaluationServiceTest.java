package com.aies.service;

import com.aies.config.AiProperties;
import com.aies.entity.EvaluationHistory;
import com.aies.entity.EvaluationResult;
import com.aies.entity.LlmConfig;
import com.aies.entity.PromptTemplate;
import com.aies.entity.Submission;
import com.aies.entity.TaskMetric;
import com.aies.mapper.EvaluationHistoryMapper;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.LlmConfigMapper;
import com.aies.mapper.PromptTemplateMapper;
import com.aies.model.EvaluationOutcome;
import com.aies.service.EvaluationService.PreparedEvaluation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EvaluationService 单测（LLD §4.3 管道四场景必备：成功/重试后成功/耗尽40003/超时计入重试
 * ——04-05 §八；R6一律MockLlmClient式桩（Mockito直接mock LlmClient接口）；
 * backoff spy归零防测试拖慢；事务双写ADR-004形状验证）。
 */
class EvaluationServiceTest {

    private static final String TEMPLATE_TEXT =
            "请依据以下评价指标评分：{metrics}。作业文本：{parsed_text}。仅输出JSON。";

    private PromptTemplateMapper promptTemplateMapper;
    private LlmConfigMapper llmConfigMapper;
    private TaskService taskService;
    private FileStorage fileStorage;
    private LlmClient llmClient;
    private EvaluationResultMapper evaluationResultMapper;
    private EvaluationHistoryMapper evaluationHistoryMapper;
    private EvaluationService service;
    private EvaluationService svc;
    private LlmConfig cfg;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        promptTemplateMapper = mock(PromptTemplateMapper.class);
        llmConfigMapper = mock(LlmConfigMapper.class);
        taskService = mock(TaskService.class);
        fileStorage = mock(FileStorage.class);
        llmClient = mock(LlmClient.class);
        evaluationResultMapper = mock(EvaluationResultMapper.class);
        evaluationHistoryMapper = mock(EvaluationHistoryMapper.class);
        service = new EvaluationService(promptTemplateMapper, llmConfigMapper, taskService,
                fileStorage, llmClient, new PromptAssembler(new AiProperties()),
                new SchemaValidator(new ObjectMapper()),
                evaluationResultMapper, evaluationHistoryMapper,
                new ObjectMapper(), new AiProperties());
        svc = spy(service);
        // 指数退避归零（真实节奏1s/2s/4s，单测不能真睡）
        doNothing().when(svc).backoff(anyInt());

        cfg = new LlmConfig();
        cfg.setModelName("qwen2.5:7b");
        cfg.setTemperature(new BigDecimal("0.0"));
        cfg.setTimeoutSeconds(60);
        cfg.setMaxRetry(3);
    }

    private PromptTemplate template() {
        PromptTemplate template = new PromptTemplate();
        template.setId(1L);
        template.setVersionNo(1);
        template.setContent(TEMPLATE_TEXT);
        template.setEnabled(1);
        return template;
    }

    private PreparedEvaluation prep() {
        TaskMetric metric = new TaskMetric();
        metric.setMetricName("功能正确性");
        metric.setWeight(new BigDecimal("40"));
        TaskMetric metric2 = new TaskMetric();
        metric2.setMetricName("代码质量");
        metric2.setWeight(new BigDecimal("35"));
        when(taskService.listMetrics(1L)).thenReturn(List.of(metric, metric2));
        when(fileStorage.openStream(anyString())).thenReturn(
                new ByteArrayInputStream("作业正文".getBytes(StandardCharsets.UTF_8)));

        Submission submission = new Submission();
        submission.setId(5L);
        submission.setTaskId(1L);
        submission.setParsedTextKey("parsed/abc.txt");
        return svc.prepare(submission, template(), cfg);
    }

    private String validJson(String total, String a, String b) {
        return "{\"total_score\": " + total + ", \"metric_scores\": {\"功能正确性\": " + a
                + ", \"代码质量\": " + b + "}, \"ai_comment\": \"评语内容\"}";
    }

    /** 场景1（LLD§4.3）：首试通过——retryCount=0，不触发退避，指标分JSON序列化 */
    @Test
    void firstAttemptValidSucceedsWithZeroRetry() {
        when(llmClient.evaluate(anyString(), same(cfg))).thenReturn(
                new com.aies.model.LlmRawResult(validJson("75", "40", "35"), 500L, "qwen2.5:7b"));

        EvaluationOutcome outcome = svc.runPipeline(prep());

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.retryCount()).isZero();
        assertThat(outcome.totalScore()).isEqualByComparingTo("75");
        assertThat(outcome.metricScoresJson()).contains("功能正确性").contains("40");
        verify(svc, never()).backoff(anyInt());
    }

    /** 场景2：包裹但缺指标（剥markdown后仍非法）→退避重试→第二次通过（retryCount=1，退避1次） */
    @Test
    void wrappedThenValidRetriesOnce() {
        String wrappedInvalid = "```json\n{\"total_score\": 72, "
                + "\"metric_scores\": {\"功能正确性\": 40}, \"ai_comment\": \"缺代码质量\"}\n```";
        when(llmClient.evaluate(anyString(), same(cfg))).thenReturn(
                new com.aies.model.LlmRawResult(wrappedInvalid, 400L, "qwen2.5:7b"),
                new com.aies.model.LlmRawResult(validJson("72", "40", "32"), 400L, "qwen2.5:7b"));

        EvaluationOutcome outcome = svc.runPipeline(prep());

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.retryCount()).isEqualTo(1);
        verify(svc, times(1)).backoff(1);
        verify(svc, never()).backoff(2);
    }

    /** 场景3：三次非法耗尽→FAILED(40003语义)——R5不静默，failReason留痕
     * （三连均非法：缺指标+超权重/超权重/纯文本，确保不提前命中合法响应） */
    @Test
    void threeInvalidResponsesExhaustToFailed() {
        when(llmClient.evaluate(anyString(), same(cfg))).thenReturn(
                new com.aies.model.LlmRawResult(
                        "{\"total_score\": 50, \"metric_scores\": {\"功能正确性\": 50}, \"ai_comment\": \"缺指标\"}",
                        300L, "qwen2.5:7b"),
                new com.aies.model.LlmRawResult(validJson("75", "45", "35"), 300L, "qwen2.5:7b"),
                new com.aies.model.LlmRawResult("完全不是JSON", 300L, "qwen2.5:7b"));

        EvaluationOutcome outcome = svc.runPipeline(prep());

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retryCount()).isEqualTo(3);
        assertThat(outcome.failReason()).isNotBlank();
        assertThat(outcome.rawResponse()).isEqualTo("完全不是JSON");
        verify(llmClient, times(3)).evaluate(anyString(), same(cfg));
        verify(svc, times(2)).backoff(anyInt());
    }

    /** 场景4：超时（LlmInvocationException）计入重试→后续成功（规范§三超时语义） */
    @Test
    void invocationExceptionCountsAsRetryThenSucceeds() {
        when(llmClient.evaluate(anyString(), same(cfg)))
                .thenThrow(new LlmInvocationException("Ollama调用超时(60s)"))
                .thenReturn(new com.aies.model.LlmRawResult(validJson("66", "36", "30"), 900L, "qwen2.5:7b"));
        EvaluationOutcome outcome = svc.runPipeline(prep());

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.retryCount()).isEqualTo(1);
        assertThat(outcome.elapsedMs()).isEqualTo(900L);
    }

    /** prepare：模板+指标SPI+文本三源组装（R1旁证——prompt含模板占位替换产物） */
    @Test
    void prepareAssemblesPromptFromThreeSources() {
        PreparedEvaluation prepared = prep();

        assertThat(prepared.promptText()).contains("功能正确性（权重40）")
                .contains("作业正文").doesNotContain("{parsed_text}");
        assertThat(prepared.weightCaps()).containsEntry("功能正确性", new BigDecimal("40"));
        assertThat(prepared.traceId()).matches("^[a-f0-9]{32}$");
    }

    /** digest：长度+前200字符摘要（留痕防全量敏感文本） */
    @Test
    void digestTruncatesPrompt() {
        String digest = svc.digest("x".repeat(500));

        assertThat(digest).startsWith("len=500;").hasSizeLessThan(300);
    }

    /** 事务双写（ADR-004）：新result插入AI_DONE v1 + history v1 同批落库 */
    @Test
    void persistSuccessInsertsResultAndHistory() {
        when(evaluationResultMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        EvaluationOutcome outcome = new EvaluationOutcome(true, new BigDecimal("80"),
                "{\"功能正确性\":40,\"代码质量\":40}", "评语", 0, 500L, "qwen2.5:7b", null,
                "{\"total_score\":80}");

        EvaluationResult saved = svc.persistEvaluation(5L, "trace0001", outcome,
                EvaluationService.SOURCE_AI_INITIAL);

        assertThat(saved.getStatus()).isEqualTo("AI_DONE");
        assertThat(saved.getCurrentVersionNo()).isEqualTo(1);
        assertThat(saved.getUpdateTime()).isNotNull();
        ArgumentCaptor<EvaluationResult> resultCap = ArgumentCaptor.forClass(EvaluationResult.class);
        verify(evaluationResultMapper).insert(resultCap.capture());
        ArgumentCaptor<EvaluationHistory> histCap = ArgumentCaptor.forClass(EvaluationHistory.class);
        verify(evaluationHistoryMapper).insert(histCap.capture());
        assertThat(histCap.getValue().getEvaluationId()).isEqualTo(resultCap.getValue().getId());
        assertThat(histCap.getValue().getVersionNo()).isEqualTo(1);
        assertThat(histCap.getValue().getSource()).isEqualTo("AI_INITIAL");
        assertThat(histCap.getValue().getTraceId()).isEqualTo("trace0001");
    }

    /** FAILED路径：error_code=40003、history不写（total_score NOT NULL，失败无版本行） */
    @Test
    void persistFailedMarks40003WithoutHistory() {
        when(evaluationResultMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        EvaluationResult saved = svc.persistEvaluation(5L, "trace0002",
                EvaluationOutcome.failed("重试耗尽"), EvaluationService.SOURCE_AI_INITIAL);

        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(saved.getErrorCode()).isEqualTo(40003);
        assertThat(saved.getUpdateTime()).isNotNull();
        verify(evaluationResultMapper).insert(saved);
        verify(evaluationHistoryMapper, never()).insert(any(EvaluationHistory.class));
    }

    /** PENDING残留（前次崩溃）复用行：update而非insert，history仍v1（uk_eval_version防重号） */
    @Test
    void persistReusesPendingRowWithUpdate() {
        EvaluationResult pending = new EvaluationResult();
        pending.setId(9L);
        pending.setSubmissionId(5L);
        pending.setStatus("PENDING");
        pending.setCurrentVersionNo(1);
        pending.setReviewerId(2L);
        pending.setReviewTime(java.time.LocalDateTime.now());
        when(evaluationResultMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pending);
        EvaluationOutcome outcome = new EvaluationOutcome(true, new BigDecimal("70"),
                "{}", "评语", 0, 100L, "qwen2.5:7b", null, null);

        svc.persistEvaluation(5L, "trace0003", outcome, EvaluationService.SOURCE_AI_INITIAL);

        assertThat(pending.getReviewerId()).isNull();
        assertThat(pending.getReviewTime()).isNull();

        verify(evaluationResultMapper).updateById(pending);
        verify(evaluationResultMapper, never()).insert(any(EvaluationResult.class));
        ArgumentCaptor<EvaluationHistory> histCap = ArgumentCaptor.forClass(EvaluationHistory.class);
        verify(evaluationHistoryMapper).insert(histCap.capture());
        assertThat(histCap.getValue().getEvaluationId()).isEqualTo(9L);
    }

    /** 启用版模板缺失→null（LLD步骤1：告警+FAILED由调用方承载） */
    @Test
    void loadTemplateReturnsNullWhenMissing() {
        when(promptTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThat(svc.loadTemplate()).isNull();
    }

    /** llm_config缺失→IllegalState（R4参数唯一来源，缺失阻断评价链） */
    @Test
    void loadLlmConfigFailsFastWhenMissing() {
        when(llmConfigMapper.selectList(any())).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(svc::loadLlmConfig)
                .isInstanceOf(IllegalStateException.class);
    }

    /** Map.copyOfcaps：weightCaps不可变防御（管道只读） */
    @Test
    void weightCapsIsImmutable() {
        PreparedEvaluation prepared = prep();

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> prepared.weightCaps().put("x", BigDecimal.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
