package com.aies.realtime;

import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.entity.Task;
import com.aies.mapper.TaskMapper;
import com.aies.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 评价状态变化实时通知：把 AI 初评、教师复核、重评的结果推送给学生和任务教师。
 *
 * <p>在事务内调用时注册 AFTER_COMMIT 回调，保证前端刷新时读到的是已提交状态；
 * 在事务外（EvaluationWorker persist 已提交后）直接发送。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeNotifier {

    private final RealtimeWebSocketHandler webSocketHandler;
    private final TaskMapper taskMapper;
    private final NotificationService notificationService;

    public void notifyEvaluation(Submission submission, EvaluationResult result, String source) {
        if (submission == null || result == null) {
            return;
        }
        Task task = submission.getTaskId() == null ? null : taskMapper.selectById(submission.getTaskId());
        EvaluationUpdatePayload payload = new EvaluationUpdatePayload(
                result.getId(), submission.getId(), submission.getStudentId(),
                task == null ? null : task.getTeacherId(),
                submission.getFileName(), task == null ? null : task.getTitle(),
                result.getStatus(), result.getTotalScore(), source, result.getUpdateTime());
        Runnable dispatch = () -> dispatch(payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    private void dispatch(EvaluationUpdatePayload payload) {
        Set<Long> recipients = new LinkedHashSet<>();
        if (payload.studentId() != null) {
            recipients.add(payload.studentId());
        }
        if (payload.teacherId() != null) {
            recipients.add(payload.teacherId());
        }
        recipients.forEach(userId -> webSocketHandler.sendToUser(userId, "evaluation.updated", payload));
        recordNotifications(payload);
        log.debug("评价状态实时通知: evaluationId={} status={} recipients={}",
                payload.evaluationId(), payload.status(), recipients);
    }

    /** 终态才写入通知中心（PENDING 属中间态，避免重评过程刷屏） */
    private void recordNotifications(EvaluationUpdatePayload payload) {
        String label = statusLabel(payload.status());
        if (label == null) {
            return;
        }
        String subject = payload.taskTitle() == null || payload.taskTitle().isBlank()
                ? payload.fileName() : payload.taskTitle();
        String title = payload.fileName() == null ? "提交文件" : payload.fileName();
        String score = payload.totalScore() == null ? "" : " · 总分 " + payload.totalScore();
        String content = (subject == null ? "提交" : subject) + " · " + label + score;
        String link = payload.evaluationId() == null ? null : "/evaluations/" + payload.evaluationId();
        if (payload.studentId() != null) {
            notificationService.notify(payload.studentId(), "EVALUATION", "评价结果已更新", content, link);
        }
        if (payload.teacherId() != null) {
            notificationService.notify(payload.teacherId(), "EVALUATION", "学生评价状态更新",
                    title + " · " + label + score, link);
        }
    }

    private static String statusLabel(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "AI_DONE" -> "已出分";
            case "REVIEWED" -> "教师已复核";
            case "FAILED" -> "评价失败";
            default -> null;
        };
    }

    public record EvaluationUpdatePayload(Long evaluationId, Long submissionId, Long studentId,
                                          Long teacherId, String fileName, String taskTitle,
                                          String status, BigDecimal totalScore, String source,
                                          LocalDateTime updateTime) {
    }
}
