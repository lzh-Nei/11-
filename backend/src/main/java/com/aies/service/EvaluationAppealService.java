package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.AppealVO;
import com.aies.entity.EvaluationAppeal;
import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.entity.Task;
import com.aies.entity.User;
import com.aies.mapper.EvaluationAppealMapper;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.RbacMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.UserMapper;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学生评价申诉：随机改派一名非原评教师复评。
 */
@Service
@RequiredArgsConstructor
public class EvaluationAppealService {

    private static final String ROLE_STUDENT = "STUDENT";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final int MIN_REJECT_REASON_LEN = 5;

    private final EvaluationResultMapper evaluationResultMapper;
    private final SubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final RbacMapper rbacMapper;
    private final EvaluationAppealMapper appealMapper;
    private final NotificationService notificationService;

    @Transactional
    public AppealResult appeal(long evaluationId, String reason, LoginPrincipal principal) {
        if (!ROLE_STUDENT.equals(principal.role())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        EvaluationResult result = evaluationResultMapper.selectById(evaluationId);
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Submission submission = submissionMapper.selectById(result.getSubmissionId());
        if (submission == null || !Objects.equals(submission.getStudentId(), principal.userId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!EvaluationService.STATUS_REVIEWED.equals(result.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "教师复核完成后才能申请复核");
        }
        Long pending = appealMapper.selectCount(new LambdaQueryWrapper<EvaluationAppeal>()
                .eq(EvaluationAppeal::getEvaluationId, evaluationId)
                .eq(EvaluationAppeal::getStatus, "PENDING"));
        if (pending != null && pending > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该评价已有复核申请正在处理中");
        }
        Task task = taskMapper.selectById(submission.getTaskId());
        List<Long> candidates = new ArrayList<>(rbacMapper.selectAvailableTeacherIds(null));
        candidates.removeIf(id -> (task != null && Objects.equals(id, task.getTeacherId()))
                || Objects.equals(id, result.getReviewerId())
                || Objects.equals(id, principal.userId()));
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "暂无其他可受理的教师，请联系管理员");
        }
        Collections.shuffle(candidates);
        Long assignedTeacherId = candidates.get(0);
        EvaluationAppeal appeal = new EvaluationAppeal();
        appeal.setEvaluationId(evaluationId);
        appeal.setSubmissionId(submission.getId());
        appeal.setStudentId(principal.userId());
        appeal.setReason(reason.trim());
        appeal.setStatus("PENDING");
        appeal.setAssignedTeacherId(assignedTeacherId);
        appeal.setCreateTime(LocalDateTime.now());
        appealMapper.insert(appeal);
        User teacher = userMapper.selectById(assignedTeacherId);
        String name = teacher == null ? null
                : (teacher.getRealName() == null || teacher.getRealName().isBlank()
                        ? teacher.getUsername() : teacher.getRealName());
        notificationService.notify(assignedTeacherId, "APPEAL", "新的评价复核申请",
                "学生申请复核评价 #" + evaluationId + "，请尽快处理", "/evaluations/" + evaluationId);
        return new AppealResult(appeal.getId(), assignedTeacherId, name, appeal.getStatus());
    }

