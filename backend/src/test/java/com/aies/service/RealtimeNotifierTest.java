package com.aies.service;

import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.entity.Task;
import com.aies.mapper.TaskMapper;
import com.aies.realtime.RealtimeNotifier;
import com.aies.realtime.RealtimeWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 评价实时通知：学生与任务教师都应收到状态事件。 */
class RealtimeNotifierTest {

    private RealtimeWebSocketHandler handler;
    private TaskMapper taskMapper;
    private RealtimeNotifier notifier;

    @BeforeEach
    void setUp() {
        handler = mock(RealtimeWebSocketHandler.class);
        taskMapper = mock(TaskMapper.class);
        notifier = new RealtimeNotifier(handler, taskMapper, mock(NotificationService.class));
    }

    @Test
    void evaluationUpdateSentToStudentAndTeacher() {
        Submission submission = new Submission();
        submission.setId(5L);
        submission.setTaskId(1L);
        submission.setStudentId(3L);
        submission.setFileName("report.docx");
        Task task = new Task();
        task.setId(1L);
        task.setTitle("测试任务");
        task.setTeacherId(2L);
        when(taskMapper.selectById(1L)).thenReturn(task);
        EvaluationResult result = new EvaluationResult();
        result.setId(7L);
        result.setStatus("AI_DONE");
        result.setTotalScore(new BigDecimal("88"));

        notifier.notifyEvaluation(submission, result, "AI_INITIAL");

        verify(handler).sendToUser(eq(3L), eq("evaluation.updated"), any());
        verify(handler).sendToUser(eq(2L), eq("evaluation.updated"), any());
    }
}
