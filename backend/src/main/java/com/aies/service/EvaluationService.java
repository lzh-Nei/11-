package com.aies.service;

import com.aies.config.AiProperties;
import com.aies.entity.LlmConfig;
import com.aies.entity.PromptTemplate;
import com.aies.entity.Submission;
import com.aies.entity.TaskMetric;
import com.aies.mapper.LlmConfigMapper;
import com.aies.mapper.PromptTemplateMapper;
import com.aies.model.EvaluationOutcome;
import com.aies.service.PromptAssembler.AssembledPrompt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI评价管道核心（04-04 LLD §四 ADR-001落地；UC-20 MQ内部链路的执行体）。
 *
 * <p>主流程（LLD §4.3）：①取启用版模板+task指标集SPI组装 ②调LlmClient
 * ③Schema校验 ④≤3次指数退避1s/2s/4s ⑤事务双写result+history（ADR-004）
 * ⑥留痕冷桶（提交后独立写，红线R2）。LLM重试循环在事务外（60s×3不占连接）。</p>
 *
 * <p>红线对账：R1 Prompt走模板表（Assembler零字面量）；R3 LlmClient唯一出口；
 * R4 参数一律llm_config；R5 校验失败不静默；R6 单测MockLlmClient。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    /** 评价状态机字典（V4 §7.1） */
    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_AI_DONE = "AI_DONE";
    static final String STATUS_REVIEWED = "REVIEWED";
    static final String STATUS_FAILED = "FAILED";
    /** history.source 字典：AI初评 */
    static final String SOURCE_AI_INITIAL = "AI_INITIAL";

    private final PromptTemplateMapper promptTemplateMapper;
    private final LlmConfigMapper llmConfigMapper;
    private final TaskService taskService;
    private final FileStorage fileStorage;
    private final LlmClient llmClient;
    private final PromptAssembler promptAssembler;
    private final SchemaValidator schemaValidator;
    private final com.aies.mapper.EvaluationResultMapper evaluationResultMapper;
    private final com.aies.mapper.EvaluationHistoryMapper evaluationHistoryMapper;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    /**
     * 评价请求准备（非事务：读模板/配置/指标/文本并组装Prompt）。
     *
     * @param template 启用版模板（null→调用方走FAILED前置路径）
     * @param cfg      llm_config单行
     */
    public PreparedEvaluation prepare(Submission submission, PromptTemplate template, LlmConfig cfg) {
        String text = readParsedText(submission.getParsedTextKey());
        List<TaskMetric> metrics = taskService.listMetrics(submission.getTaskId());
        Map<String, BigDecimal> weightCaps = new LinkedHashMap<>();
        for (TaskMetric metric : metrics) {
            weightCaps.put(metric.getMetricName(), metric.getWeight());
        }
        AssembledPrompt prompt = promptAssembler.assemble(template.getContent(), metrics, text);
        return new PreparedEvaluation(submission.getId(), template, cfg,
                prompt.text(), Map.copyOf(weightCaps), newTraceId());
    }

    /**
     * LLM重试循环（非事务）。≤max_retry次（llm_config锁定），退避1s/2s/4s；
     * 超时/网络/非200与Schema不合法同等计入重试（LLD §4.3步骤2~4）。
     */
    public EvaluationOutcome runPipeline(PreparedEvaluation prep) {
        int maxRetry = prep.cfg().getMaxRetry();
        LlmConfig cfg = prep.cfg();
        String lastRaw = null;
        String lastModel = cfg.getModelName();
        String lastReason = null;
        boolean lastValid = false;
        long totalElapsed = 0;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                com.aies.model.LlmRawResult raw = llmClient.evaluate(prep.promptText(), cfg);
                lastRaw = raw.rawResponse();
                lastModel = raw.modelVersion();
                totalElapsed += raw.elapsedMs();
                ValidationResultHolder holder = validate(raw, prep);
                lastValid = holder.valid();
                lastReason = holder.reason();
                if (lastValid) {
                    log.info("评分成功(attempt {}/{}): submissionId={} total={} retry={} elapsed={}ms",
                            attempt, maxRetry, prep.submissionId(), holder.result().totalScore(),
                            attempt - 1, totalElapsed);
                    return new EvaluationOutcome(true, holder.result().totalScore(),
                            objectMapper.writeValueAsString(holder.result().metricScores()),
                            holder.result().aiComment(), attempt - 1, totalElapsed,
                            lastModel, null, lastRaw);
                }
                // R5红线：校验失败必须显式记录，禁止静默通过
                log.warn("Schema校验失败(attempt {}/{}): submissionId={} reason={}",
                        attempt, maxRetry, prep.submissionId(), lastReason);
            } catch (LlmInvocationException e) {
                // 超时/网络/非200统一入口：计为一次失败重试（规范§三超时语义）
                lastValid = false;
                lastReason = "LLM调用失败: " + e.getMessage();
                log.warn("LLM调用失败(attempt {}/{}): submissionId={} err={}",
                        attempt, maxRetry, prep.submissionId(), e.getMessage());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // metricScores 序列化失败（理论不可达，防御兜底）
                throw new IllegalStateException("指标分JSON序列化失败", e);
            }
            if (attempt < maxRetry) {
                backoff(attempt);
            }
        }
        log.warn("评分重试耗尽(→FAILED 40003): submissionId={} attempts={} reason={}",
                prep.submissionId(), maxRetry, lastReason);
        return new EvaluationOutcome(false, null, null, null, maxRetry, totalElapsed,
                lastModel, lastReason == null ? "未知失败原因" : lastReason, lastRaw);
    }

    private record ValidationResultHolder(boolean valid, String reason, com.aies.model.ValidationResult result) {
    }

    private ValidationResultHolder validate(com.aies.model.LlmRawResult raw, PreparedEvaluation prep) {
        com.aies.model.ValidationResult vr = schemaValidator.validate(raw.rawResponse(), prep.weightCaps());
        return new ValidationResultHolder(vr.valid(), vr.reason(), vr);
    }

    /** 指数退避1s/2s/4s（虚拟线程Thread.sleep合法；protected供单测spy归零） */
    protected void backoff(int attempt) {
        try {
            Thread.sleep(1000L << (attempt - 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 事务落库（LLD §4.3步骤5：result当前值+history版本同事务双写，ADR-004）。
     * 必须经代理外部调用（Worker/Replay），类内自调用不走事务。
     *
     * <p>FAILED路径只更新result（history.total_score NOT NULL，失败无版本行）；
     * 成功路径 result→AI_DONE + history插入（uk_eval_version防重号）。</p>
     */
    @Transactional
    public com.aies.entity.EvaluationResult persistEvaluation(long submissionId, String traceId,
                                                              EvaluationOutcome outcome, String source) {
        com.aies.entity.EvaluationResult result = evaluationResultMapper.selectOne(
                new LambdaQueryWrapper<com.aies.entity.EvaluationResult>()
                        .eq(com.aies.entity.EvaluationResult::getSubmissionId, submissionId));
        boolean isNew = result == null;
        if (isNew) {
            result = new com.aies.entity.EvaluationResult();
            result.setSubmissionId(submissionId);
            result.setStatus(STATUS_PENDING);
            result.setCurrentVersionNo(1);
        }
        if (!outcome.success()) {
            result.setStatus(STATUS_FAILED);
            result.setErrorCode(40003);
            result.setTotalScore(null);
            result.setMetricScores(null);
            result.setAiComment(null);
            result.setAiModel(outcome.modelVersion());
            result.setTraceId(traceId);
            if (isNew) {
                evaluationResultMapper.insert(result);
            } else {
                evaluationResultMapper.updateById(result);
            }
            return result;
        }
        int versionNo = result.getCurrentVersionNo() == null ? 1 : result.getCurrentVersionNo();
        result.setStatus(STATUS_AI_DONE);
        result.setErrorCode(null);
        result.setTotalScore(outcome.totalScore());
        result.setMetricScores(outcome.metricScoresJson());
        result.setAiComment(outcome.aiComment());
        result.setAiModel(outcome.modelVersion());
        result.setTraceId(traceId);
        result.setCurrentVersionNo(versionNo);
        if (isNew) {
            evaluationResultMapper.insert(result);
        } else {
            evaluationResultMapper.updateById(result);
        }
        com.aies.entity.EvaluationHistory history = new com.aies.entity.EvaluationHistory();
        history.setEvaluationId(result.getId());
        history.setSubmissionId(submissionId);
        history.setVersionNo(versionNo);
        history.setSource(source);
        history.setTotalScore(outcome.totalScore());
        history.setMetricScores(outcome.metricScoresJson());
        history.setAiComment(outcome.aiComment());
        history.setAiModel(outcome.modelVersion());
        history.setTraceId(traceId);
        evaluationHistoryMapper.insert(history);
        log.info("评价落库完成: submissionId={} version={} total={} historyId={}",
                submissionId, versionNo, outcome.totalScore(), history.getId());
        return result;
    }

    /** 取启用版模板（模板缺失→null，LLD步骤1：告警+FAILED） */
    public PromptTemplate loadTemplate() {
        return promptTemplateMapper.selectOne(new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getEnabled, 1));
    }

    /** 取llm_config单行（R4：参数唯一来源；缺失→SYSTEM_ERROR阻断评价链） */
    public LlmConfig loadLlmConfig() {
        List<LlmConfig> rows = llmConfigMapper.selectList(null);
        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException("llm_config未初始化（V4种子缺失），评价链阻断");
        }
        return rows.get(0);
    }

    private String readParsedText(String parsedTextKey) {
        try (InputStream in = fileStorage.openStream(parsedTextKey)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("提取文本读取失败: key=" + parsedTextKey, e);
        }
    }

    /** traceId：UUID32（与对象key同形，冷桶 trace/{traceId}.json 直接可写） */
    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Prompt前200字符摘要（留痕request_payload_digest，防全量敏感文本入冷桶） */
    public String digest(String promptText) {
        if (promptText == null) {
            return "";
        }
        int limit = Math.min(promptText.length(), 200);
        return "len=" + promptText.length() + "; head=" + promptText.substring(0, limit);
    }

    /**
     * 评价请求准备产物（LLM调用前的全部输入；traceId贯穿MDC/日志/DB/留痕）。
     */
    public record PreparedEvaluation(long submissionId, PromptTemplate template, LlmConfig cfg,
                                     String promptText, Map<String, BigDecimal> weightCaps, String traceId) {
    }
}
