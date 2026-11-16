package com.aies.service;

import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.entity.Task;
import com.aies.entity.User;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.UserMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 管理端评价列表单测：evaluation+submission+task+user 聚合与空页。 */
class AdminEvaluationServiceTest {

    private EvaluationResultMapper evaluationResultMapper;
    private SubmissionMapper submissionMapper;
    private TaskMapper taskMapper;
    private UserMapper userMapper;
    private AdminEvaluationService service;

    @BeforeEach
    void setUp() {
        evaluationResultMapper = mock(EvaluationResultMapper.class);
        submissionMapper = mock(SubmissionMapper.class);
        taskMapper = mock(TaskMapper.class);
        userMapper = mock(UserMapper.class);
        service = new AdminEvaluationService(evaluationResultMapper, submissionMapper, taskMapper, userMapper);
    }

    @Test
    void listShouldAggregateTaskStudentTeacher() {
        EvaluationResult evaluation = new EvaluationResult();
        evaluation.setId(1L);
        evaluation.setSubmissionId(10L);
        evaluation.setStatus("REVIEWED");
        evaluation.setTotalScore(new BigDecimal("88"));
        evaluation.setCurrentVersionNo(2);
        evaluation.setUpdateTime(LocalDateTime.now());
        Page<EvaluationResult> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(evaluation));
        when(evaluationResultMapper.selectPage(any(), any())).thenReturn(page);

        Submission submission = new Submission();
        submission.setId(10L);
        submission.setTaskId(20L);
        submission.setStudentId(3L);
        when(submissionMapper.selectBatchIds(anyList())).thenReturn(List.of(submission));

        Task task = new Task();
        task.setId(20L);
        task.setTitle("实训任务");
        task.setTeacherId(2L);
        when(taskMapper.selectBatchIds(anyList())).thenReturn(List.of(task));

        User student = new User();
        student.setId(3L);
        student.setUsername("student");
        student.setRealName("示例学生");
        User teacher = new User();
        teacher.setId(2L);
        teacher.setUsername("teacher");
        teacher.setRealName("示例教师");
        when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(student, teacher));

        var result = service.list(1, 10, "REVIEWED");

        assertThat(result.getTotal()).isEqualTo(1);
        var vo = result.getRecords().get(0);
        assertThat(vo.taskTitle()).isEqualTo("实训任务");
        assertThat(vo.studentName()).isEqualTo("示例学生");
        assertThat(vo.teacherName()).isEqualTo("示例教师");
        assertThat(vo.status()).isEqualTo("REVIEWED");
    }

    @Test
    void listShouldReturnEmptyWhenNoEvaluations() {
        Page<EvaluationResult> page = new Page<>(1, 10, 0);
        page.setRecords(List.of());
        when(evaluationResultMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.list(1, 10, null);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }
}
