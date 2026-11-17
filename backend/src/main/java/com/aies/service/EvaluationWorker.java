package com.aies.service;

import com.aies.config.RabbitConfig;
import com.aies.entity.EvaluationResult;
import com.aies.entity.PromptTemplate;
import com.aies.entity.Submission;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.model.EvalMessage;
import com.aies.model.EvaluationOutcome;
import com.aies.realtime.RealtimeNotifier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 评价 worker（UC-20：MQ系统内部链路消费者，03-03 §七"无REST触发接口"契约承载，W7-C2）。
 *
 * <p>链路：ParseWorker SUCCESS → eval.submit 消息 → 本worker → EvaluationService
 * 管道（LLM+Schema+重试）→ 事务双写（ADR-004）→ 留痕冷桶（事务后，红线R2）。</p>
 *
 * <p>可靠性口径（与 ParseWorker 同风格）：①幂等——result 已非 PENDING（AI_DONE/
 * REVIEWED/FAILED）直接跳过重复投递；②解析未SUCCESS的消息跳过（脏消息防御）；
 * ③管道内失败已收敛为 FAILED 落库后正常 ack；④事务回滚（DB故障）异常外抛→
 * 不requeue（application.yml default-requeue-rejected=false），submission 留
 * PENDING 可经 E-03 重评或重放工具恢复（登记口径）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationWorker {

    private final SubmissionMapper submissionMapper;
    private final EvaluationResultMapper evaluationResultMapper;
    private final EvaluationService evaluationService;
    private final TraceRecorder traceRecorder;
    private final RealtimeNotifier realtimeNotifier;

    @RabbitListener(queues = RabbitConfig.EVAL_QUEUE)
    public void onMessage(EvalMessage message) {
        long submissionId = message.submissionId();
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            log.warn("评价消息对应提交不存在，跳过: submissionId={}", submissionId);
            return;
        }
        // 解析未SUCCESS（含消息早于解析可见的极小窗口）：跳过，解析链补发可恢复
        if (!"SUCCESS".equals(submission.getParseStatus())
                || submission.getParsedTextKey() == null) {
            log.warn("评价消息对应提交解析未SUCCESS，跳过: submissionId={} parseStatus={}",
                    submissionId, submission.getParseStatus());
            return;
        }
        // 幂等：仅 PENDING（首次/前次崩溃残留）可进入管道，其余状态跳过重复投递
        EvaluationResult existing = evaluationResultMapper.selectOne(
                new LambdaQueryWrapper<EvaluationResult>()
                        .eq(EvaluationResult::getSubmissionId, submissionId));
        if (existing != null && !"PENDING".equals(existing.getStatus())) {
            log.info("重复投递跳过（result已{}）: submissionId={}", existing.getStatus(), submissionId);
            return;
        }

        String traceId;
        EvaluationOutcome outcome;
        PromptTemplate template;
        String digest;
        EvaluationResult saved;
        try {
            template = evaluationService.loadTemplate();
            if (template == null) {
                // LLD §4.3步骤1失败处理：模板缺失→告警+FAILED（无LLM调用，traceId即时生成留痕）
                log.error("启用版prompt_template缺失(→FAILED 40003): submissionId={}", submissionId);
                outcome = EvaluationOutcome.failed("prompt_template缺失（enabled=1无行）");
                template = null;
                traceId = java.util.UUID.randomUUID().toString().replace("-", "");
                digest = "";
            } else {
                var cfg = evaluationService.loadLlmConfig();
                var prep = evaluationService.prepare(submission, template, cfg);
                traceId = prep.traceId();
                digest = evaluationService.digest(prep.promptText());
                log.info("评价开始: submissionId={} traceId={} promptLen={}",
                        submissionId, traceId, prep.promptText().length());
                outcome = evaluationService.runPipeline(prep);
            }
            // 事务双写（外部调用经代理保事务）；异常外抛→不requeue，留PENDING可恢复
            saved = evaluationService.persistEvaluation(submissionId, traceId, outcome,
                    EvaluationService.SOURCE_AI_INITIAL);
        } catch (RuntimeException e) {
            // 前置失败（llm_config缺失/文本读取失败等）：收敛FAILED落库（40003），不让毒消息死循环
            log.error("评价前置失败(→FAILED 40003): submissionId={} err={}", submissionId, e.getMessage());
            outcome = EvaluationOutcome.failed("前置失败: " + e.getMessage());
            traceId = java.util.UUID.randomUUID().toString().replace("-", "");
            template = null;
            digest = "";
            saved = evaluationService.persistEvaluation(submissionId, traceId, outcome,
                    EvaluationService.SOURCE_AI_INITIAL);
        }
        realtimeNotifier.notifyEvaluation(submission, saved, EvaluationService.SOURCE_AI_INITIAL);
        // 步骤6：留痕写冷桶（独立于事务——persistEvaluation 事务已提交，R2红线达成）
        traceRecorder.record(traceId, template, outcome, digest);
    }
}
