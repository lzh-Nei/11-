package com.aies.service;

import com.aies.config.RabbitConfig;
import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.model.EvalMessage;
import com.aies.model.ParseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步链超时补偿（由 {@link ScheduledMaintenanceService} 定时调用）。
 *
 * <p>覆盖“状态已落库但 MQ 消息丢失/消费中断”的窗口：解析链 PENDING/QUEUED 超时重投，
 * 评价链 PENDING 超时重投。消费端本身幂等（SUCCESS/非 PENDING 跳过），重复投递安全。
 * 单条投递失败只记日志，不中断同批其它消息。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncRecoveryService {

    private final SubmissionMapper submissionMapper;
    private final EvaluationResultMapper evaluationResultMapper;
    private final RabbitTemplate rabbitTemplate;

    /** 重投超时解析任务，返回成功投递条数 */
    public int recoverStuckParses(LocalDateTime now, int pendingMinutes, int queuedMinutes, int batch) {
        List<Submission> submissions = submissionMapper.selectStuckParses(
                now.minusMinutes(pendingMinutes), now.minusMinutes(queuedMinutes), batch);
        int sent = 0;
        for (Submission submission : submissions) {
            try {
                rabbitTemplate.convertAndSend(RabbitConfig.PARSE_EXCHANGE,
                        RabbitConfig.PARSE_ROUTING_KEY, new ParseMessage(submission.getId()),
                        new CorrelationData("recover-parse-" + submission.getId()));
                sent++;
                log.warn("解析任务超时补偿投递: submissionId={} status={}",
                        submission.getId(), submission.getParseStatus());
            } catch (RuntimeException e) {
                log.error("解析任务补偿投递失败: submissionId={} err={}",
                        submission.getId(), e.getMessage());
            }
        }
        return sent;
    }

    /** 重投超时评价任务；先刷新 update_time 防止多轮/多实例重复投递 */
    public int recoverStuckEvaluations(LocalDateTime now, int pendingMinutes, int batch) {
        List<EvaluationResult> results = evaluationResultMapper.selectStuckPending(
                now.minusMinutes(pendingMinutes), batch);
        int sent = 0;
        for (EvaluationResult result : results) {
            if (evaluationResultMapper.touchPending(result.getId(), now) == 0) {
                continue;
            }
            try {
                rabbitTemplate.convertAndSend(RabbitConfig.EVAL_EXCHANGE,
                        RabbitConfig.EVAL_ROUTING_KEY, new EvalMessage(result.getSubmissionId()),
                        new CorrelationData("recover-eval-" + result.getSubmissionId()));
                sent++;
                log.warn("评价任务超时补偿投递: submissionId={} resultId={}",
                        result.getSubmissionId(), result.getId());
            } catch (RuntimeException e) {
                log.error("评价任务补偿投递失败: submissionId={} err={}",
                        result.getSubmissionId(), e.getMessage());
            }
        }
        return sent;
    }
}
