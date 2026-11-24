package com.aies.service;

import com.aies.common.ReportCacheEvictEvent;
import com.aies.entity.EvaluationResult;
import com.aies.entity.EvaluationHistory;
import com.aies.entity.Submission;
import com.aies.entity.Task;
import com.aies.mapper.EvaluationHistoryMapper;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.UserMapper;
import com.aies.model.ReportOverviewDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报表域单测（W11-C1 第二波：PM三盲点预警全覆盖+四图口径断言）。
 * 盲点1=监听器异常吞噬；盲点2=事务上下文前提（反射钉死）；盲点3=AFTER_COMMIT正向
 * （监听器直调）——回滚对照以实弹验证承载（Mockito单测无法跨真实事务）。
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ReportQueryServiceTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private EvaluationResultMapper evaluationResultMapper;
    @Mock
    private EvaluationHistoryMapper evaluationHistoryMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private SysConfigService sysConfigService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private ReportQueryService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ReportQueryService(taskMapper, submissionMapper,
                evaluationResultMapper, evaluationHistoryMapper, userMapper, sysConfigService,
                redis, objectMapper, null);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(userMapper.countStudentsByClassIds(any())).thenReturn(List.of());
        lenient().when(sysConfigService.getInt(anyString(), anyInt())).thenReturn(10);
    }

    private Task task(long id) {
        Task t = new Task();
        t.setId(id);
        t.setTeacherId(2L);
        t.setTitle("任务2");
        return t;
    }

    private Submission sub(long id, long taskId, long studentId) {
        Submission s = new Submission();
        s.setId(id);
        s.setTaskId(taskId);
        s.setStudentId(studentId);
        s.setParseStatus("SUCCESS");
        return s;
    }

    private EvaluationResult result(long subId, String status, int total, String metrics) {
        EvaluationResult r = new EvaluationResult();
        r.setSubmissionId(subId);
        r.setStatus(status);
        r.setTotalScore(total == 0 ? null : BigDecimal.valueOf(total));
        r.setMetricScores(metrics);
        return r;
    }

    @Test
    @DisplayName("四图口径：FAILED计入分母不计入均分，scoreDist只算已出分")
    void aggregateCountsFailedInDenominatorNotAverage() {
        Task t = task(10L);
        Submission s1 = sub(101L, 10L, 3L);
        Submission s2 = sub(102L, 10L, 4L);
        EvaluationResult done = result(101L, "AI_DONE", 80,
                "{\"功能正确性\":35,\"代码质量\":28,\"文档规范\":17}");
        EvaluationResult failed = result(102L, "FAILED", 0, null);
        when(taskMapper.selectList(any())).thenReturn(List.of(t));
        when(submissionMapper.selectList(any())).thenReturn(List.of(s1, s2));
        when(evaluationResultMapper.selectList(any())).thenReturn(List.of(done, failed));
        when(evaluationHistoryMapper.selectList(any())).thenReturn(List.of());
        when(valueOps.get(anyString())).thenReturn(null);

        ReportOverviewDTO dto = service.overviewForTeacher(2L);

        assertEquals(1, dto.tasks().size());
        assertEquals(2, dto.tasks().get(0).submitted());      // FAILED计入提交率分母
        assertEquals(1, dto.tasks().get(0).scored());          // 已出分=1
        assertEquals(0, BigDecimal.valueOf(80).compareTo(dto.tasks().get(0).avgScore())); // 均分仅AI_DONE
        assertEquals(1, dto.scoreDist().get(3).count());       // 80-89段=1
        assertEquals(2, dto.scoreGapDist().get(4).count());    // 无复核段=2（两提交均无教师复核）
    }

    @Test
    @DisplayName("分差分布：AI_INITIAL与教师版最新行差值计段")
    void gapDistUsesLatestVersions() {
        Task t = task(10L);
        Submission s1 = sub(101L, 10L, 3L);
        EvaluationResult done = result(101L, "REVIEWED", 88, null);
        EvaluationHistory ai = new EvaluationHistory();
        ai.setSubmissionId(101L);
        ai.setSource("AI_INITIAL");
        ai.setVersionNo(1);
        ai.setTotalScore(BigDecimal.valueOf(58));
        EvaluationHistory teacher = new EvaluationHistory();
        teacher.setSubmissionId(101L);
        teacher.setSource("TEACHER_REVIEW");
        teacher.setVersionNo(2);
        teacher.setTotalScore(BigDecimal.valueOf(88));
        when(taskMapper.selectList(any())).thenReturn(List.of(t));
        when(submissionMapper.selectList(any())).thenReturn(List.of(s1));
        when(evaluationResultMapper.selectList(any())).thenReturn(List.of(done));
        when(evaluationHistoryMapper.selectList(any())).thenReturn(List.of(ai, teacher));
        when(valueOps.get(anyString())).thenReturn(null);

        ReportOverviewDTO dto = service.overviewForTeacher(2L);

        assertEquals(1, dto.scoreGapDist().get(3).count());   // |88-58|=30 → >20段（index 3）
        assertEquals(0, dto.scoreGapDist().get(4).count());    // 无复核段=0
    }

    @Test
    @DisplayName("缓存命中：不触DB直接反序列化返回")
    void cacheHitSkipsDb() throws Exception {
        ReportOverviewDTO cached = ReportOverviewDTO.empty();
        when(valueOps.get(ReportQueryService.CACHE_PREFIX + "teacher:2"))
                .thenReturn(objectMapper.writeValueAsString(cached));

        ReportOverviewDTO dto = service.overviewForTeacher(2L);

        assertNotNull(dto);
        verify(taskMapper, never()).selectList(any());
        verify(submissionMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("缓存坏JSON：吞错重建（TTL兜底语义）")
    void corruptedCacheRebuilds() {
        when(valueOps.get(anyString())).thenReturn("not-json{");
        when(taskMapper.selectList(any())).thenReturn(List.of());
        ReportOverviewDTO dto = service.overviewForTeacher(2L);
        assertNotNull(dto);
        assertTrue(dto.tasks().isEmpty());
    }

    @Test
    @DisplayName("盲点1：监听器Redis异常吞噬不传播（TTL兜底）")
    void listenerSwallowsRedisFailure() {
        when(redis.delete(anyString())).thenThrow(new RuntimeException("redis down"));
        when(taskMapper.selectById(10L)).thenReturn(task(10L));
        ReportCacheEvictEvent event = new ReportCacheEvictEvent(10L, 3L);

        assertDoesNotThrow(() -> service.onCacheEvict(event));
    }

    @Test
    @DisplayName("监听器正常：me与teacher两scope key均删除")
    void listenerDeletesBothScopes() {
        Task t = task(10L);
        t.setTeacherId(2L);
        when(taskMapper.selectById(10L)).thenReturn(t);
        when(redis.delete(anyString())).thenReturn(true);

        service.onCacheEvict(new ReportCacheEvictEvent(10L, 3L));

        verify(redis).delete(ReportQueryService.CACHE_PREFIX + "me:3");
        verify(redis).delete(ReportQueryService.CACHE_PREFIX + "teacher:2");
    }

    @Test
    @DisplayName("refresh：删除对应scope缓存键")
    void refreshDeletesKey() {
        service.refresh("teacher", 2L);
        verify(redis).delete(ReportQueryService.CACHE_PREFIX + "teacher:2");
    }

    @Test
    @DisplayName("盲点2：三落库点必须@Transactional包裹（AFTER_COMMIT前提反射钉死）")
    void transactionalAnchorsPresent() throws Exception {
        Class<?> svc = Class.forName("com.aies.service.EvaluationCommandService");
        Method review = svc.getMethod("review", long.class,
                Class.forName("com.aies.dto.ReviewEvaluationRequest"),
                com.aies.security.LoginPrincipal.class);
        Method manualScore = svc.getMethod("manualScore", long.class,
                Class.forName("com.aies.dto.ReviewEvaluationRequest"),
                com.aies.security.LoginPrincipal.class);
        Method reEvaluate = svc.getMethod("reEvaluate", long.class,
                com.aies.security.LoginPrincipal.class);
        assertNotNull(review.getAnnotation(Transactional.class));
        assertNotNull(manualScore.getAnnotation(Transactional.class));
        assertNotNull(reEvaluate.getAnnotation(Transactional.class));
    }
}
