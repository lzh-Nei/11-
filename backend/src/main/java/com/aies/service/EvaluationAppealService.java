package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 学生评价申诉：随机改派一名非原评教师复评。
 */
@Service
@RequiredArgsConstructor
public class EvaluationAppealService {

    private static final String ROLE_STUDENT = "STUDENT";

    private final EvaluationResultMapper evaluationResultMapper;
    private final SubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final RbacMapper rbacMapper;
    private final EvaluationAppealMapper appealMapper;

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
        return new AppealResult(appeal.getId(), assignedTeacherId, name, appeal.getStatus());
    }

    public record AppealResult(Long appealId, Long assignedTeacherId,
                               String assignedTeacherName, String status) {
    }
}
