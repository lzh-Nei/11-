package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.TaskCreateRequest;
import com.aies.dto.TaskMetricVO;
import com.aies.dto.TaskOwnerVO;
import com.aies.dto.TaskVO;
import com.aies.entity.Submission;
import com.aies.entity.Task;
import com.aies.entity.TaskMetric;
import com.aies.entity.User;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.TaskMetricMapper;
import com.aies.mapper.UserMapper;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 任务域业务（03-03 §五 T-01/T-04/T-05 / UC-10/13 / 04-04 S-05 IDOR清单）。
 *
 * <p>视角裁剪：学生=本班任务（状态标签可筛）；教师=本人（teacher_id=本人）；管理员=全局。
 * 详情/提交仍经 requireVisible 限制，任务不存在与无权访问统一返回 40400（防探测）。</p>
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_TEACHER = "TEACHER";
    /** UC-10：指标权重和必须等于100（双端校验的服务端手段，03-02 §3.3勘误） */
    private static final BigDecimal WEIGHT_SUM_REQUIRED = new BigDecimal("100");

    private final TaskMapper taskMapper;
    private final TaskMetricMapper taskMetricMapper;
    private final UserMapper userMapper;
    private final SubmissionMapper submissionMapper;

    /**
     * T-01 创建任务（UC-10）：deadline→30006 / 班级归属→40301 / Σ≠100→40001，
     * 通过后 DRAFT 落库 + 指标集同事务写入。
     */
    @Transactional
    public TaskVO create(TaskCreateRequest request, LoginPrincipal principal) {
        // UC-10 E2：截止时间早于当前→30006（Bean Validation只能给30000，故在应用层判）
        if (request.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.DEADLINE_INVALID);
        }
        // S-05 归属校验：目标班级必须存在且为本教师绑定班级（UC-10前置：教师已绑定班级）
        Long classTeacher = taskMapper.selectClassTeacher(request.getClassId());
        if (classTeacher == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "目标班级不存在");
        }
        if (classTeacher != principal.userId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // UC-10 E1：Σ=100（BigDecimal比较，兼容33.33×3与100.0的标度差）
        BigDecimal sum = request.getMetrics().stream()
                .map(TaskCreateRequest.MetricItem::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(WEIGHT_SUM_REQUIRED) != 0) {
            throw new BusinessException(ErrorCode.METRIC_WEIGHT_SUM_INVALID);
        }
        // 指标名重复前置校验（trim后不分大小写，与 uk_task_metric 的ci排序规则一致）
        TreeSet<String> distinctNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        request.getMetrics().forEach(item -> distinctNames.add(item.getMetricName().trim()));
        if (distinctNames.size() != request.getMetrics().size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "指标名重复");
        }

        Task task = new Task();
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setTeacherId(principal.userId());
        task.setClassId(request.getClassId());
        task.setDeadline(request.getDeadline());
        task.setStatus(STATUS_DRAFT);
        taskMapper.insert(task);

        request.getMetrics().forEach(item -> {
            TaskMetric metric = new TaskMetric();
            metric.setTaskId(task.getId());
            metric.setMetricName(item.getMetricName().trim());
            metric.setWeight(item.getWeight());
            metric.setSortOrder(item.getSortOrder() == null ? 0 : item.getSortOrder());
            taskMetricMapper.insert(metric);
        });

        List<TaskMetric> metrics = taskMetricMapper.selectList(
                new LambdaQueryWrapper<TaskMetric>()
                        .eq(TaskMetric::getTaskId, task.getId())
                        .orderByAsc(TaskMetric::getSortOrder)
                        .orderByAsc(TaskMetric::getId));
        return toVO(task, metrics, true);
    }

    /** 编辑任务：草稿可全量修改；已发布任务在无提交时可改结构，有提交时仅可改标题/说明/截止时间 */
    @Transactional
    public TaskVO update(long taskId, TaskCreateRequest request, LoginPrincipal principal) {
        Task task = requireManageable(taskId, principal, "编辑");
        validateTaskFields(request, principal);

        long submissions = submissionMapper.selectCount(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, taskId));
        if (submissions > 0) {
            if (!Objects.equals(task.getClassId(), request.getClassId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                        "任务已有学生提交，不能更换班级");
            }
            List<TaskMetric> existing = taskMetricMapper.selectList(
                    new LambdaQueryWrapper<TaskMetric>()
                            .eq(TaskMetric::getTaskId, taskId)
                            .orderByAsc(TaskMetric::getSortOrder)
                            .orderByAsc(TaskMetric::getId));
            if (!sameMetrics(existing, request.getMetrics())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                        "任务已有学生提交，不能修改评分指标");
            }
        }

        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setClassId(request.getClassId());
        task.setDeadline(request.getDeadline());
        taskMapper.updateById(task);

        taskMetricMapper.delete(new LambdaQueryWrapper<TaskMetric>()
                .eq(TaskMetric::getTaskId, taskId));
        request.getMetrics().forEach(item -> {
            TaskMetric metric = new TaskMetric();
            metric.setTaskId(taskId);
            metric.setMetricName(item.getMetricName().trim());
            metric.setWeight(item.getWeight());
            metric.setSortOrder(item.getSortOrder() == null ? 0 : item.getSortOrder());
            taskMetricMapper.insert(metric);
        });

        List<TaskMetric> metrics = taskMetricMapper.selectList(
                new LambdaQueryWrapper<TaskMetric>()
                        .eq(TaskMetric::getTaskId, taskId)
                        .orderByAsc(TaskMetric::getSortOrder)
                        .orderByAsc(TaskMetric::getId));
        return toVO(task, metrics, true);
    }

    /** 删除任务：草稿/已发布均可，但已有学生提交的任务禁止删除 */
    @Transactional
    public void delete(long taskId, LoginPrincipal principal) {
        requireManageable(taskId, principal, "删除");
        long submissions = submissionMapper.selectCount(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, taskId));
        if (submissions > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "任务已有学生提交，不能删除");
        }
        taskMetricMapper.delete(new LambdaQueryWrapper<TaskMetric>()
                .eq(TaskMetric::getTaskId, taskId));
        taskMapper.deleteById(taskId);
    }

    /** 可管理任务（草稿/已发布 + 归属）：教师=本人；管理员=全局；学生不可见；已关闭只读。 */
    private Task requireManageable(long taskId, LoginPrincipal principal, String action) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        switch (principal.role()) {
            case ROLE_TEACHER -> {
                if (task.getTeacherId() != principal.userId()) {
                    throw new BusinessException(ErrorCode.NOT_FOUND);
                }
            }
            case ROLE_STUDENT -> throw new BusinessException(ErrorCode.NOT_FOUND);
            default -> {
                // ADMIN：全局可管理（报表同口径）
            }
        }
        if (STATUS_CLOSED.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "已关闭任务不能" + action + "，请保留用于评价追溯");
        }
        if (!STATUS_DRAFT.equals(task.getStatus()) && !STATUS_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "任务状态不允许" + action);
        }
        return task;
    }

    /** 已有提交时：指标集合与顺序必须和原任务一致（名称不区分大小写，权重按数值比较）。 */
    private boolean sameMetrics(List<TaskMetric> existing,
                                List<TaskCreateRequest.MetricItem> requested) {
        if (existing.size() != requested.size()) {
            return false;
        }
        for (int i = 0; i < existing.size(); i++) {
            String existingName = existing.get(i).getMetricName();
            String requestedName = requested.get(i).getMetricName();
            if (existingName == null || requestedName == null
                    || !existingName.equalsIgnoreCase(requestedName.trim())) {
                return false;
            }
            if (existing.get(i).getWeight() == null || requested.get(i).getWeight() == null
                    || existing.get(i).getWeight().compareTo(requested.get(i).getWeight()) != 0) {
                return false;
            }
        }
        return true;
    }

    /** 创建/编辑共用字段校验：截止时间、班级归属、权重和、指标名唯一 */
    private void validateTaskFields(TaskCreateRequest request, LoginPrincipal principal) {
        if (request.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.DEADLINE_INVALID);
        }
        Long classTeacher = taskMapper.selectClassTeacher(request.getClassId());
        if (classTeacher == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "目标班级不存在");
        }
        if (classTeacher != principal.userId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        BigDecimal sum = request.getMetrics().stream()
                .map(TaskCreateRequest.MetricItem::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(WEIGHT_SUM_REQUIRED) != 0) {
            throw new BusinessException(ErrorCode.METRIC_WEIGHT_SUM_INVALID);
        }
        TreeSet<String> distinctNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        request.getMetrics().forEach(item -> distinctNames.add(item.getMetricName().trim()));
        if (distinctNames.size() != request.getMetrics().size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "指标名重复");
        }
    }

    /**
     * T-03 任务发布（UC-16前置）：DRAFT→PUBLISHED 状态机。
     * S-05 归属校验首行执行（教师=本人；管理员=全局；学生无 task:publish 权限点，
     * 此处仍作防御兜底），不存在与无权访问统一 40400 同形（防探测）。
     * 发布后指标集锁定（当前无指标编辑端点，锁定语义由"发布后不可再改"承载）。
     *
     * <p>TODO(截止自动关闭)：deadline 到达后任务应转 CLOSED——留待定时任务
     * （@Scheduled 扫描 PUBLISHED 且 deadline 已过）实现，接口层不做假实现（W6-C2 §四裁决）。</p>
     */
    @Transactional
    public TaskVO publish(long taskId, LoginPrincipal principal) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        switch (principal.role()) {
            case ROLE_TEACHER -> {
                if (task.getTeacherId() != principal.userId()) {
                    throw new BusinessException(ErrorCode.NOT_FOUND);
                }
            }
            case ROLE_STUDENT -> throw new BusinessException(ErrorCode.NOT_FOUND);
            default -> {
                // ADMIN：全局可发布（报表同口径）
            }
        }
        // 状态机：仅 DRAFT 可发布；重复发布/已关闭→参数级状态冲突
        if (!STATUS_DRAFT.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "仅草稿状态任务可发布，当前状态: " + task.getStatus());
        }
        task.setStatus(STATUS_PUBLISHED);
        task.setPublishTime(LocalDateTime.now());
        taskMapper.updateById(task);

        List<TaskMetric> metrics = taskMetricMapper.selectList(
                new LambdaQueryWrapper<TaskMetric>()
                        .eq(TaskMetric::getTaskId, taskId)
                        .orderByAsc(TaskMetric::getSortOrder)
                        .orderByAsc(TaskMetric::getId));
        return toVO(task, metrics, true);
    }

    /**
     * T-04 任务详情（UC-13）：S-05 归属校验首行执行（见04-04 §三清单），按角色装配提交统计。
     */
    public TaskVO getById(long taskId, LoginPrincipal principal) {
        Task task = requireVisible(taskId, principal);
        boolean teacherView = !ROLE_STUDENT.equals(principal.role());
        List<TaskMetric> metrics = taskMetricMapper.selectList(
                new LambdaQueryWrapper<TaskMetric>()
                        .eq(TaskMetric::getTaskId, taskId)
                        .orderByAsc(TaskMetric::getSortOrder)
                        .orderByAsc(TaskMetric::getId));
        TaskVO vo = toVO(task, metrics, teacherView);
        fillOwnerNames(List.of(vo));
        return vo;
    }

    /** 批量补班级名/教师名；测试 mock 返回 null 时保持原值（前端有空值兜底）。 */
    private void fillOwnerNames(List<TaskVO> records) {
        if (records.isEmpty()) {
            return;
        }
        List<Long> taskIds = records.stream().map(TaskVO::getId).toList();
        List<TaskOwnerVO> owners = taskMapper.selectOwners(taskIds);
        if (owners == null) {
            return;
        }
        Map<Long, TaskOwnerVO> ownerMap = owners.stream()
                .collect(Collectors.toMap(TaskOwnerVO::getTaskId, owner -> owner,
                        (left, right) -> left));
        for (TaskVO vo : records) {
            TaskOwnerVO owner = ownerMap.get(vo.getId());
            if (owner != null) {
                vo.setClassName(owner.getClassName());
                vo.setTeacherName(owner.getTeacherName());
            }
        }
    }

    /**
     * 任务指标集查询（03-01 §2.3 SPI白名单承载：04-05 §四要求评价域经本方法取指标，
     * 禁直查task表Mapper——W7-C2 AI评分管道Prompt组装的数据源）。
     */
    public List<TaskMetric> listMetrics(long taskId) {
        List<TaskMetric> metrics = taskMetricMapper.selectList(
                new LambdaQueryWrapper<TaskMetric>()
                        .eq(TaskMetric::getTaskId, taskId)
                        .orderByAsc(TaskMetric::getSortOrder)
                        .orderByAsc(TaskMetric::getId));
        if (metrics.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "任务指标集为空，无法评分: taskId=" + taskId);
        }
        return metrics;
    }

    /**
     * T-05 任务列表：学生=本班+状态标签；教师=本人；管理员=全局。
     * 教师/管理员可用 stage（全部=非草稿 / 已发布=未过截止 / 已截止=关闭或已过截止）。
     */
    public Page<TaskVO> list(long page, long size, Long classId, String status,
                             LoginPrincipal principal) {
        return list(page, size, classId, status, null, principal);
    }

    public Page<TaskVO> list(long page, long size, Long classId, String status,
                             String stage, LoginPrincipal principal) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        if (ROLE_STUDENT.equals(principal.role())) {
            User user = userMapper.selectById(principal.userId());
            Long myClassId = (user == null) ? null : user.getClassId();
            wrapper.eq(Task::getClassId, myClassId == null ? -1L : myClassId);
            if (StringUtils.hasText(status)) {
                wrapper.eq(Task::getStatus, status);
            }
        } else {
            if (ROLE_TEACHER.equals(principal.role())) {
                wrapper.eq(Task::getTeacherId, principal.userId());
            }
            if (classId != null) {
                wrapper.eq(Task::getClassId, classId);
            }
            if (StringUtils.hasText(stage)) {
                applyStage(wrapper, stage);
            } else if (StringUtils.hasText(status)) {
                wrapper.eq(Task::getStatus, status);
            }
        }
        wrapper.orderByDesc(Task::getCreateTime).orderByDesc(Task::getId);
        Page<Task> result = taskMapper.selectPage(Page.of(page, size), wrapper);

        Map<Long, List<TaskMetric>> metricMap = groupMetricsByTask(result.getRecords());
        boolean teacherView = !ROLE_STUDENT.equals(principal.role());
        List<TaskVO> records = result.getRecords().stream()
                .map(task -> toVO(task, metricMap.getOrDefault(task.getId(), List.of()), teacherView))
                .toList();
        fillOwnerNames(records);
        Page<TaskVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    private void applyStage(LambdaQueryWrapper<Task> wrapper, String stage) {
        LocalDateTime now = LocalDateTime.now();
        switch (stage) {
            case "published" -> wrapper.eq(Task::getStatus, STATUS_PUBLISHED)
                    .gt(Task::getDeadline, now);
            case "ended" -> wrapper.and(w -> w.eq(Task::getStatus, STATUS_CLOSED)
                    .or(x -> x.eq(Task::getStatus, STATUS_PUBLISHED)
                            .lt(Task::getDeadline, now)));
            default -> wrapper.ne(Task::getStatus, STATUS_DRAFT);
        }
    }

    /* ===== S-05 IDOR 归属校验（04-04 §三：task详情——学生=本班+PUBLISHED；教师=本人） =====
     * public：submission 域（UC-16）复用同形规则，保证 40400 防探测口径单源。 */
    public Task requireVisible(long taskId, LoginPrincipal principal) {
        Task task = taskMapper.selectById(taskId);
        // 不存在与无权访问统一40400：学生探测他人任务/未发布任务不泄露存在性
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        switch (principal.role()) {
            case ROLE_STUDENT -> {
                User user = userMapper.selectById(principal.userId());
                boolean sameClass = user != null && user.getClassId() != null
                        && user.getClassId().equals(task.getClassId());
                if (!sameClass || !STATUS_PUBLISHED.equals(task.getStatus())) {
                    throw new BusinessException(ErrorCode.NOT_FOUND);
                }
            }
            case ROLE_TEACHER -> {
                if (task.getTeacherId() != principal.userId()) {
                    throw new BusinessException(ErrorCode.NOT_FOUND);
                }
            }
            default -> {
                // ADMIN：全局可见（报表同口径）
            }
        }
        return task;
    }

    private Map<Long, List<TaskMetric>> groupMetricsByTask(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return Map.of();
        }
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        return taskMetricMapper.selectList(new LambdaQueryWrapper<TaskMetric>()
                        .in(TaskMetric::getTaskId, taskIds)
                        .orderByAsc(TaskMetric::getSortOrder)
                        .orderByAsc(TaskMetric::getId))
                .stream()
                .collect(Collectors.groupingBy(TaskMetric::getTaskId));
    }

    private TaskVO toVO(Task task, List<TaskMetric> metrics, boolean withStats) {
        TaskVO vo = TaskVO.from(task,
                metrics.stream().map(TaskMetricVO::from).toList());
        if (withStats) {
            vo.setSubmissionCount(taskMapper.countSubmissions(task.getId()));
        }
        return vo;
    }
}