    /** 学生本人申诉列表（倒序） */
    public Page<AppealVO> mine(long studentId, long page, long size) {
        return toPage(appealMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<EvaluationAppeal>()
                        .eq(EvaluationAppeal::getStudentId, studentId)
                        .orderByDesc(EvaluationAppeal::getId)));
    }

    /** 指派给当前教师的申诉列表（倒序） */
    public Page<AppealVO> assigned(long teacherId, long page, long size) {
        return toPage(appealMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<EvaluationAppeal>()
                        .eq(EvaluationAppeal::getAssignedTeacherId, teacherId)
                        .orderByDesc(EvaluationAppeal::getId)));
    }

    /** 申诉详情：学生本人 / 被指派教师 / 管理员可见，其余 40400 同形 */
    public AppealVO detail(long appealId, LoginPrincipal principal) {
        EvaluationAppeal appeal = requireAppeal(appealId);
        boolean student = Objects.equals(appeal.getStudentId(), principal.userId());
        boolean teacher = Objects.equals(appeal.getAssignedTeacherId(), principal.userId());
        if (!student && !teacher && !"ADMIN".equals(principal.role())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return toVOs(List.of(appeal)).get(0);
    }

    /** 学生撤回：仅本人且 PENDING；撤回后通知被指派教师 */
    @Transactional
    public void withdraw(long appealId, LoginPrincipal principal) {
        EvaluationAppeal appeal = requireAppeal(appealId);
        if (!Objects.equals(appeal.getStudentId(), principal.userId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!STATUS_PENDING.equals(appeal.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅待处理的申诉可撤回");
        }
        LocalDateTime now = LocalDateTime.now();
        appeal.setStatus(STATUS_CANCELED);
        appeal.setResolvedTime(now);
        appeal.setHandledTime(now);
        appealMapper.updateById(appeal);

        if (appeal.getAssignedTeacherId() != null) {
            notificationService.notify(appeal.getAssignedTeacherId(), "APPEAL", "复核申请已撤回",
                    "学生已撤回评价 #" + appeal.getEvaluationId() + " 的复核申请",
                    "/evaluations/" + appeal.getEvaluationId());
        }
    }

    /** 教师驳回：仅被指派教师且 PENDING；驳回理由 5-500 字，通知学生 */
    @Transactional
    public void reject(long appealId, String reason, LoginPrincipal principal) {
        EvaluationAppeal appeal = requireAppeal(appealId);
        if (!Objects.equals(appeal.getAssignedTeacherId(), principal.userId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!STATUS_PENDING.equals(appeal.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该申诉已处理，不能重复驳回");
        }
        String text = reason == null ? "" : reason.trim();
        if (text.length() < MIN_REJECT_REASON_LEN) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "驳回理由至少" + MIN_REJECT_REASON_LEN + "个字");
        }
        LocalDateTime now = LocalDateTime.now();
        appeal.setStatus(STATUS_REJECTED);
        appeal.setRejectReason(text);
        appeal.setHandledTime(now);
        appeal.setResolvedTime(now);
        appealMapper.updateById(appeal);

        notificationService.notify(appeal.getStudentId(), "APPEAL", "复核申请被驳回",
                "评价 #" + appeal.getEvaluationId() + " 的复核申请已驳回：" + text,
                "/evaluations/" + appeal.getEvaluationId());
    }

    private EvaluationAppeal requireAppeal(long appealId) {
        EvaluationAppeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return appeal;
    }

    private Page<AppealVO> toPage(Page<EvaluationAppeal> result) {
        Page<AppealVO> page = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        page.setRecords(toVOs(result.getRecords()));
        return page;
    }

    /** 批量回填任务标题与学生/教师姓名，避免逐行查询 */
    private List<AppealVO> toVOs(List<EvaluationAppeal> appeals) {
        if (appeals.isEmpty()) {
            return List.of();
        }
        List<Long> submissionIds = appeals.stream().map(EvaluationAppeal::getSubmissionId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Submission> submissions = submissionIds.isEmpty() ? Map.of()
                : submissionMapper.selectBatchIds(submissionIds).stream()
                        .collect(Collectors.toMap(Submission::getId, s -> s, (a, b) -> a));
        List<Long> taskIds = submissions.values().stream().map(Submission::getTaskId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Task> tasks = taskIds.isEmpty() ? Map.of()
                : taskMapper.selectBatchIds(taskIds).stream()
                        .collect(Collectors.toMap(Task::getId, t -> t, (a, b) -> a));
        Set<Long> userIds = new HashSet<>();
        appeals.forEach(appeal -> {
            if (appeal.getStudentId() != null) {
                userIds.add(appeal.getStudentId());
            }
            if (appeal.getAssignedTeacherId() != null) {
                userIds.add(appeal.getAssignedTeacherId());
            }
        });
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return appeals.stream().map(appeal -> {
            Submission submission = submissions.get(appeal.getSubmissionId());
            Task task = submission == null ? null : tasks.get(submission.getTaskId());
            return new AppealVO(appeal.getId(), appeal.getEvaluationId(), appeal.getSubmissionId(),
                    task == null ? null : task.getTitle(),
                    displayName(users.get(appeal.getStudentId())), appeal.getReason(),
                    appeal.getStatus(), appeal.getAssignedTeacherId(),
                    displayName(users.get(appeal.getAssignedTeacherId())),
                    appeal.getRejectReason(), appeal.getCreateTime(), appeal.getResolvedTime());
        }).toList();
    }

    private String displayName(User user) {
        if (user == null) {
            return null;
        }
        return user.getRealName() == null || user.getRealName().isBlank()
                ? user.getUsername() : user.getRealName();
    }

    public record AppealResult(Long appealId, Long assignedTeacherId,
                               String assignedTeacherName, String status) {
    }
}
