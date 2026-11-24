package com.aies.service;

import com.aies.config.RabbitConfig;
import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.model.EvalMessage;
import com.aies.model.ParseMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 异步链超时补偿单测：解析 PENDING/QUEUED 重投、评价 PENDING 重投与 touch 互斥。
 */
@ExtendWith(MockitoExtension.class)
class AsyncRecoveryServiceTest {

    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private EvaluationResultMapper evaluationResultMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @InjectMocks
    private AsyncRecoveryService recoveryService;

    @Test
    void recoverStuckParsesShouldRepublishPendingAndQueued() {
        when(submissionMapper.selectStuckParses(any(), any(), eq(50)))
                .thenReturn(List.of(submission(1L, "PENDING"), submission(2L, "QUEUED")));

        int sent = recoveryService.recoverStuckParses(LocalDateTime.now(), 10, 30, 50);

        assertThat(sent).isEqualTo(2);
        verify(rabbitTemplate, times(2)).convertAndSend(eq(RabbitConfig.PARSE_EXCHANGE),
                eq(RabbitConfig.PARSE_ROUTING_KEY), any(ParseMessage.class), any(CorrelationData.class));
    }

    @Test
    void parsePublishFailureShouldNotBreakBatch() {
        when(submissionMapper.selectStuckParses(any(), any(), eq(50)))
                .thenReturn(List.of(submission(1L, "PENDING"), submission(2L, "QUEUED")));
        doThrow(new AmqpException("broker down")).doNothing()
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(),
                        any(ParseMessage.class), any(CorrelationData.class));

        int sent = recoveryService.recoverStuckParses(LocalDateTime.now(), 10, 30, 50);

        assertThat(sent).isEqualTo(1);
        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(),
                any(ParseMessage.class), any(CorrelationData.class));
    }

    @Test
    void recoverStuckEvaluationsShouldTouchThenPublish() {
        when(evaluationResultMapper.selectStuckPending(any(), eq(50)))
                .thenReturn(List.of(result(10L, 100L), result(11L, 101L)));
        when(evaluationResultMapper.touchPending(eq(10L), any())).thenReturn(1);
        when(evaluationResultMapper.touchPending(eq(11L), any())).thenReturn(0);

        int sent = recoveryService.recoverStuckEvaluations(LocalDateTime.now(), 15, 50);

        assertThat(sent).isEqualTo(1);
        verify(evaluationResultMapper).touchPending(eq(10L), any());
        verify(evaluationResultMapper).touchPending(eq(11L), any());
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EVAL_EXCHANGE),
                eq(RabbitConfig.EVAL_ROUTING_KEY), any(EvalMessage.class), any(CorrelationData.class));
    }

    @Test
    void emptyCandidatesShouldNotPublish() {
        when(submissionMapper.selectStuckParses(any(), any(), eq(50))).thenReturn(List.of());
        when(evaluationResultMapper.selectStuckPending(any(), eq(50))).thenReturn(List.of());

        assertThat(recoveryService.recoverStuckParses(LocalDateTime.now(), 10, 30, 50)).isZero();
        assertThat(recoveryService.recoverStuckEvaluations(LocalDateTime.now(), 15, 50)).isZero();
        verifyNoInteractions(rabbitTemplate);
    }

    private Submission submission(long id, String status) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setParseStatus(status);
        return submission;
    }

    private EvaluationResult result(long id, long submissionId) {
        EvaluationResult result = new EvaluationResult();
        result.setId(id);
        result.setSubmissionId(submissionId);
        result.setStatus("PENDING");
        return result;
    }
}
