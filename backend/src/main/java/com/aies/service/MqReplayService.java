package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.config.RabbitConfig;
import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.model.EvalMessage;
import com.aies.model.ParseMessage;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MQ 失败重放（管理端运维动作）。
 *
 * <p>只重放“未完成”的记录：解析重放要求当前不是 SUCCESS；评价重放要求解析已 SUCCESS
 * 且评价不是 AI_DONE/REVIEWED（已出分/已归档必须走教师端重评，避免管理端覆盖成绩）。
 * 状态先回 PENDING，事务提交后再投递消息；发送失败保留 PENDING 供下次重试。
 * 每次重放写审计日志，便于追踪运维操作。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqReplayService {

    private static final String PENDING = "PENDING";
    private static final String SUCCESS = "SUCCESS";
    private static final String AI_DONE = "AI_DONE";
    private static final String REVIEWED = "REVIEWED";

    private final SubmissionMapper submissionMapper;
    private final EvaluationResultMapper evaluationResultMapper;
    private final RabbitTemplate rabbitTemplate;
    private final AdminAuditService auditService;

    @Transactional
    public void replayParse(long submissionId, LoginPrincipal principal) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(submission.getCanceled())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "提交已取消，不能重放解析");
        }
        if (SUCCESS.equals(submission.getParseStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "解析已完成，无需重放");
        }
        submission.setParseStatus(PENDING);
        submission.setParsedTextKey(null);
        if (submissionMapper.updateById(submission) == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "解析重放冲突，请刷新后重试");
        }
        audit(principal, "REPLAY_PARSE", submissionId);
        sendAfterCommit(() -> rabbitTemplate.convertAndSend(RabbitConfig.PARSE_EXCHANGE,
                RabbitConfig.PARSE_ROUTING_KEY, new ParseMessage(submissionId)));
        log.info("解析重放已触发: submissionId={} operator={}", submissionId, principal.userId());
    }

    @Transactional
    public void replayEvaluation(long submissionId, LoginPrincipal principal) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(submission.getCanceled())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "提交已取消，不能重放评价");
        }
        if (!SUCCESS.equals(submission.getParseStatus()) || submission.getParsedTextKey() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件尚未解析成功，不能重放评价");
        }
        EvaluationResult existing = evaluationResultMapper.selectOne(
                new LambdaQueryWrapper<EvaluationResult>()
                        .eq(EvaluationResult::getSubmissionId, submissionId));
        if (existing != null && (AI_DONE.equals(existing.getStatus())
                || REVIEWED.equals(existing.getStatus()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "该提交已完成评价，请使用教师端重新评价");
        }
        if (existing == null) {
            existing = new EvaluationResult();
            existing.setSubmissionId(submissionId);
            existing.setStatus(PENDING);
            existing.setCurrentVersionNo(1);
            existing.setUpdateTime(LocalDateTime.now());
            evaluationResultMapper.insert(existing);
        } else {
            existing.setStatus(PENDING);
            existing.setErrorCode(null);
            existing.setUpdateTime(LocalDateTime.now());
            if (evaluationResultMapper.updateById(existing) == 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "评价重放冲突，请刷新后重试");
            }
        }
        audit(principal, "REPLAY_EVALUATION", submissionId);
        sendAfterCommit(() -> rabbitTemplate.convertAndSend(RabbitConfig.EVAL_EXCHANGE,
                RabbitConfig.EVAL_ROUTING_KEY, new EvalMessage(submissionId)));
        log.info("评价重放已触发: submissionId={} operator={}", submissionId, principal.userId());
    }

    private void audit(LoginPrincipal principal, String action, long submissionId) {
        auditService.record(principal, "mq", action, "submission:" + submissionId,
                Map.of("submissionId", submissionId));
    }

    /** 与上传/重评链同口径：事务提交后投递，避免 worker 早于状态可见。 */
    private void sendAfterCommit(Runnable sender) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeSend(sender);
                }
            });
        } else {
            safeSend(sender);
        }
    }

    private void safeSend(Runnable sender) {
        try {
            sender.run();
        } catch (Exception e) {
            // 状态已置 PENDING，保留给管理员再次重放；不让 broker 故障反噬接口
            log.error("MQ重放消息发送失败(留PENDING待重试): {}", e.getMessage());
        }
    }
}
