package com.aies.service;

import com.aies.dto.admin.AdminDtos.AdminEvaluationVO;
import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.entity.Task;
import com.aies.entity.User;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端评价列表（管理员查看教师评价/复核历史入口，M3）。
 *
 * <p>只读聚合 evaluation_result + submission + task + sys_user；详情与版本链复用现有
 * E-01/E-04/E-05 端点（管理员具备 evaluation:view / evaluation:history:view）。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminEvaluationService {

    private final EvaluationResultMapper evaluationResultMapper;
    private final SubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;

    public Page<AdminEvaluationVO> list(long page, long size, String status) {
        LambdaQueryWrapper<EvaluationResult> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(EvaluationResult::getStatus, status.trim());
        }
        wrapper.orderByDesc(EvaluationResult::getUpdateTime).orderByDesc(EvaluationResult::getId);
        Page<EvaluationResult> result = evaluationResultMapper.selectPage(Page.of(page, size), wrapper);

        List<Long> submissionIds = result.getRecords().stream()
                .map(EvaluationResult::getSubmissionId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Submission> submissions = submissionIds.isEmpty() ? Map.of()
                : submissionMapper.selectBatchIds(submissionIds).stream()
                        .collect(Collectors.toMap(Submission::getId, Function.identity(),
                                (left, right) -> left));
        List<Long> taskIds = submissions.values().stream()
                .map(Submission::getTaskId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Task> tasks = taskIds.isEmpty() ? Map.of()
                : taskMapper.selectBatchIds(taskIds).stream()
                        .collect(Collectors.toMap(Task::getId, Function.identity(),
                                (left, right) -> left));
        List<Long> userIds = java.util.stream.Stream.concat(
                        submissions.values().stream().map(Submission::getStudentId),
                        tasks.values().stream().map(Task::getTeacherId))
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(),
                                (left, right) -> left));

        List<AdminEvaluationVO> records = result.getRecords().stream()
                .map(evaluation -> {
                    Submission submission = submissions.get(evaluation.getSubmissionId());
                    Task task = submission == null ? null : tasks.get(submission.getTaskId());
                    User student = submission == null ? null : users.get(submission.getStudentId());
                    User teacher = task == null ? null : users.get(task.getTeacherId());
                    return new AdminEvaluationVO(evaluation.getId(), evaluation.getSubmissionId(),
                            task == null ? null : task.getId(), task == null ? null : task.getTitle(),
                            displayName(student), displayName(teacher), evaluation.getStatus(),
                            evaluation.getTotalScore(), evaluation.getCurrentVersionNo(),
                            evaluation.getUpdateTime());
                })
                .toList();
        Page<AdminEvaluationVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    private String displayName(User user) {
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }
}
