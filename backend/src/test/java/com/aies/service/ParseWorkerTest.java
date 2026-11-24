package com.aies.service;

import com.aies.config.RabbitConfig;
import com.aies.entity.Submission;
import com.aies.mapper.SubmissionMapper;
import com.aies.model.EvalMessage;
import com.aies.model.ParseMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ParseWorker 单测（UC-16 第5步消费者状态机：幂等/推进/失败三支路 + MQ至少一次投递语义）。
 */
class ParseWorkerTest {

    private SubmissionMapper submissionMapper;
    private FileStorage fileStorage;
    private ParseService parseService;
    private RabbitTemplate rabbitTemplate;
    private MqProcessingGuard processingGuard;
    private ParseWorker worker;

    @BeforeEach
    void setUp() {
        submissionMapper = mock(SubmissionMapper.class);
        fileStorage = mock(FileStorage.class);
        parseService = mock(ParseService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        processingGuard = mock(MqProcessingGuard.class);
        when(processingGuard.tryLock(anyString(), any())).thenReturn("token");
        worker = new ParseWorker(submissionMapper, fileStorage, parseService, rabbitTemplate,
                processingGuard);
    }

    private Submission submission(long id, String fileType, String parseStatus) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setTaskId(1L);
        submission.setStudentId(3L);
        submission.setFileKey("f".repeat(32));
        submission.setFileName("课程报告." + fileType);
        submission.setFileType(fileType);
        submission.setParseStatus(parseStatus);
        return submission;
    }

    /** 例1 幂等：重复投递（已SUCCESS）直接跳过，不重解析不落库 */
    @Test
    void duplicateDeliverySkippedWhenAlreadySuccess() {
        when(submissionMapper.selectById(5L))
                .thenReturn(submission(5L, "docx", "SUCCESS"));

        worker.onMessage(new ParseMessage(5L));

        verify(submissionMapper, never()).updateById(any(Submission.class));
        verify(fileStorage, never()).openStream(anyString());
    }

    /** 例2 单飞锁：已有实例处理中时跳过重复投递（不读库、不重解析） */
    @Test
    void duplicateDeliverySkippedWhenAnotherWorkerHoldsLock() {
        when(processingGuard.tryLock(anyString(), any())).thenReturn(null);

        worker.onMessage(new ParseMessage(5L));

        verify(submissionMapper, never()).selectById(anyLong());
        verify(submissionMapper, never()).updateById(any(Submission.class));
    }

    /** 例3 脏消息：提交不存在（事务提交早于消息可见的窗口）跳过不落库 */
    @Test
    void missingSubmissionSkipped() {
        when(submissionMapper.selectById(9L)).thenReturn(null);

        worker.onMessage(new ParseMessage(9L));

        verify(submissionMapper, never()).updateById(any(Submission.class));
        verify(fileStorage, never()).openStream(anyString());
    }

    /** 例3 正向推进：PENDING→QUEUED→提取→parsed/{uuid32}.txt 落桶→SUCCESS */
    @Test
    void docxMessageAdvancesToSuccessWithParsedTextKey() throws IOException {
        when(submissionMapper.selectById(5L)).thenReturn(submission(5L, "docx", "PENDING"));
        when(fileStorage.openStream("f".repeat(32))).thenReturn(
                new ByteArrayInputStream("docx-bytes".getBytes(StandardCharsets.UTF_8)));
        when(parseService.extractDocx(any(InputStream.class))).thenReturn("hello world");
        // worker 复用同一实体两次updateById，须在调用时快照状态（captor只能看到终态）
        List<String> statusSequence = new ArrayList<>();
        List<String> textKeys = new ArrayList<>();
        when(submissionMapper.updateById(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            statusSequence.add(s.getParseStatus());
            textKeys.add(s.getParsedTextKey());
            return 1;
        });

        worker.onMessage(new ParseMessage(5L));

        assertThat(statusSequence).containsExactly("QUEUED", "SUCCESS");
        assertThat(textKeys.get(0)).isNull();
        assertThat(textKeys.get(1)).matches("^parsed/[a-f0-9]{32}\\.txt$");

        ArgumentCaptor<String> textKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).store(textKeyCaptor.capture(), any(InputStream.class), eq(11L));
        assertThat(textKeyCaptor.getValue()).isEqualTo(textKeys.get(1));

        // UC-20（W7-C2）：解析SUCCESS→评价消息投递（eval.submit），MQ内部链路无REST触发
        ArgumentCaptor<Object> evalCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EVAL_EXCHANGE),
                eq(RabbitConfig.EVAL_ROUTING_KEY), evalCaptor.capture(), any(CorrelationData.class));
        assertThat(evalCaptor.getValue()).isEqualTo(new EvalMessage(5L));
    }

    /** 例4 pdf 分支路由：fileType=pdf 走 extractPdf（docx 提取不触达） */
    @Test
    void pdfMessageRoutesToPdfExtractor() throws IOException {
        when(submissionMapper.selectById(6L)).thenReturn(submission(6L, "pdf", "PENDING"));
        when(fileStorage.openStream("f".repeat(32))).thenReturn(
                new ByteArrayInputStream("pdf-bytes".getBytes(StandardCharsets.UTF_8)));
        when(parseService.extractPdf(any(InputStream.class))).thenReturn("pdf text");

        worker.onMessage(new ParseMessage(6L));

        verify(parseService).extractPdf(any(InputStream.class));
        verify(parseService, never()).extractDocx(any(InputStream.class));
        ArgumentCaptor<Submission> updated = ArgumentCaptor.forClass(Submission.class);
        verify(submissionMapper, times(2)).updateById(updated.capture());
        assertThat(updated.getAllValues().get(1).getParseStatus()).isEqualTo("SUCCESS");
    }

    /** 例5 毒文件：提取异常置FAILED正常ack（不requeue死循环），textKey回null */
    @Test
    void poisonedFileMarksFailedWithoutRequeue() throws IOException {
        when(submissionMapper.selectById(7L)).thenReturn(submission(7L, "docx", "QUEUED"));
        when(fileStorage.openStream("f".repeat(32))).thenReturn(
                new ByteArrayInputStream("broken".getBytes(StandardCharsets.UTF_8)));
        when(parseService.extractDocx(any(InputStream.class)))
                .thenThrow(new IOException("damaged zip"));

        worker.onMessage(new ParseMessage(7L));

        ArgumentCaptor<Submission> updated = ArgumentCaptor.forClass(Submission.class);
        verify(submissionMapper, times(2)).updateById(updated.capture());
        Submission failed = updated.getAllValues().get(1);
        assertThat(failed.getParseStatus()).isEqualTo("FAILED");
        assertThat(failed.getParsedTextKey()).isNull();
        verify(fileStorage, never()).store(anyString(), any(InputStream.class), anyLong());
    }
}
