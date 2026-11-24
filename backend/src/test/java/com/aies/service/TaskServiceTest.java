package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.TaskCreateRequest;
import com.aies.dto.TaskVO;
import com.aies.entity.Task;
import com.aies.entity.TaskMetric;
import com.aies.entity.User;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.TaskMetricMapper;
import com.aies.mapper.UserMapper;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaskService 单测（UC-10 五例：Σ≠100→40001 / 过期deadline→30006 / 指标重名→30000 /
 * 班级越权→40301 / 正例落库DRAFT；UC-13 IDOR学生裁剪；二波扩例：T-03发布状态机 /
 * T-04详情统计装配与裁剪 / T-05列表学生·教师双视图 / S-05 requireVisible 40400同形）。
 */
class TaskServiceTest {

    private TaskMapper taskMapper;
    private TaskMetricMapper taskMetricMapper;
    private UserMapper userMapper;
    private SubmissionMapper submissionMapper;
    private TaskService taskService;

    private final LoginPrincipal teacher = new LoginPrincipal(2L, "jti", "raw", "TEACHER", Long.MAX_VALUE);
    private final LoginPrincipal student = new LoginPrincipal(3L, "jti", "raw", "STUDENT", Long.MAX_VALUE);
    private final LoginPrincipal admin = new LoginPrincipal(1L, "jti", "raw", "ADMIN", Long.MAX_VALUE);
    private final LoginPrincipal otherTeacher = new LoginPrincipal(9L, "jti", "raw", "TEACHER", Long.MAX_VALUE);

    @BeforeEach
    void setUp() {
        taskMapper = mock(TaskMapper.class);
        taskMetricMapper = mock(TaskMetricMapper.class);
        userMapper = mock(UserMapper.class);
        submissionMapper = mock(SubmissionMapper.class);
        taskService = new TaskService(taskMapper, taskMetricMapper, userMapper, submissionMapper);
    }

    private TaskCreateRequest request(BigDecimal w1, BigDecimal w2, BigDecimal w3) {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setTitle("单测任务");
        request.setClassId(1L);
        request.setDeadline(LocalDateTime.now().plusDays(7));
        TaskCreateRequest.MetricItem m1 = new TaskCreateRequest.MetricItem();
        m1.setMetricName("metric-a");
        m1.setWeight(w1);
        m1.setSortOrder(1);
        TaskCreateRequest.MetricItem m2 = new TaskCreateRequest.MetricItem();
        m2.setMetricName(w2 == null ? "metric-a" : "metric-b");
        m2.setWeight(w2 == null ? w1 : w2);
        m2.setSortOrder(2);
        TaskCreateRequest.MetricItem m3 = new TaskCreateRequest.MetricItem();
        m3.setMetricName("metric-c");
        m3.setWeight(w3);
        m3.setSortOrder(3);
        request.setMetrics(new java.util.ArrayList<>(List.of(m1, m2, m3)));
        return request;
    }

