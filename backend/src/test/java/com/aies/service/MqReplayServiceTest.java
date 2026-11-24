package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.config.RabbitConfig;
import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.model.EvalMessage;
import com.aies.model.ParseMessage;
import com.aies.security.LoginPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MqReplayService 单测：解析/评价重放的状态闸门、PENDING 复位、MQ 投递与审计。
 */
class MqReplayServiceTest {

    private SubmissionMapper submissionMapper;
    private EvaluationResultMapper evaluationResultMapper;
    private RabbitTemplate rabbitTemplate;
    private AdminAuditService auditService;
    private MqReplayService service;
    private final LoginPrincipal principal = new LoginPrincipal(1L, "jti", "raw", "ADMIN", 0L);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        submissionMapper = mock(SubmissionMapper.class);
        evaluationResultMapper = mock(EvaluationResultMapper.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        auditService = mock(AdminAuditService.class);
        service = new MqReplayService(submissionMapper, evaluationResultMapper,
                rabbitTemplate, auditService);
        when(submissionMapper.updateById(any(Submission.class))).thenReturn(1);
        when(evaluationResultMapper.updateById(any(EvaluationResult.class))).thenReturn(1);
    }

    private Submission submission(String parseStatus) {
        Submission submission = new Submission();
        submission.setId(9L);
        submission.setParseStatus(parseStatus);
        submission.setParsedTextKey("SUCCESS".equals(parseStatus) ? "parsed/a.txt" : null);
        submission.setCanceled(0);
        return submission;
    }

    @Test
    void replayParseResetsFailedAndSendsMessage() {
        when(submissionMapper.selectById(9L)).thenReturn(submission("FAILED"));

        service.replayParse(9L, principal);

        ArgumentCaptor<Submission> row = ArgumentCaptor.forClass(Submission.class);
        verify(submissionMapper).updateById(row.capture());
        assertThat(row.getValue().getParseStatus()).isEqualTo("PENDING");
        assertThat(row.getValue().getParsedTextKey()).isNull();
        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.PARSE_EXCHANGE),
                eq(RabbitConfig.PARSE_ROUTING_KEY), message.capture(), any(CorrelationData.class));
        assertThat(message.getValue()).isInstanceOf(ParseMessage.class);
        verify(auditService).record(eq(principal), eq("mq"), eq("REPLAY_PARSE"),
                eq("submission:9"), anyMap());
    }

    @Test
    void replayParseRejectsCompletedSubmission() {
        when(submissionMapper.selectById(9L)).thenReturn(submission("SUCCESS"));

        assertThatThrownBy(() -> service.replayParse(9L, principal))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class),
                any(Object.class), any(CorrelationData.class));
    }

    @Test
    void replayEvaluationRejectsReviewedResult() {
        when(submissionMapper.selectById(9L)).thenReturn(submission("SUCCESS"));
        EvaluationResult result = new EvaluationResult();
        result.setStatus("REVIEWED");
        when(evaluationResultMapper.selectOne(any())).thenReturn(result);

        assertThatThrownBy(() -> service.replayEvaluation(9L, principal))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(30000);
    }

    @Test
    void replayEvaluationResetsFailedResultAndSendsMessage() {
        when(submissionMapper.selectById(9L)).thenReturn(submission("SUCCESS"));
        EvaluationResult result = new EvaluationResult();
        result.setStatus("FAILED");
        result.setErrorCode(40003);
        when(evaluationResultMapper.selectOne(any())).thenReturn(result);

        service.replayEvaluation(9L, principal);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getErrorCode()).isNull();
        assertThat(result.getUpdateTime()).isNotNull();
        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EVAL_EXCHANGE),
                eq(RabbitConfig.EVAL_ROUTING_KEY), message.capture(), any(CorrelationData.class));
        assertThat(message.getValue()).isInstanceOf(EvalMessage.class);
        verify(auditService).record(eq(principal), eq("mq"), eq("REPLAY_EVALUATION"),
                eq("submission:9"), anyMap());
    }

    @Test
    void replayEvaluationCreatesPendingRowWhenMissing() {
        when(submissionMapper.selectById(9L)).thenReturn(submission("SUCCESS"));
        when(evaluationResultMapper.selectOne(any())).thenReturn(null);

        service.replayEvaluation(9L, principal);

        ArgumentCaptor<EvaluationResult> row = ArgumentCaptor.forClass(EvaluationResult.class);
        verify(evaluationResultMapper).insert(row.capture());
        assertThat(row.getValue().getSubmissionId()).isEqualTo(9L);
        assertThat(row.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(row.getValue().getUpdateTime()).isNotNull();
    }
}
