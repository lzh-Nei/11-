package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.dto.EvaluationHistoryPageVO;
import com.aies.dto.EvaluationHistoryVO;
import com.aies.dto.EvaluationVO;
import com.aies.entity.EvaluationHistory;
import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.entity.Task;
import com.aies.entity.User;
import com.aies.mapper.EvaluationHistoryMapper;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.UserMapper;
import com.aies.security.LoginPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EvaluationQueryService 单测（03-03 §七 E-01 + E-04/E-05 W8-M1扩展：
 * IDOR 40400同形 + 教师视角trace_id + metric_scores脏数据兜底 +
 * UC-25游标分页契约（倒序/limit钳制/cursor解码）+ 快照字段与学生裁剪）。
 */
class EvaluationQueryServiceTest {

    private EvaluationResultMapper evaluationResultMapper;
    private EvaluationHistoryMapper evaluationHistoryMapper;
    private SubmissionMapper submissionMapper;
    private TaskMapper taskMapper;
    private UserMapper userMapper;
    private EvaluationQueryService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        evaluationResultMapper = mock(EvaluationResultMapper.class);
        evaluationHistoryMapper = mock(EvaluationHistoryMapper.class);
        submissionMapper = mock(SubmissionMapper.class);
        taskMapper = mock(TaskMapper.class);
        userMapper = mock(UserMapper.class);
        service = new EvaluationQueryService(evaluationResultMapper, evaluationHistoryMapper,
                submissionMapper, taskMapper, userMapper, new ObjectMapper());
    }

    private LoginPrincipal principal(long userId, String role) {
        return new LoginPrincipal(userId, "jti", "raw", role, Long.MAX_VALUE);
    }

    private EvaluationResult result(String metricScoresJson) {
        EvaluationResult result = new EvaluationResult();
        result.setId(7L);
        result.setSubmissionId(5L);
        result.setStatus("AI_DONE");
        result.setCurrentVersionNo(1);
        result.setTotalScore(new BigDecimal("80"));
        result.setMetricScores(metricScoresJson);
        result.setAiComment("评语");
        result.setTraceId("trace0001");
        return result;
    }

    private Submission ownedBy(long studentId) {
        Submission submission = new Submission();
        submission.setId(5L);
        submission.setStudentId(studentId);
        submission.setTaskId(1L);
        return submission;
    }

    private void stubReadableChain(long studentId, long taskTeacherId) {
        when(evaluationResultMapper.selectById(7L)).thenReturn(result("{}"));
        when(submissionMapper.selectById(5L)).thenReturn(ownedBy(studentId));
        Task task = new Task();
        task.setId(1L);
        task.setTeacherId(taskTeacherId);
        when(taskMapper.selectById(1L)).thenReturn(task);
    }

    private EvaluationHistory historyRow(int versionNo, String source, String reviewerKey) {
        EvaluationHistory row = new EvaluationHistory();
        row.setId((long) versionNo);
        row.setEvaluationId(7L);
        row.setSubmissionId(5L);
        row.setVersionNo(versionNo);
        row.setSource(source);
        row.setTotalScore(new BigDecimal("80"));
        row.setMetricScores("{\"代码质量\":35}");
        row.setAiComment("AI评语" + versionNo);
        row.setTeacherComment("TEACHER_REVIEW".equals(source) ? "复核理由充分完整" : null);
        row.setTraceId("AI_INITIAL".equals(source) ? "trace0001" : null);
        if (reviewerKey != null) {
            row.setReviewerId(Long.valueOf(reviewerKey));
        }
        return row;
    }

    /* ==================== E-01（既有） ==================== */

    /** 学生查本人评价：全字段 + traceId不外泄（教师视角限定） */
    @Test
    void studentOwnEvaluationReturnsVoWithoutTraceId() {
        when(evaluationResultMapper.selectById(7L)).thenReturn(result("{\"代码质量\":35}"));
        when(submissionMapper.selectById(5L)).thenReturn(ownedBy(3L));

        EvaluationVO vo = service.getById(7L, principal(3L, "STUDENT"));

        assertThat(vo.getStatus()).isEqualTo("AI_DONE");
        assertThat(vo.getTotalScore()).isEqualByComparingTo("80");
        assertThat(vo.getMetricScores()).containsEntry("代码质量", new BigDecimal("35"));
        assertThat(vo.getTraceId()).isNull();
    }

    /** IDOR：学生查他人提交的评价 → 40400同形（防探测） */
    @Test
    void studentOtherEvaluationThrows40400() {
        when(evaluationResultMapper.selectById(7L)).thenReturn(result("{}"));
        when(submissionMapper.selectById(5L)).thenReturn(ownedBy(9L));

        assertThatThrownBy(() -> service.getById(7L, principal(3L, "STUDENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40400);
    }

    /** 防御：学生视角下提交不存在 → 同40400（不区分不存在/无权） */
    @Test
    void studentWithMissingSubmissionThrows40400() {
        when(evaluationResultMapper.selectById(7L)).thenReturn(result("{}"));
        when(submissionMapper.selectById(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(7L, principal(3L, "STUDENT")))
                .isInstanceOf(BusinessException.class);
    }

    /** result不存在 → 40400 */
    @Test
    void missingResultThrows40400() {
        when(evaluationResultMapper.selectById(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(7L, principal(3L, "STUDENT")))
                .isInstanceOf(BusinessException.class);
    }

    /** 教师视角：trace_id返回（E-01响应要点） */
    @Test
    void teacherViewIncludesTraceId() {
        when(evaluationResultMapper.selectById(7L)).thenReturn(result("{}"));
        when(submissionMapper.selectById(5L)).thenReturn(ownedBy(3L));

        EvaluationVO vo = service.getById(7L, principal(2L, "TEACHER"));

        assertThat(vo.getTraceId()).isEqualTo("trace0001");
    }

    /** metric_scores脏JSON → 空Map兜底（报告页不阻断） */
    @Test
    void dirtyMetricScoresJsonFallsBackToEmptyMap() {
        when(evaluationResultMapper.selectById(7L)).thenReturn(result("not-json"));
        when(submissionMapper.selectById(5L)).thenReturn(ownedBy(3L));

        EvaluationVO vo = service.getById(7L, principal(3L, "STUDENT"));

        assertThat(vo.getMetricScores()).isEmpty();
    }

    /** metric_scores为null → 空Map（PENDING/FAILED形态） */
    @Test
    void nullMetricScoresYieldsEmptyMap() {
        when(evaluationResultMapper.selectById(7L)).thenReturn(result(null));
        when(submissionMapper.selectById(5L)).thenReturn(ownedBy(3L));

        EvaluationVO vo = service.getById(7L, principal(3L, "STUDENT"));

        assertThat(vo.getMetricScores()).isEmpty();
        assertThat(vo.getTotalScore()).isEqualByComparingTo("80");
    }

    /* ==================== E-04 历史分页（UC-25） ==================== */

    /** 首页：version_no倒序返回，单页内hasMore=false且nextCursor=null */
    @Test
    void historyFirstPageReturnsDescWithoutCursor() {
        stubReadableChain(3L, 2L);
        when(evaluationHistoryMapper.selectList(any()))
                .thenReturn(List.of(historyRow(2, "TEACHER_REVIEW", "2"), historyRow(1, "AI_INITIAL", null)));

        EvaluationHistoryPageVO page = service.getHistoryPage(7L, null, null, principal(2L, "TEACHER"));

        assertThat(page.getList()).hasSize(2);
        assertThat(page.getList().get(0).getVersionNo()).isEqualTo(2);
        assertThat(page.getList().get(1).getVersionNo()).isEqualTo(1);
        assertThat(page.getList().get(1).getReviewerName()).isNull();
        assertThat(page.isHasMore()).isFalse();
        assertThat(page.getNextCursor()).isNull();
    }

    /** hasMore=true时nextCursor=末行version_no的Base64（URL-safe不透明游标） */
    @Test
    void historyPageWithMoreCarriesNextCursor() {
        stubReadableChain(3L, 2L);
        when(evaluationHistoryMapper.selectList(any())).thenReturn(List.of(
                historyRow(3, "RE_EVALUATE", "2"), historyRow(2, "TEACHER_REVIEW", "2"),
                historyRow(1, "AI_INITIAL", null)));

        EvaluationHistoryPageVO page = service.getHistoryPage(7L, null, 2, principal(2L, "TEACHER"));

        assertThat(page.getList()).hasSize(2);
        assertThat(page.isHasMore()).isTrue();
        String decoded = new String(Base64.getUrlDecoder().decode(page.getNextCursor()),
                StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("2");
    }

    /** 翻页：携cursor续查（服务端解码version_no作为起始游标） */
    @Test
    void historyPageDecodesCursorForNextSlice() {
        stubReadableChain(3L, 2L);
        String cursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2".getBytes(StandardCharsets.UTF_8));
        when(evaluationHistoryMapper.selectList(any()))
                .thenReturn(List.of(historyRow(1, "AI_INITIAL", null)));

        EvaluationHistoryPageVO page = service.getHistoryPage(7L, cursor, 2, principal(2L, "TEACHER"));

        assertThat(page.getList()).hasSize(1);
        assertThat(page.getList().get(0).getVersionNo()).isEqualTo(1);
        assertThat(page.isHasMore()).isFalse();
    }

    /** cursor格式非法→30000（UC-25 E2） */
    @Test
    void historyIllegalCursorThrows30000() {
        stubReadableChain(3L, 2L);

        assertThatThrownBy(() -> service.getHistoryPage(7L, "!!!not-base64!!!", 20, principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
    }

    /** limit越界（0与51）→30000（UC-25：1~50钳制） */
    @Test
    void historyLimitOutOfRangeThrows30000() {
        stubReadableChain(3L, 2L);

        assertThatThrownBy(() -> service.getHistoryPage(7L, null, 0, principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
        assertThatThrownBy(() -> service.getHistoryPage(7L, null, 51, principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
    }

    /** IDOR：学生查他人提交的历史版本→40400同形 */
    @Test
    void studentOtherHistoryThrows40400() {
        stubReadableChain(9L, 2L);

        assertThatThrownBy(() -> service.getHistoryPage(7L, null, 20, principal(3L, "STUDENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40400);
    }

    /** 非归属教师（任务属其他教师）→40400同形（spec决策2） */
    @Test
    void otherTeacherHistoryThrows40400() {
        stubReadableChain(3L, 99L);

        assertThatThrownBy(() -> service.getHistoryPage(7L, null, 20, principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40400);
    }

    /** reviewer_name装配：复核版本查sys_user.real_name */
    @Test
    void historyListItemCarriesReviewerName() {
        stubReadableChain(3L, 2L);
        User teacher = new User();
        teacher.setId(2L);
        teacher.setRealName("王老师");
        when(userMapper.selectById(2L)).thenReturn(teacher);
        when(evaluationHistoryMapper.selectList(any()))
                .thenReturn(List.of(historyRow(2, "TEACHER_REVIEW", "2")));

        EvaluationHistoryPageVO page = service.getHistoryPage(7L, null, 20, principal(2L, "TEACHER"));

        assertThat(page.getList().get(0).getReviewerName()).isEqualTo("王老师");
    }

    /* ==================== E-05 单版本快照（UC-25） ==================== */

    /** E-05快照全字段：指标分解析/评语/复核理由/reviewer_name */
    @Test
    void versionSnapshotReturnsFullFields() {
        stubReadableChain(3L, 2L);
        User teacher = new User();
        teacher.setId(2L);
        teacher.setRealName("王老师");
        when(userMapper.selectById(2L)).thenReturn(teacher);
        when(evaluationHistoryMapper.selectOne(any())).thenReturn(historyRow(2, "TEACHER_REVIEW", "2"));

        EvaluationHistoryVO vo = service.getVersion(7L, 2, principal(2L, "TEACHER"));

        assertThat(vo.getSource()).isEqualTo("TEACHER_REVIEW");
        assertThat(vo.getMetricScores()).containsEntry("代码质量", new BigDecimal("35"));
        assertThat(vo.getAiComment()).isEqualTo("AI评语2");
        assertThat(vo.getTeacherComment()).isEqualTo("复核理由充分完整");
        assertThat(vo.getReviewerName()).isEqualTo("王老师");
        assertThat(vo.getTraceId()).isNull();
    }

    /** 学生视角E-05：trace_id裁剪（UC-25备选流A2），AI版本指标分/评语可读 */
    @Test
    void studentVersionSnapshotTrimsTraceId() {
        stubReadableChain(3L, 2L);
        when(evaluationHistoryMapper.selectOne(any())).thenReturn(historyRow(1, "AI_INITIAL", null));

        EvaluationHistoryVO vo = service.getVersion(7L, 1, principal(3L, "STUDENT"));

        assertThat(vo.getSource()).isEqualTo("AI_INITIAL");
        assertThat(vo.getTraceId()).isNull();
        assertThat(vo.getMetricScores()).containsEntry("代码质量", new BigDecimal("35"));
    }

    /** 教师视角E-05：AI版本trace_id返回（可跳留痕回放抽屉） */
    @Test
    void teacherVersionSnapshotIncludesTraceId() {
        stubReadableChain(3L, 2L);
        when(evaluationHistoryMapper.selectOne(any())).thenReturn(historyRow(1, "AI_INITIAL", null));

        EvaluationHistoryVO vo = service.getVersion(7L, 1, principal(2L, "TEACHER"));

        assertThat(vo.getTraceId()).isEqualTo("trace0001");
    }

    /** 版本不存在→40400同形 */
    @Test
    void missingVersionThrows40400() {
        stubReadableChain(3L, 2L);
        when(evaluationHistoryMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getVersion(7L, 9, principal(2L, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40400);
    }
}