    /** 例1 Σ=99 → 40001（UC-10 E1） */
    @Test
    void createWeightSumNot100Rejected() {
        when(taskMapper.selectClassTeacher(1L)).thenReturn(2L);

        assertThatThrownBy(() -> taskService.create(
                request(new BigDecimal("39"), new BigDecimal("35"), new BigDecimal("25")), teacher))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.METRIC_WEIGHT_SUM_INVALID.getCode()));
    }

    /** 例2 截止时间早于当前 → 30006（UC-10 E2） */
    @Test
    void createPastDeadlineRejected() {
        TaskCreateRequest request = request(new BigDecimal("40"), new BigDecimal("35"), new BigDecimal("25"));
        request.setDeadline(LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> taskService.create(request, teacher))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DEADLINE_INVALID.getCode()));
    }

    /** 例3 指标名重复（trim后不分大小写；Σ=100确保先过权重关） → 30000 */
    @Test
    void createDuplicateMetricNameRejected() {
        when(taskMapper.selectClassTeacher(1L)).thenReturn(2L);

        assertThatThrownBy(() -> taskService.create(
                request(new BigDecimal("25"), null, new BigDecimal("50")), teacher))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    /** 例4 目标班级非本人绑定 → 40301（S-05归属校验） */
    @Test
    void createClassNotOwnedRejected() {
        when(taskMapper.selectClassTeacher(1L)).thenReturn(9L);

        assertThatThrownBy(() -> taskService.create(
                request(new BigDecimal("40"), new BigDecimal("35"), new BigDecimal("25")), teacher))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode()));
    }

    /** 例5 正例：Σ=100且班级归属匹配 → DRAFT落库+3条指标同事务写入 */
    @Test
    void createSuccessInsertsTaskAndMetrics() {
        when(taskMapper.selectClassTeacher(1L)).thenReturn(2L);
        when(taskMetricMapper.selectList(any())).thenReturn(List.of());

        TaskVO vo = taskService.create(
                request(new BigDecimal("40"), new BigDecimal("35"), new BigDecimal("25")), teacher);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(captor.getValue().getTeacherId()).isEqualTo(2L);
        verify(taskMetricMapper, times(3)).insert(any());
        assertThat(vo.getStatus()).isEqualTo("DRAFT");
    }

    /** 例6 IDOR：学生看他人班级任务与未发布任务 → 统一40400（防探测，不泄露存在性） */
    @Test
    void studentCannotSeeOtherClassOrUnpublished() {
        Task otherClass = new Task();
        otherClass.setId(99L);
        otherClass.setClassId(2L);
        otherClass.setStatus("PUBLISHED");
        Task unpublished = new Task();
        unpublished.setId(1L);
        unpublished.setClassId(1L);
        unpublished.setStatus("DRAFT");

        User studentUser = new User();
        studentUser.setId(3L);
        studentUser.setClassId(1L);
        when(userMapper.selectById(3L)).thenReturn(studentUser);
        when(taskMapper.selectById(99L)).thenReturn(otherClass);
        when(taskMapper.selectById(1L)).thenReturn(unpublished);

        assertThatThrownBy(() -> taskService.getById(99L, student))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
        assertThatThrownBy(() -> taskService.getById(1L, student))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    /** 例7 学生任务列表：本班过滤 + 状态标签筛选，VO裁剪submissionCount为null */
    @Test
    void studentListFiltersOwnClassByStatusAndTrimsStats() {
        Page<Task> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(task(1L, 2L, 1L, "PUBLISHED")));
        doReturn(page).when(taskMapper).selectPage(any(), any());
        when(taskMetricMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectById(3L)).thenReturn(studentOf(1L));

        Page<TaskVO> voPage = taskService.list(1, 10, 5L, "DRAFT", student);

        verify(userMapper).selectById(3L);
        assertThat(voPage.getRecords()).hasSize(1);
        assertThat(voPage.getRecords().get(0).getSubmissionCount()).isNull();
    }

    /** 例8 教师任务列表：本人全量分支（不查用户表、可带筛选），VO装配submissionCount统计 */
    @Test
    void teacherListAssemblesSubmissionStatsWithoutUserLookup() {
        Page<Task> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(task(1L, 2L, 1L, "PUBLISHED")));
        doReturn(page).when(taskMapper).selectPage(any(), any());
        when(taskMetricMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.countSubmissions(1L)).thenReturn(5L);

        Page<TaskVO> voPage = taskService.list(1, 10, null, null, teacher);

        verify(userMapper, never()).selectById(any());
        assertThat(voPage.getRecords()).hasSize(1);
        assertThat(voPage.getRecords().get(0).getSubmissionCount()).isEqualTo(5L);
    }

    /** 例9 教师详情：装配submissionCount与指标集（UC-13 T-04教师视角） */
    @Test
    void teacherDetailAssemblesSubmissionCountAndMetrics() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, 2L, 1L, "PUBLISHED"));
        when(taskMetricMapper.selectList(any())).thenReturn(List.of(metric(1L, "代码质量", "60")));
        when(taskMapper.countSubmissions(1L)).thenReturn(5L);

        TaskVO vo = taskService.getById(1L, teacher);

        assertThat(vo.getSubmissionCount()).isEqualTo(5L);
        assertThat(vo.getMetrics()).hasSize(1);
        assertThat(vo.getMetrics().get(0).getMetricName()).isEqualTo("代码质量");
    }

    /** 例10 学生详情（本班已发布）：submissionCount裁剪为null且不触发统计查询 */
    @Test
    void studentDetailTrimsSubmissionCount() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, 2L, 1L, "PUBLISHED"));
        when(taskMetricMapper.selectList(any())).thenReturn(List.of(metric(1L, "代码质量", "60")));
        when(userMapper.selectById(3L)).thenReturn(studentOf(1L));

        TaskVO vo = taskService.getById(1L, student);

        assertThat(vo.getSubmissionCount()).isNull();
        verify(taskMapper, never()).countSubmissions(any());
        assertThat(vo.getMetrics()).hasSize(1);
    }

    /** 例11 教师发布本人DRAFT任务：DRAFT→PUBLISHED状态机落库（UC-16前置） */
    @Test
    void publishDraftTaskTransitionsToPublished() {
        Task draft = task(1L, 2L, 1L, "DRAFT");
        when(taskMapper.selectById(1L)).thenReturn(draft);
        when(taskMetricMapper.selectList(any())).thenReturn(List.of());

        TaskVO vo = taskService.publish(1L, teacher);

        verify(taskMapper).updateById(draft);
        assertThat(draft.getStatus()).isEqualTo("PUBLISHED");
        assertThat(vo.getStatus()).isEqualTo("PUBLISHED");
    }

    /** 例12 二次发布（已PUBLISHED）→ 30000状态冲突拒绝 */
    @Test
    void publishNonDraftRejectedAsParamError() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, 2L, 1L, "PUBLISHED"));

        assertThatThrownBy(() -> taskService.publish(1L, teacher))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
                    assertThat(e.getMessage()).contains("仅草稿");
                });
    }

    /** 例13 学生发布任务 → 40400同形（S-05：学生无发布入口，防御兜底不泄露存在性） */
    @Test
    void publishByStudentRejectedAsNotFound() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, 2L, 1L, "DRAFT"));

        assertThatThrownBy(() -> taskService.publish(1L, student))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    /** 例14 非归属教师发布他人任务 → 40400同形（S-05归属校验首行执行） */
    @Test
    void publishByNonOwnerTeacherRejectedAsNotFound() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, 2L, 1L, "DRAFT"));

        assertThatThrownBy(() -> taskService.publish(1L, otherTeacher))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    /** 例15 发布不存在的任务 → 40400 */
    @Test
    void publishMissingTaskRejectedAsNotFound() {
        when(taskMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> taskService.publish(1L, teacher))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    /** 例16 管理员发布：全局可发布（跳过归属校验的default分支），状态机正常推进 */
    @Test
    void adminPublishSkipsOwnershipCheck() {
        Task draft = task(1L, 2L, 1L, "DRAFT");
        when(taskMapper.selectById(1L)).thenReturn(draft);
        when(taskMetricMapper.selectList(any())).thenReturn(List.of());

        taskService.publish(1L, admin);

        assertThat(draft.getStatus()).isEqualTo("PUBLISHED");
    }

    /** 例17 requireVisible正例：本班已发布任务对学生返回实体（submission域40400同形单源复用） */
    @Test
    void requireVisibleReturnsPublishedTaskForSameClassStudent() {
        Task published = task(1L, 2L, 1L, "PUBLISHED");
        when(taskMapper.selectById(1L)).thenReturn(published);
        when(userMapper.selectById(3L)).thenReturn(studentOf(1L));

        assertThat(taskService.requireVisible(1L, student)).isSameAs(published);
    }

    /** 例18 requireVisible：非归属教师查他人任务 → 40400同形 */
    @Test
    void requireVisibleRejectsOtherTeacherTaskAsNotFound() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, 2L, 1L, "DRAFT"));

        assertThatThrownBy(() -> taskService.requireVisible(1L, otherTeacher))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    /** T-02 编辑草稿任务：字段+指标集替换 */
    @Test
    void updateDraftShouldReplaceMetrics() {
        when(taskMapper.selectById(5L)).thenReturn(task(5L, 2L, 1L, "DRAFT"));
        when(taskMapper.selectClassTeacher(1L)).thenReturn(2L);
        when(submissionMapper.selectCount(any())).thenReturn(0L);
        when(taskMetricMapper.selectList(any())).thenReturn(List.of(metric(5L, "metric-a", "40")));

        var vo = taskService.update(5L,
                request(new BigDecimal("40"), new BigDecimal("35"), new BigDecimal("25")), teacher);

        assertThat(vo).isNotNull();
        verify(taskMapper).updateById(any(Task.class));
        verify(taskMetricMapper).delete(any());
        verify(taskMetricMapper, times(3)).insert(any(TaskMetric.class));
    }

    /** T-02 已发布任务无提交：允许修改结构与指标 */
    @Test
    void updatePublishedWithoutSubmissionsShouldAllow() {
        when(taskMapper.selectById(5L)).thenReturn(task(5L, 2L, 1L, "PUBLISHED"));
        when(taskMapper.selectClassTeacher(1L)).thenReturn(2L);
        when(submissionMapper.selectCount(any())).thenReturn(0L);
        when(taskMetricMapper.selectList(any())).thenReturn(List.of(metric(5L, "metric-a", "40")));

        var vo = taskService.update(5L,
                request(new BigDecimal("40"), new BigDecimal("35"), new BigDecimal("25")), teacher);

        assertThat(vo).isNotNull();
        verify(taskMapper).updateById(any(Task.class));
    }

    /** T-02 已发布任务有提交：改指标拒绝，避免破坏既有评价口径 */
    @Test
    void updatePublishedWithSubmissionsRejectsMetricChange() {
        when(taskMapper.selectById(5L)).thenReturn(task(5L, 2L, 1L, "PUBLISHED"));
        when(taskMapper.selectClassTeacher(1L)).thenReturn(2L);
        when(submissionMapper.selectCount(any())).thenReturn(1L);
        when(taskMetricMapper.selectList(any())).thenReturn(List.of(metric(5L, "metric-a", "40")));

        assertThatThrownBy(() -> taskService.update(5L,
                request(new BigDecimal("40"), new BigDecimal("35"), new BigDecimal("25")), teacher))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getMessage()).contains("不能修改评分指标"));
        verify(taskMapper, never()).updateById(any(Task.class));
    }

    /** T-02 已关闭任务：拒绝编辑 */
    @Test
    void updateClosedShouldReject() {
        when(taskMapper.selectById(5L)).thenReturn(task(5L, 2L, 1L, "CLOSED"));

        assertThatThrownBy(() -> taskService.update(5L,
                request(new BigDecimal("40"), new BigDecimal("35"), new BigDecimal("25")), teacher))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getMessage()).contains("已关闭"));
        verify(taskMapper, never()).updateById(any(Task.class));
    }

    /** T-02 删除草稿任务（无提交）：指标集与任务同事务清理 */
    @Test
    void deleteDraftWithoutSubmissionsShouldDelete() {
        when(taskMapper.selectById(5L)).thenReturn(task(5L, 2L, 1L, "DRAFT"));
        when(submissionMapper.selectCount(any())).thenReturn(0L);

        taskService.delete(5L, teacher);

        verify(taskMetricMapper).delete(any());
        verify(taskMapper).deleteById(5L);
    }

    /** T-02 删除已发布且无提交任务：允许 */
    @Test
    void deletePublishedWithoutSubmissionsShouldDelete() {
        when(taskMapper.selectById(5L)).thenReturn(task(5L, 2L, 1L, "PUBLISHED"));
        when(submissionMapper.selectCount(any())).thenReturn(0L);

        taskService.delete(5L, teacher);

        verify(taskMapper).deleteById(5L);
    }

    /** T-02 删除有学生提交的任务：拒绝（保留业务证据链） */
    @Test
    void deleteWithSubmissionsShouldReject() {
        when(taskMapper.selectById(5L)).thenReturn(task(5L, 2L, 1L, "PUBLISHED"));
        when(submissionMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> taskService.delete(5L, teacher))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有学生提交");
        verify(taskMapper, never()).deleteById(5L);
    }

    /** 定时任务：关闭过期已发布任务，透传 mapper 更新条数 */
    @Test
    void closeExpiredTasksShouldReturnUpdatedCount() {
        LocalDateTime now = LocalDateTime.now();
        when(taskMapper.closeExpiredPublished(now)).thenReturn(3);

        assertThat(taskService.closeExpiredTasks(now)).isEqualTo(3);
    }

    private Task task(long id, long teacherId, long classId, String status) {
        Task task = new Task();
        task.setId(id);
        task.setTitle("单测任务");
        task.setTeacherId(teacherId);
        task.setClassId(classId);
        task.setStatus(status);
        task.setDeadline(LocalDateTime.now().plusDays(3));
        task.setCreateTime(LocalDateTime.now());
        return task;
    }

    /** 构造任务指标实体（taskId/name/weight/sortOrder齐备，供TaskMetricVO装配） */
    private TaskMetric metric(long taskId, String name, String weight) {
        TaskMetric metric = new TaskMetric();
        metric.setTaskId(taskId);
        metric.setMetricName(name);
        metric.setWeight(new BigDecimal(weight));
        metric.setSortOrder(1);
        return metric;
    }

    /** 构造学生用户实体（classId决定本班可见性口径） */
    private User studentOf(long classId) {
        User user = new User();
        user.setId(3L);
        user.setClassId(classId);
        return user;
    }
}
