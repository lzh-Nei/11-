package com.aies.service;

import com.aies.dto.EvaluationVO;
import com.aies.dto.TeacherWorkflowVO;
import com.aies.entity.EvaluationAppeal;
import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.entity.SysClass;
import com.aies.entity.Task;
import com.aies.entity.User;
import com.aies.mapper.EvaluationAppealMapper;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SysClassMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.UserMapper;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教师评价工作流：提交队列（班级/学生/状态筛选）、手动 AI 初评、待复核、归档历史。
 */
@Service
@RequiredArgsConstructor
public class TeacherWorkflowService {

    private static final String ROLE_TEACHER = "TEACHER";

    private final TaskMapper taskMapper;
    private final SubmissionMapper submissionMapper;
    private final EvaluationResultMapper evaluationResultMapper;
    private final EvaluationAppealMapper appealMapper;
    private final UserMapper userMapper;
    private final SysClassMapper classMapper;
    private final EvaluationCommandService evaluationCommandService;

    public Page<TeacherWorkflowVO> list(long page, long size, Long classId, String keyword,
                                        String stage, LoginPrincipal principal) {
        if (!ROLE_TEACHER.equals(principal.role())) {
            return new Page<>(page, size, 0);
        }
        List<Task> ownTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getTeacherId, principal.userId()));
        Map<Long, Task> taskMap = new HashMap<>();
        ownTasks.forEach(task -> taskMap.put(task.getId(), task));

        List<Submission> submissions = new ArrayList<>();
        if (!ownTasks.isEmpty()) {
            submissions.addAll(submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                    .in(Submission::getTaskId, ownTasks.stream().map(Task::getId).toList())));
        }

        // 申诉改派：即使不是自己任务，也进入当前教师的待复核/历史。
        List<EvaluationAppeal> assignedAppeals = appealMapper.selectList(
                new LambdaQueryWrapper<EvaluationAppeal>()
                        .eq(EvaluationAppeal::getAssignedTeacherId, principal.userId())
                        .orderByDesc(EvaluationAppeal::getId));
        Map<Long, EvaluationAppeal> appealsByEvaluation = new LinkedHashMap<>();
        for (EvaluationAppeal appeal : assignedAppeals) {
            appealsByEvaluation.putIfAbsent(appeal.getEvaluationId(), appeal);
        }
        for (EvaluationAppeal appeal : assignedAppeals) {
            if (submissions.stream().noneMatch(s -> Objects.equals(s.getId(), appeal.getSubmissionId()))) {
                Submission s = submissionMapper.selectById(appeal.getSubmissionId());
                if (s != null) {
                    submissions.add(s);
                }
            }
        }
        submissions.removeIf(s -> Integer.valueOf(1).equals(s.getCanceled()));
        if (submissions.isEmpty()) {
            return new Page<>(page, size, 0);
        }

        Set<Long> allTaskIds = submissions.stream().map(Submission::getTaskId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!allTaskIds.isEmpty()) {
            taskMapper.selectBatchIds(allTaskIds).forEach(task -> taskMap.putIfAbsent(task.getId(), task));
        }
        Set<Long> classIds = taskMap.values().stream().map(Task::getClassId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysClass> classes = classIds.isEmpty() ? Map.of()
                : classMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(SysClass::getId, c -> c, (a, b) -> a));

        List<Long> submissionIds = submissions.stream().map(Submission::getId).toList();
        Map<Long, EvaluationResult> evaluations = evaluationResultMapper.selectList(
                        new LambdaQueryWrapper<EvaluationResult>()
                                .in(EvaluationResult::getSubmissionId, submissionIds))
                .stream().collect(Collectors.toMap(EvaluationResult::getSubmissionId, e -> e, (a, b) -> a));

        Set<Long> evaluationIds = evaluations.values().stream().map(EvaluationResult::getId)
                .collect(Collectors.toSet());
        if (!evaluationIds.isEmpty()) {
            appealMapper.selectList(new LambdaQueryWrapper<EvaluationAppeal>()
                            .in(EvaluationAppeal::getEvaluationId, evaluationIds))
                    .forEach(appeal -> {
                        EvaluationAppeal existing = appealsByEvaluation.get(appeal.getEvaluationId());
                        if (existing == null
                                || ("RESOLVED".equals(existing.getStatus())
                                && "PENDING".equals(appeal.getStatus()))) {
                            appealsByEvaluation.put(appeal.getEvaluationId(), appeal);
                        }
                    });
        }

        Set<Long> userIds = new java.util.HashSet<>();
        submissions.forEach(s -> userIds.add(s.getStudentId()));
        evaluations.values().forEach(e -> {
            if (e.getReviewerId() != null) {
                userIds.add(e.getReviewerId());
            }
        });
        assignedAppeals.forEach(a -> {
            if (a.getAssignedTeacherId() != null) {
                userIds.add(a.getAssignedTeacherId());
            }
        });
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<TeacherWorkflowVO> rows = new ArrayList<>();
        for (Submission submission : submissions) {
            Task task = taskMap.get(submission.getTaskId());
            EvaluationResult evaluation = evaluations.get(submission.getId());
            EvaluationAppeal appeal = evaluation == null ? null
                    : appealsByEvaluation.get(evaluation.getId());
            TeacherWorkflowVO vo = buildRow(submission, task, evaluation, appeal, classes, users);
            if (classId != null && !Objects.equals(vo.getClassId(), classId)) {
                continue;
            }
            if (!matchesKeyword(vo, keyword)) {
                continue;
            }
            if (!matchesStage(vo, stage)) {
                continue;
            }
            rows.add(vo);
        }
        rows.sort(Comparator.comparing(TeacherWorkflowVO::getSubmitTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        long total = rows.size();
        long from = Math.max(0, (page - 1) * size);
        int start = (int) Math.min(from, total);
        int end = (int) Math.min(start + size, total);
        Page<TeacherWorkflowVO> result = new Page<>(page, size, total);
        result.setRecords(rows.subList(start, end));
        return result;
    }

    public void triggerAi(long submissionId, LoginPrincipal principal) {
        evaluationCommandService.triggerAiEvaluation(submissionId, principal);
    }

    public EvaluationVO approve(long evaluationId, LoginPrincipal principal) {
        return evaluationCommandService.approveAi(evaluationId, principal);
    }

    private TeacherWorkflowVO buildRow(Submission submission, Task task,
                                       EvaluationResult evaluation, EvaluationAppeal appeal,
                                       Map<Long, SysClass> classes, Map<Long, User> users) {
        TeacherWorkflowVO vo = new TeacherWorkflowVO();
        vo.setSubmissionId(submission.getId());
        vo.setTaskId(submission.getTaskId());
        vo.setTaskTitle(task == null ? null : task.getTitle());
        vo.setClassId(task == null ? null : task.getClassId());
        SysClass clazz = task == null ? null : classes.get(task.getClassId());
        vo.setClassName(clazz == null ? null : clazz.getClassName());
        vo.setStudentId(submission.getStudentId());
        User student = users.get(submission.getStudentId());
        vo.setStudentName(student == null ? null
                : (student.getRealName() == null || student.getRealName().isBlank()
                        ? student.getUsername() : student.getRealName()));
        vo.setStudentUsername(student == null ? null : student.getUsername());
        vo.setFileName(submission.getFileName());
        vo.setVersionNo(submission.getVersionNo());
        vo.setParseStatus(submission.getParseStatus());
        vo.setSubmitTime(submission.getCreateTime());
        if (evaluation != null) {
            vo.setEvaluationId(evaluation.getId());
            vo.setEvaluationStatus(evaluation.getStatus());
            vo.setTotalScore(evaluation.getTotalScore());
            vo.setCurrentVersionNo(evaluation.getCurrentVersionNo());
            vo.setAiModel(evaluation.getAiModel());
            vo.setEvaluationUpdateTime(evaluation.getUpdateTime());
            vo.setLastEvaluator(lastEvaluator(evaluation, users));
            vo.setCanTriggerAi(EvaluationService.STATUS_FAILED.equals(evaluation.getStatus()));
        } else {
            vo.setCanTriggerAi("SUCCESS".equals(submission.getParseStatus()));
        }
        if (appeal != null) {
            vo.setAppealId(appeal.getId());
            vo.setAppealStatus(appeal.getStatus());
            vo.setAssignedTeacherId(appeal.getAssignedTeacherId());
            vo.setAppealReason(appeal.getReason());
            User assigned = users.get(appeal.getAssignedTeacherId());
            vo.setAssignedTeacherName(assigned == null ? null
                    : (assigned.getRealName() == null || assigned.getRealName().isBlank()
                            ? assigned.getUsername() : assigned.getRealName()));
        }
        return vo;
    }

    private String lastEvaluator(EvaluationResult evaluation, Map<Long, User> users) {
        if (EvaluationService.STATUS_REVIEWED.equals(evaluation.getStatus())
                && evaluation.getReviewerId() != null) {
            User reviewer = users.get(evaluation.getReviewerId());
            String name = reviewer == null ? null
                    : (reviewer.getRealName() == null || reviewer.getRealName().isBlank()
                            ? reviewer.getUsername() : reviewer.getRealName());
            return "教师 · " + (name == null ? "未知教师" : name);
        }
        if (evaluation.getAiModel() != null && !evaluation.getAiModel().isBlank()) {
            return "AI · " + evaluation.getAiModel();
        }
        if (EvaluationService.STATUS_AI_DONE.equals(evaluation.getStatus())) {
            return "AI";
        }
        if (EvaluationService.STATUS_PENDING.equals(evaluation.getStatus())) {
            return "AI · 处理中";
        }
        if (EvaluationService.STATUS_FAILED.equals(evaluation.getStatus())) {
            return "AI · 失败";
        }
        return "—";
    }

    private boolean matchesKeyword(TeacherWorkflowVO vo, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(vo.getTaskTitle(), kw) || contains(vo.getStudentName(), kw)
                || contains(vo.getStudentUsername(), kw) || contains(vo.getFileName(), kw);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean matchesStage(TeacherWorkflowVO vo, String stage) {
        if (stage == null || stage.isBlank() || "all".equals(stage)) {
            return true;
        }
        String status = vo.getEvaluationStatus();
        boolean aiPending = status == null || EvaluationService.STATUS_PENDING.equals(status)
                || EvaluationService.STATUS_FAILED.equals(status);
        boolean appealPending = "PENDING".equals(vo.getAppealStatus());
        return switch (stage) {
            case "ai" -> aiPending;
            case "review" -> EvaluationService.STATUS_AI_DONE.equals(status) || appealPending;
            case "history" -> EvaluationService.STATUS_REVIEWED.equals(status) && !appealPending;
            default -> true;
        };
    }
}
