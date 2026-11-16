package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.config.RabbitConfig;
import com.aies.dto.EvaluationVO;
import com.aies.dto.ReviewEvaluationRequest;
import com.aies.entity.AuditLog;
import com.aies.entity.EvaluationHistory;
import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.entity.Task;
import com.aies.entity.TaskMetric;
import com.aies.mapper.AuditLogMapper;
import com.aies.mapper.EvaluationHistoryMapper;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.TaskMapper;
import com.aies.model.EvalMessage;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 评价命令域（03-03 §七 E-02/E-03/E-06；UC-22复核改分/UC-23重新AI评价/UC-24手动评分旁路，W8-M1）。
 *
 * <p>ADR-004承载：history为追加式版本账本（只增不改不删），nextVersion=已有最大version_no+1
 * （无行则1）；result只存当前值+乐观锁@Version串行化并发复核。FAILED从不写history行——
 * 故FAILED重评/人工评分写v1、AI_DONE复核写v2、REVIEWED重评写v3，uk_eval_version天然不重号。</p>
 *
 * <p>IDOR口径：STUDENT→40400同形；TEACHER须为task归属教师（task.teacher_id本人）否则40400；
 * ADMIN放行。重评触发置PENDING后经MQ投递（复用UC-20链路与worker幂等），消息在事务提交后
 * 发送（与SubmissionService解析链同口径：发送失败留PENDING登记人工/重放恢复）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationCommandService {

    /** history.source 字典：教师复核/人工评分 */
    static final String SOURCE_TEACHER_REVIEW = "TEACHER_REVIEW";

    /** 复核理由最小长度（03-02 §3.6：必填≥10字否则30007） */
    static final int MIN_COMMENT_LEN = 10;
    /** 总分上限（SchemaValidator同口径0~100） */
    static final BigDecimal SCORE_MAX = new BigDecimal("100");

    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_TEACHER = "TEACHER";

    private final EvaluationResultMapper evaluationResultMapper;
    private final EvaluationHistoryMapper evaluationHistoryMapper;
    private final SubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final TaskService taskService;
    private final AuditLogMapper auditLogMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    /** E-02 教师复核改分（UC-22：前置AI_DONE；同事务history新版本+result当前值更新） */
    @Transactional
    public EvaluationVO review(long evaluationId, ReviewEvaluationRequest cmd, LoginPrincipal principal) {
        return doReview(evaluationId, cmd, principal, EvaluationService.STATUS_AI_DONE);
    }

    /** E-06 手动评分旁路（UC-24：前置FAILED；复用复核链，source=TEACHER_REVIEW） */
    @Transactional
    public EvaluationVO manualScore(long evaluationId, ReviewEvaluationRequest cmd, LoginPrincipal principal) {
        return doReview(evaluationId, cmd, principal, EvaluationService.STATUS_FAILED);
    }

    /** E-03 触发重评（UC-23：前置REVIEWED/FAILED；置PENDING→MQ→管道写source=RE_EVALUATE新版本） */
    @Transactional
    public void reEvaluate(long evaluationId, LoginPrincipal principal) {
        AccessContext ctx = requireAccessible(evaluationId, principal);
        EvaluationResult result = ctx.result();
        if (!EvaluationService.STATUS_REVIEWED.equals(result.getStatus())
                && !EvaluationService.STATUS_FAILED.equals(result.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "当前状态不允许重评（当前" + result.getStatus() + "，仅REVIEWED/FAILED）");
        }
        if (!"SUCCESS".equals(ctx.submission().getParseStatus())
                || ctx.submission().getParsedTextKey() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "提交未完成解析，无法重评");
        }
        if (EvaluationService.STATUS_REVIEWED.equals(result.getStatus())) {
            // REVIEWED已有history版本（v1+）：bump版本号给重评新版本（uk_eval_version防重号）
            result.setCurrentVersionNo(result.getCurrentVersionNo() + 1);
        }
        // FAILED无history行：current_version_no原样保留（重评成功写v1）
        result.setStatus(EvaluationService.STATUS_PENDING);
        result.setErrorCode(null);
        if (evaluationResultMapper.updateById(result) == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "重评并发冲突，请刷新后重试");
        }
        auditReEvaluate(evaluationId, result.getSubmissionId(), principal);
        sendEvalMessageAfterCommit(result.getSubmissionId());
        log.info("重评已触发: evaluationId={} submissionId={} nextVersion={} operator={}",
                evaluationId, result.getSubmissionId(), result.getCurrentVersionNo(), principal.userId());
    }

    /* ==================== 私有：复核主体 ==================== */

    private EvaluationVO doReview(long evaluationId, ReviewEvaluationRequest cmd,
                                  LoginPrincipal principal, String expectedStatus) {
        AccessContext ctx = requireAccessible(evaluationId, principal);
        EvaluationResult result = ctx.result();
        if (!expectedStatus.equals(result.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "评价状态不允许该操作（当前" + result.getStatus()
                            + "，要求" + expectedStatus + "）");
        }
        String comment = cmd.getReviewComment() == null ? "" : cmd.getReviewComment().trim();
        if (comment.length() < MIN_COMMENT_LEN) {
            throw new BusinessException(ErrorCode.REVIEW_REASON_REQUIRED);
        }
        validateScores(cmd, ctx.submission().getTaskId());

        int nextVersion = nextVersionNo(evaluationId);
        // 乐观锁先更新当前值（并发复核在此串行化：后到者version过期→0行→30000，UC-22 E2）
        result.setStatus(EvaluationService.STATUS_REVIEWED);
        result.setTotalScore(cmd.getTotalScore());
        result.setMetricScores(serializeMetrics(cmd.getMetricScores()));
        result.setReviewerId(principal.userId());
        result.setReviewTime(LocalDateTime.now());
        result.setCurrentVersionNo(nextVersion);
        if (evaluationResultMapper.updateById(result) == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "复核并发冲突，请刷新后重试");
        }
        // ADR-004：新版本入history（同事务；先update后insert防uk_eval_version并发重号）
        EvaluationHistory history = new EvaluationHistory();
        history.setEvaluationId(evaluationId);
        history.setSubmissionId(result.getSubmissionId());
        history.setVersionNo(nextVersion);
        history.setSource(SOURCE_TEACHER_REVIEW);
        history.setTotalScore(cmd.getTotalScore());
        history.setMetricScores(result.getMetricScores());
        history.setAiComment(result.getAiComment());
        history.setTeacherComment(comment);
        history.setReviewerId(principal.userId());
        evaluationHistoryMapper.insert(history);
        log.info("复核完成: evaluationId={} version={} total={} reviewer={}",
                evaluationId, nextVersion, cmd.getTotalScore(), principal.userId());
        return toVo(result, cmd.getMetricScores());
    }

    /** 教师输入校验（对齐SchemaValidator纪律：键集/权重上限/Σ=总分±0.5/总分0~100） */
    private void validateScores(ReviewEvaluationRequest cmd, long taskId) {
        BigDecimal total = cmd.getTotalScore();
        if (total == null || total.compareTo(BigDecimal.ZERO) < 0 || total.compareTo(SCORE_MAX) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "总分超出0~100");
        }
        Map<String, BigDecimal> caps = new LinkedHashMap<>();
        for (TaskMetric metric : taskService.listMetrics(taskId)) {
            caps.put(metric.getMetricName(), metric.getWeight());
        }
        Map<String, BigDecimal> scores = cmd.getMetricScores();
        if (scores == null || !caps.keySet().equals(scores.keySet())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "指标集与任务指标不符");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : scores.entrySet()) {
            BigDecimal score = entry.getValue();
            if (score == null || score.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "指标分为负: " + entry.getKey());
            }
            BigDecimal cap = caps.get(entry.getKey());
            if (score.compareTo(cap) > 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "指标分超权重上限(" + entry.getKey() + "): " + score + ">" + cap);
            }
            sum = sum.add(score);
        }
        if (sum.subtract(total).abs().compareTo(SchemaValidator.SUM_TOLERANCE) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "指标分之和与总分不符: Σ" + sum + " vs " + total);
        }
    }

    private String serializeMetrics(Map<String, BigDecimal> metricScores) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(metricScores));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("指标分JSON序列化失败", e);
        }
    }

    /* ==================== 私有：重评支撑 ==================== */

    /** UC-23重评次数审计（audit_log首用，S-09仅追加） */
    private void auditReEvaluate(long evaluationId, long submissionId, LoginPrincipal principal) {
        AuditLog audit = new AuditLog();
        audit.setOperatorId(principal.userId());
        audit.setModule("evaluation");
        audit.setAction("RE_EVALUATE");
        audit.setTarget("evaluation:" + evaluationId);
        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("submissionId", submissionId);
        detail.put("operatorId", principal.userId());
        audit.setDetail(detail.toString());
        auditLogMapper.insert(audit);
    }

    /** 事务提交后投递重评消息（与SubmissionService解析链同口径；发送失败留PENDING登记恢复） */
    private void sendEvalMessageAfterCommit(long submissionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchEvalMessage(submissionId);
                }
            });
        } else {
            dispatchEvalMessage(submissionId);
        }
    }

    private void dispatchEvalMessage(long submissionId) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EVAL_EXCHANGE,
                    RabbitConfig.EVAL_ROUTING_KEY, new EvalMessage(submissionId));
            log.info("重评消息已投递: submissionId={}", submissionId);
        } catch (Exception e) {
            // 与解析链同口径：不让消息故障反噬接口；PENDING留存，恢复路径登记偏差（人工/重放）
            log.error("重评消息发送失败(留PENDING待恢复): submissionId={} err={}", submissionId, e.getMessage());
        }
    }

    /* ==================== 私有：归属校验与工具 ==================== */

    /** 归属校验（spec决策2）：不存在→40400；STUDENT→40400同形（无命令权限，防探测同口径）；
     *  TEACHER须为task归属教师否则40400；ADMIN放行 */
    private AccessContext requireAccessible(long evaluationId, LoginPrincipal principal) {
        EvaluationResult result = evaluationResultMapper.selectById(evaluationId);
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Submission submission = submissionMapper.selectById(result.getSubmissionId());
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (ROLE_STUDENT.equals(principal.role())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (ROLE_TEACHER.equals(principal.role())) {
            Task task = taskMapper.selectById(submission.getTaskId());
            if (task == null || task.getTeacherId() == null
                    || task.getTeacherId() != principal.userId()) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
        }
        return new AccessContext(result, submission);
    }

    /** nextVersion = 已有最大version_no+1（无行则1）；先result.updateById后history.insert，
     *  并发由乐观锁串行化，uk_eval_version天然不重号 */
    private int nextVersionNo(long evaluationId) {
        EvaluationHistory latest = evaluationHistoryMapper.selectOne(
                new LambdaQueryWrapper<EvaluationHistory>()
                        .eq(EvaluationHistory::getEvaluationId, evaluationId)
                        .orderByDesc(EvaluationHistory::getVersionNo)
                        .last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    /** 复核响应VO（metricScores直接取校验通过的入参映射，免二次JSON解析） */
    private EvaluationVO toVo(EvaluationResult result, Map<String, BigDecimal> metricScores) {
        EvaluationVO vo = new EvaluationVO();
        vo.setId(result.getId());
        vo.setSubmissionId(result.getSubmissionId());
        vo.setStatus(result.getStatus());
        vo.setCurrentVersionNo(result.getCurrentVersionNo());
        vo.setTotalScore(result.getTotalScore());
        vo.setMetricScores(metricScores);
        vo.setAiComment(result.getAiComment());
        vo.setErrorCode(result.getErrorCode());
        vo.setUpdateTime(result.getUpdateTime());
        vo.setTraceId(result.getTraceId());
        return vo;
    }

    /** 归属校验产物（result+submission一次装载，避免二次查询） */
    private record AccessContext(EvaluationResult result, Submission submission) {
    }
}
