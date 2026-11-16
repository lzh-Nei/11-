package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.dto.EvaluationVO;
import com.aies.dto.ReviewEvaluationRequest;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EvaluationCommandService 单测（03-03 §七 E-02/E-03/E-06：UC-22复核链全分支 +
 * UC-23重评版本语义 + UC-24旁路 + IDOR同形 + 乐观锁冲突；MQ发送在无事务同步上下文
 * 中直连（单测无TransactionSynchronization）→convertAndSend可直证）。
 */
class EvaluationCommandServiceTest {

    private EvaluationResultMapper evaluationResultMapper;
    private EvaluationHistoryMapper evaluationHistoryMapper;
    private SubmissionMapper submissionMapper;
    private TaskMapper taskMapper;
    private TaskService taskService;
    private AuditLogMapper auditLogMapper;
    private RabbitTemplate rabbitTemplate;
    private EvaluationCommandService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        evaluationResultMapper = mock(EvaluationResultMapper.class);
        evaluationHistoryMapper = mock(EvaluationHistoryMapper.class);
        submissionMapper = mock(SubmissionMapper.class);
        taskMapper = mock(TaskMapper.class);
        taskService = mock(TaskService.class);
        auditLogMapper = mock(AuditLogMapper.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        service = new EvaluationCommandService(evaluationResultMapper, evaluationHistoryMapper,
                submissionMapper, taskMapper, taskService, auditLogMapper, rabbitTemplate,
                new ObjectMapper(), mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    private LoginPrincipal principal(long userId, String role) {
        return new LoginPrincipal(userId, "jti", "raw", role, Long.MAX_VALUE);
    }

    private EvaluationResult result(String status, int versionNo) {
        EvaluationResult result = new EvaluationResult();
        result.setId(7L);
        result.setSubmissionId(5L);
        result.setStatus(status);
        result.setCurrentVersionNo(versionNo);
        result.setVersion(0);
        result.setAiComment("AI评语");
        return result;
    }

    private Submission submission(long studentId) {
        Submission submission = new Submission();
        submission.setId(5L);
        submission.setTaskId(1L);
        submission.setStudentId(studentId);
        submission.setParseStatus("SUCCESS");
        submission.setParsedTextKey("parsed/5.txt");
        return submission;
    }

    private Task task(long teacherId) {
        Task task = new Task();
        task.setId(1L);
        task.setTeacherId(teacherId);
        return task;
    }

    private List<TaskMetric> taskMetrics() {
        TaskMetric m1 = new TaskMetric();
        m1.setMetricName("功能正确性");
        m1.setWeight(new BigDecimal("40"));
        TaskMetric m2 = new TaskMetric();
        m2.setMetricName("代码质量");
        m2.setWeight(new BigDecimal("35"));
        TaskMetric m3 = new TaskMetric();
        m3.setMetricName("文档规范");
        m3.setWeight(new BigDecimal("25"));
        return List.of(m1, m2, m3);
    }

    private ReviewEvaluationRequest cmd(String total, Map<String, BigDecimal> scores, String comment) {
        ReviewEvaluationRequest request = new ReviewEvaluationRequest();
        request.setTotalScore(total == null ? null : new BigDecimal(total));
        request.setMetricScores(scores);
        request.setReviewComment(comment);
        return request;
    }

    private Map<String, BigDecimal> validScores() {
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        scores.put("功能正确性", new BigDecimal("30"));
        scores.put("代码质量", new BigDecimal("30"));
        scores.put("文档规范", new BigDecimal("20"));
        return scores;
    }

    /** 归属链桩：result(AI_DONE)+submission(任务1)+task(教师2)+指标集+乐观锁成功 */
    private void stubReviewChain(String status, int versionNo, long teacherId) {
        when(evaluationResultMapper.selectById(7L)).thenReturn(result(status, versionNo));
        when(submissionMapper.selectById(5L)).thenReturn(submission(3L));
        when(taskMapper.selectById(1L)).thenReturn(task(teacherId));
        when(taskService.listMetrics(1L)).thenReturn(taskMetrics());
        when(evaluationResultMapper.updateById(any(EvaluationResult.class))).thenReturn(1);
    }

    private static final String COMMENT = "评分调整理由已核实作业内容"; // 12字

    /* ==================== E-02 复核（UC-22） ==================== */

    /** 复核正常链：AI_DONE（必有v1账本行）→REVIEWED，history新版本(source=TEACHER_REVIEW,v2,理由+操作人) */
    @Test
    void reviewHappyPathWritesNewVersion() {
        stubReviewChain("AI_DONE", 1, 2L);
        EvaluationHistory latest = new EvaluationHistory();
        latest.setVersionNo(1);
        when(evaluationHistoryMapper.selectOne(any())).thenReturn(latest);

        EvaluationVO vo = service.review(7L, cmd("80", validScores(), COMMENT), principal(2L, "TEACHER"));

        assertThat(vo.getStatus()).isEqualTo("REVIEWED");
        assertThat(vo.getCurrentVersionNo()).isEqualTo(2);
        assertThat(vo.getTotalScore()).isEqualByComparingTo("80");
        ArgumentCaptor<EvaluationHistory> captor = ArgumentCaptor.forClass(EvaluationHistory.class);
        verify(evaluationHistoryMapper).insert(captor.capture());
        EvaluationHistory history = captor.getValue();
        assertThat(history.getVersionNo()).isEqualTo(2);
        assertThat(history.getSource()).isEqualTo("TEACHER_REVIEW");
        assertThat(history.getTeacherComment()).isEqualTo(COMMENT);
        assertThat(history.getReviewerId()).isEqualTo(2L);
        assertThat(history.getTotalScore()).isEqualByComparingTo("80");
    }

    /** 理由空→30007（契约E-02异常流E1） */
    @Test
    void reviewBlankCommentThrows30007() {
        stubReviewChain("AI_DONE", 1, 2L);

        assertThatThrownBy(() -> service.review(7L, cmd("80", validScores(), "  "), principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30007);
        verify(evaluationHistoryMapper, never()).insert(any());
    }

    /** 理由不足10字→30007（03-02 §3.6必填≥10字） */
    @Test
    void reviewShortCommentThrows30007() {
        stubReviewChain("AI_DONE", 1, 2L);

        assertThatThrownBy(() -> service.review(7L, cmd("80", validScores(), "太短"), principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30007);
    }

    /** 状态前置违规：REVIEWED不可再复核→30000（spec决策3） */
    @Test
    void reviewOnReviewedThrows30000() {
        stubReviewChain("REVIEWED", 2, 2L);

        assertThatThrownBy(() -> service.review(7L, cmd("80", validScores(), COMMENT), principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
    }

    /** 总分越界(>100)→30000 */
    @Test
    void reviewTotalOutOfRangeThrows30000() {
        stubReviewChain("AI_DONE", 1, 2L);

        assertThatThrownBy(() -> service.review(7L, cmd("101", validScores(), COMMENT), principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
    }

    /** 指标缺项→30000（键集必须与任务指标集一致） */
    @Test
    void reviewMissingMetricThrows30000() {
        stubReviewChain("AI_DONE", 1, 2L);
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        scores.put("功能正确性", new BigDecimal("40"));

        assertThatThrownBy(() -> service.review(7L, cmd("80", scores, COMMENT), principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
    }

    /** 指标超权重上限→30000（对齐SchemaValidator纪律） */
    @Test
    void reviewMetricOverCapThrows30000() {
        stubReviewChain("AI_DONE", 1, 2L);
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        scores.put("功能正确性", new BigDecimal("41"));
        scores.put("代码质量", new BigDecimal("30"));
        scores.put("文档规范", new BigDecimal("20"));

        assertThatThrownBy(() -> service.review(7L, cmd("91", scores, COMMENT), principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
    }

    /** Σ指标分与总分不符→30000（±0.5容差外） */
    @Test
    void reviewSumMismatchThrows30000() {
        stubReviewChain("AI_DONE", 1, 2L);
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        scores.put("功能正确性", new BigDecimal("40"));
        scores.put("代码质量", new BigDecimal("30"));
        scores.put("文档规范", new BigDecimal("20"));

        assertThatThrownBy(() -> service.review(7L, cmd("60", scores, COMMENT), principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
    }

    /** IDOR：学生调复核→40400同形（防探测） */
    @Test
    void studentReviewThrows40400() {
        when(evaluationResultMapper.selectById(7L)).thenReturn(result("AI_DONE", 1));
        when(submissionMapper.selectById(5L)).thenReturn(submission(9L));

        assertThatThrownBy(() -> service.review(7L, cmd("80", validScores(), COMMENT), principal(3L, "STUDENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40400);
    }

    /** 非归属教师（任务属其他教师）→40400同形 */
    @Test
    void otherTeacherReviewThrows40400() {
        stubReviewChain("AI_DONE", 1, 99L);

        assertThatThrownBy(() -> service.review(7L, cmd("80", validScores(), COMMENT), principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40400);
    }

    /** 乐观锁冲突：updateById返回0行→30000（UC-22 E2并发复核，MVP不自动重试） */
    @Test
    void reviewOptimisticLockConflictThrows30000() {
        stubReviewChain("AI_DONE", 1, 2L);
        when(evaluationResultMapper.updateById(any(EvaluationResult.class))).thenReturn(0);

        assertThatThrownBy(() -> service.review(7L, cmd("80", validScores(), COMMENT), principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
        verify(evaluationHistoryMapper, never()).insert(any());
    }

    /* ==================== E-06 手动评分旁路（UC-24） ==================== */

    /** manual对FAILED正常：写v1（FAILED无history行），source=TEACHER_REVIEW，状态REVIEWED */
    @Test
    void manualScoreOnFailedWritesV1() {
        stubReviewChain("FAILED", 1, 2L);
        when(evaluationHistoryMapper.selectOne(any())).thenReturn(null);
        Map<String, BigDecimal> scores55 = new LinkedHashMap<>();
        scores55.put("功能正确性", new BigDecimal("25"));
        scores55.put("代码质量", new BigDecimal("20"));
        scores55.put("文档规范", new BigDecimal("10"));

        EvaluationVO vo = service.manualScore(7L, cmd("55", scores55, COMMENT), principal(2L, "TEACHER"));

        assertThat(vo.getStatus()).isEqualTo("REVIEWED");
        assertThat(vo.getCurrentVersionNo()).isEqualTo(1);
        ArgumentCaptor<EvaluationHistory> captor = ArgumentCaptor.forClass(EvaluationHistory.class);
        verify(evaluationHistoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getVersionNo()).isEqualTo(1);
        assertThat(captor.getValue().getSource()).isEqualTo("TEACHER_REVIEW");
    }

    /** manual前置=FAILED：对AI_DONE调manual→30000（契约E-06"前置=status=FAILED"） */
    @Test
    void manualScoreOnAiDoneThrows30000() {
        stubReviewChain("AI_DONE", 1, 2L);

        assertThatThrownBy(() -> service.manualScore(7L, cmd("55", validScores(), COMMENT), principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
    }

    /* ==================== E-03 重评（UC-23） ==================== */

    /** 重评REVIEWED：bump版本1→2 + 置PENDING + MQ投递 + 审计行（UC-23重评次数审计） */
    @Test
    void reEvaluateOnReviewedBumpsVersionAndPublishes() {
        stubReviewChain("REVIEWED", 2, 2L);

        service.reEvaluate(7L, principal(2L, "TEACHER"));

        ArgumentCaptor<com.aies.entity.EvaluationResult> captor =
                ArgumentCaptor.forClass(com.aies.entity.EvaluationResult.class);
        verify(evaluationResultMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getCurrentVersionNo()).isEqualTo(3);
        verify(rabbitTemplate).convertAndSend(eq("aies.eval.exchange"), eq("eval.submit"), any(EvalMessage.class));
        verify(auditLogMapper).insert(any(com.aies.entity.AuditLog.class));
    }

    /** 重评FAILED：版本原样保留（无history行，重评成功写v1）+ PENDING + MQ */
    @Test
    void reEvaluateOnFailedKeepsVersionAndPublishes() {
        stubReviewChain("FAILED", 1, 2L);

        service.reEvaluate(7L, principal(2L, "TEACHER"));

        ArgumentCaptor<com.aies.entity.EvaluationResult> captor =
                ArgumentCaptor.forClass(com.aies.entity.EvaluationResult.class);
        verify(evaluationResultMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getCurrentVersionNo()).isEqualTo(1);
        verify(rabbitTemplate).convertAndSend(eq("aies.eval.exchange"), eq("eval.submit"), any(EvalMessage.class));
    }

    /** 重评状态前置违规：AI_DONE→30000（仅REVIEWED/FAILED可重评） */
    @Test
    void reEvaluateOnAiDoneThrows30000() {
        stubReviewChain("AI_DONE", 1, 2L);

        assertThatThrownBy(() -> service.reEvaluate(7L, principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    /** 重评前置：解析未SUCCESS→30000（管道无文本可用） */
    @Test
    void reEvaluateOnUnparsedSubmissionThrows30000() {
        when(evaluationResultMapper.selectById(7L)).thenReturn(result("FAILED", 1));
        Submission unparsed = submission(3L);
        unparsed.setParseStatus("QUEUED");
        when(submissionMapper.selectById(5L)).thenReturn(unparsed);
        when(taskMapper.selectById(1L)).thenReturn(task(2L));

        assertThatThrownBy(() -> service.reEvaluate(7L, principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
    }

    /** 重评IDOR：学生→40400同形 */
    @Test
    void reEvaluateByStudentThrows40400() {
        when(evaluationResultMapper.selectById(7L)).thenReturn(result("REVIEWED", 2));
        when(submissionMapper.selectById(5L)).thenReturn(submission(9L));

        assertThatThrownBy(() -> service.reEvaluate(7L, principal(3L, "STUDENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40400);
    }

    /** 审计行内容：module=evaluation/action=RE_EVALUATE/target=evaluation:{id}（spec决策8） */
    @Test
    void reEvaluateAuditRowCarriesTargetAndOperator() {
        stubReviewChain("REVIEWED", 2, 2L);

        service.reEvaluate(7L, principal(2L, "TEACHER"));

        ArgumentCaptor<com.aies.entity.AuditLog> captor = ArgumentCaptor.forClass(com.aies.entity.AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        com.aies.entity.AuditLog audit = captor.getValue();
        assertThat(audit.getModule()).isEqualTo("evaluation");
        assertThat(audit.getAction()).isEqualTo("RE_EVALUATE");
        assertThat(audit.getTarget()).isEqualTo("evaluation:7");
        assertThat(audit.getOperatorId()).isEqualTo(2L);
        assertThat(audit.getDetail()).contains("\"submissionId\":5");
    }

    /** 不存在的评价→40400（review入口同形） */
    @Test
    void reviewMissingResultThrows40400() {
        when(evaluationResultMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.review(99L, cmd("80", validScores(), COMMENT), principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40400);
    }

    /** 校验链按任务指标集执行（listMetrics按taskId调用） */
    @Test
    void reviewValidatesAgainstTaskMetricSet() {
        stubReviewChain("AI_DONE", 1, 2L);
        when(evaluationHistoryMapper.selectOne(any())).thenReturn(null);

        service.review(7L, cmd("80", validScores(), COMMENT), principal(2L, "TEACHER"));

        verify(taskService).listMetrics(eq(1L));
    }
}
