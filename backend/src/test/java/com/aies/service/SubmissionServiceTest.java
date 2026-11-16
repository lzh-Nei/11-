package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.config.RabbitConfig;
import com.aies.dto.SubmissionDetailVO;
import com.aies.dto.SubmissionMineVO;
import com.aies.dto.SubmissionVO;
import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.entity.Task;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.TaskMapper;
import com.aies.model.ParseMessage;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SubmissionService 单测（UC-16/S-01 校验序 + S-02/S-03/S-04 + IDOR 40400同形 + 解析消息投递）。
 *
 * <p>校验序（S-07）逐环验证：可见性→截止→大小→双白名单→病毒钩子→版本自增→落桶→落库→发消息。</p>
 */
class SubmissionServiceTest {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_MIME = "application/pdf";

    private SubmissionMapper submissionMapper;
    private TaskMapper taskMapper;
    private TaskService taskService;
    private FileStorage fileStorage;
    private VirusScanHook virusScanHook;
    private RabbitTemplate rabbitTemplate;
    private EvaluationResultMapper evaluationResultMapper;
    private SubmissionService submissionService;

    private final LoginPrincipal student =
            new LoginPrincipal(3L, "jti", "raw", "STUDENT", Long.MAX_VALUE);

    @BeforeEach
    void setUp() {
        submissionMapper = mock(SubmissionMapper.class);
        taskMapper = mock(TaskMapper.class);
        taskService = mock(TaskService.class);
        fileStorage = mock(FileStorage.class);
        virusScanHook = mock(VirusScanHook.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        evaluationResultMapper = mock(EvaluationResultMapper.class);
        submissionService = new SubmissionService(submissionMapper, taskMapper, taskService,
                fileStorage, virusScanHook, rabbitTemplate, evaluationResultMapper);
        when(virusScanHook.clean(any())).thenReturn(true);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Task publishedTask() {
        Task task = new Task();
        task.setId(1L);
        task.setClassId(1L);
        task.setTeacherId(2L);
        task.setStatus("PUBLISHED");
        task.setDeadline(LocalDateTime.now().plusDays(7));
        return task;
    }

    private MultipartFile docxFile() {
        return new MockMultipartFile("file", "课程报告.docx", DOCX_MIME,
                "fake-docx-bytes".getBytes(StandardCharsets.UTF_8));
    }

    private void stubInsertAssignsId(long id) {
        when(submissionMapper.insert(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            s.setId(id);
            return 1;
        });
    }

    private void stubVisibleTask() {
        when(taskService.requireVisible(1L, student)).thenReturn(publishedTask());
        when(submissionMapper.selectMaxVersion(1L, 3L)).thenReturn(2);
        stubInsertAssignsId(100L);
    }

    /* ==================== S-01 提交（UC-16） ==================== */

    /** 例1 角色兜底：非学生角色（学生端接口被教师调用）→ 40400 同形 */
    @Test
    void submitRejectsNonStudentRole() {
        LoginPrincipal teacher = new LoginPrincipal(2L, "jti", "raw", "TEACHER", Long.MAX_VALUE);

        assertThatThrownBy(() -> submissionService.submit(1L, docxFile(), teacher))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
        verify(taskService, never()).requireVisible(anyLong(), any());
    }

    /** 例2 S-05 可见性：requireVisible 抛 40400 原样穿透（IDOR 防线单源复用） */
    @Test
    void submitRejectsInvisibleTask() {
        when(taskService.requireVisible(1L, student))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> submissionService.submit(1L, docxFile(), student))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
        verify(submissionMapper, never()).insert(any(Submission.class));
    }

    /** 例3 已关闭任务 → 40002（UC-16 E3） */
    @Test
    void submitRejectsClosedTask() {
        Task closed = publishedTask();
        closed.setStatus("CLOSED");
        when(taskService.requireVisible(1L, student)).thenReturn(closed);

        assertThatThrownBy(() -> submissionService.submit(1L, docxFile(), student))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.TASK_NOT_OPEN.getCode()));
    }

    /** 例4 已过截止时间 → 40002（CLOSED 兜底之外的时间维度） */
    @Test
    void submitRejectsPastDeadline() {
        Task expired = publishedTask();
        expired.setDeadline(LocalDateTime.now().minusDays(1));
        when(taskService.requireVisible(1L, student)).thenReturn(expired);

        assertThatThrownBy(() -> submissionService.submit(1L, docxFile(), student))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.TASK_NOT_OPEN.getCode()));
    }

    /** 例5 空文件 → 30000（S-07：先校验后落盘） */
    @Test
    void submitRejectsEmptyFile() {
        stubVisibleTask();

        assertThatThrownBy(() -> submissionService.submit(1L,
                new MockMultipartFile("file", "a.docx", DOCX_MIME, new byte[0]), student))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
                    assertThat(e.getMessage()).isEqualTo("文件为空");
                });
    }

    /** 例6 >20MB → 30003（UC-16 E2，白名单之前拦） */
    @Test
    void submitRejectsOversizeFile() {
        stubVisibleTask();
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(20L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> submissionService.submit(1L, oversized, student))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE.getCode()));
        verify(fileStorage, never()).store(anyString(), any(InputStream.class), anyLong());
    }

    /** 例7 扩展名不在白名单 → 30001（docx/pdf 之外的类型直接拒） */
    @Test
    void submitRejectsUnsupportedExtension() {
        stubVisibleTask();

        assertThatThrownBy(() -> submissionService.submit(1L,
                new MockMultipartFile("file", "virus.zip", "application/zip", "x".getBytes()), student))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.FILE_TYPE_UNSUPPORTED.getCode()));
    }

    /** 例8 扩展名+MIME 不一致 → 30001（S-07 双重白名单：禁只信单一来源） */
    @Test
    void submitRejectsMimeMismatch() {
        stubVisibleTask();

        assertThatThrownBy(() -> submissionService.submit(1L,
                new MockMultipartFile("file", "a.docx", PDF_MIME, "x".getBytes()), student))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.FILE_TYPE_UNSUPPORTED.getCode()));
    }

    /** 例9 病毒扫描命中 → 30001 同径拒绝（R-02 钩子接线） */
    @Test
    void submitRejectsWhenVirusScanHits() {
        stubVisibleTask();
        when(virusScanHook.clean(any())).thenReturn(false);

        assertThatThrownBy(() -> submissionService.submit(1L, docxFile(), student))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.FILE_TYPE_UNSUPPORTED.getCode());
                    assertThat(e.getMessage()).isEqualTo("文件未通过安全扫描");
                });
        verify(fileStorage, never()).store(anyString(), any(InputStream.class), anyLong());
    }

    /** 例10 正例：版本自增+UUID32 key落桶+PENDING落库+消息携带新id投递 */
    @Test
    void submitSuccessIncrementsVersionStoresAndDispatches() {
        stubVisibleTask();

        SubmissionVO vo = submissionService.submit(1L, docxFile(), student);

        assertThat(vo.getId()).isEqualTo(100L);
        assertThat(vo.getVersionNo()).isEqualTo(3);
        assertThat(vo.getParseStatus()).isEqualTo("PENDING");
        assertThat(vo.getFileType()).isEqualTo("docx");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).store(keyCaptor.capture(), any(InputStream.class), eq(15L));
        assertThat(keyCaptor.getValue()).matches("^[a-f0-9]{32}$");

        ArgumentCaptor<Submission> inserted = ArgumentCaptor.forClass(Submission.class);
        verify(submissionMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getFileKey()).isEqualTo(keyCaptor.getValue());
        assertThat(inserted.getValue().getParseStatus()).isEqualTo("PENDING");

        ArgumentCaptor<ParseMessage> message = ArgumentCaptor.forClass(ParseMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.PARSE_EXCHANGE),
                eq(RabbitConfig.PARSE_ROUTING_KEY), message.capture());
        assertThat(message.getValue().submissionId()).isEqualTo(100L);
    }

    /** 例11 并发撞唯一键 → 30000"提交冲突"（DuplicateKey 全局映射的就地纠正） */
    @Test
    void submitMapsDuplicateKeyToSubmissionConflict() {
        stubVisibleTask();
        when(submissionMapper.insert(any(Submission.class)))
                .thenThrow(new DuplicateKeyException("uk_task_student_version"));

        assertThatThrownBy(() -> submissionService.submit(1L, docxFile(), student))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
                    assertThat(e.getMessage()).isEqualTo("提交冲突，请重试");
                });
    }

    /** 例12 MQ故障不反噬提交接口：发送异常吞掉，PENDING 留存可重放 */
    @Test
    void submitSurvivesMessageSendFailure() {
        stubVisibleTask();
        doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        SubmissionVO vo = submissionService.submit(1L, docxFile(), student);

        assertThat(vo.getId()).isEqualTo(100L);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    /** 例13 事务同步激活：消息仅在 afterCommit 后投递（worker 不得先于提交可见） */
    @Test
    void dispatchesParseMessageOnlyAfterCommit() {
        stubVisibleTask();
        TransactionSynchronizationManager.initSynchronization();
        try {
            submissionService.submit(1L, docxFile(), student);
            verify(rabbitTemplate, never())
                    .convertAndSend(anyString(), anyString(), any(Object.class));
        } finally {
            List<TransactionSynchronization> syncs = new ArrayList<>(
                    TransactionSynchronizationManager.getSynchronizations());
            TransactionSynchronizationManager.clearSynchronization();
            syncs.forEach(TransactionSynchronization::afterCommit);
        }
        ArgumentCaptor<ParseMessage> message = ArgumentCaptor.forClass(ParseMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.PARSE_EXCHANGE),
                eq(RabbitConfig.PARSE_ROUTING_KEY), message.capture());
        assertThat(message.getValue().submissionId()).isEqualTo(100L);
    }

    /* ==================== S-02 我的提交列表（UC-17） ==================== */

    private Submission savedSubmission(long id, long taskId, int version) {
        Submission s = new Submission();
        s.setId(id);
        s.setTaskId(taskId);
        s.setStudentId(3L);
        s.setFileKey("k".repeat(32));
        s.setFileName("报告v" + version + ".docx");
        s.setFileSize(15L);
        s.setFileType("docx");
        s.setVersionNo(version);
        s.setParseStatus("SUCCESS");
        return s;
    }

    /** 例14 评价状态联查：命中 AI_DONE / 未命中回填 PENDING（UC-17 字典初始态） */
    @Test
    void mineMapsEvaluationStatusAndDefaultsPending() {
        Page<Submission> page = new Page<>(1, 10);
        page.setRecords(List.of(savedSubmission(1L, 1L, 1), savedSubmission(2L, 1L, 2)));
        page.setTotal(2);
        when(submissionMapper.selectPage(any(), any())).thenReturn(page);
        EvaluationResult eval = new EvaluationResult();
        eval.setSubmissionId(1L);
        eval.setStatus("AI_DONE");
        when(evaluationResultMapper.selectList(any())).thenReturn(List.of(eval));

        Page<SubmissionMineVO> vos = submissionService.mine(3L, null, 1, 10);

        assertThat(vos.getTotal()).isEqualTo(2);
        assertThat(vos.getRecords()).hasSize(2);
        assertThat(vos.getRecords().get(0).getEvaluationStatus()).isEqualTo("AI_DONE");
        assertThat(vos.getRecords().get(1).getEvaluationStatus()).isEqualTo("PENDING");
    }

    /** 例15 分页钳制：page=0→1、size=500→100（防拖库），空页不触发评价联查 */
    @Test
    void mineClampsPageAndSizeBounds() {
        Page<Submission> empty = new Page<>(1, 100);
        empty.setRecords(List.of());
        empty.setTotal(0);
        AtomicReference<Page<Submission>> passed = new AtomicReference<>();
        when(submissionMapper.selectPage(any(), any())).thenAnswer(inv -> {
            passed.set(inv.getArgument(0));
            return empty;
        });

        Page<SubmissionMineVO> vos = submissionService.mine(3L, null, 0, 500);

        assertThat(passed.get().getCurrent()).isEqualTo(1);
        assertThat(passed.get().getSize()).isEqualTo(100);
        assertThat(vos.getRecords()).isEmpty();
        verify(evaluationResultMapper, never()).selectList(any());
    }

    /* ==================== S-03/S-04 详情与下载（UC-18） ==================== */

    private Submission ownedSubmission() {
        Submission s = savedSubmission(5L, 1L, 1);
        s.setParseStatus("SUCCESS");
        return s;
    }

    /** 例16 归属校验：不存在/他人提交统一 40400 同形（S-05/04-04 §三防探测） */
    @Test
    void detailAndDownloadRejectMissingOrForeignSubmission() {
        when(submissionMapper.selectById(9L)).thenReturn(null);
        Submission foreign = ownedSubmission();
        foreign.setStudentId(99L);
        when(submissionMapper.selectById(6L)).thenReturn(foreign);

        assertThatThrownBy(() -> submissionService.detail(9L, student))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
        assertThatThrownBy(() -> submissionService.detail(6L, student))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
        assertThatThrownBy(() -> submissionService.downloadUrl(6L, student))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    /** 例17 详情正例：签名URL(300s)+提取文本预览+评价状态回填 */
    @Test
    void detailBuildsSignedUrlAndPreview() {
        Submission submission = ownedSubmission();
        submission.setParsedTextKey("parsed/1.txt");
        when(submissionMapper.selectById(5L)).thenReturn(submission);
        when(evaluationResultMapper.selectList(any())).thenReturn(List.of());
        when(fileStorage.presignedGetUrl("k".repeat(32), 300))
                .thenReturn("https://minio.example/signed");
        when(fileStorage.openStream("parsed/1.txt")).thenReturn(new ByteArrayInputStream(
                "提取文本内容".getBytes(StandardCharsets.UTF_8)));

        SubmissionDetailVO vo = submissionService.detail(5L, student);

        assertThat(vo.getDownloadUrl()).isEqualTo("https://minio.example/signed");
        assertThat(vo.getEvaluationStatus()).isEqualTo("PENDING");
        assertThat(vo.getTextPreview()).isEqualTo("提取文本内容");
        assertThat(vo.getTextChars()).isEqualTo(6);
        verify(fileStorage).presignedGetUrl("k".repeat(32), 300);
    }

    /** 例18 预览截断：2500字符→预览2000+总数字段2500（全量在parsed对象） */
    @Test
    void detailTruncatesPreviewAt2000Chars() {
        Submission submission = ownedSubmission();
        submission.setParsedTextKey("parsed/2.txt");
        when(submissionMapper.selectById(5L)).thenReturn(submission);
        when(evaluationResultMapper.selectList(any())).thenReturn(List.of());
        when(fileStorage.presignedGetUrl(anyString(), eq(300)))
                .thenReturn("https://minio.example/signed");
        when(fileStorage.openStream("parsed/2.txt")).thenReturn(new ByteArrayInputStream(
                "x".repeat(2500).getBytes(StandardCharsets.UTF_8)));

        SubmissionDetailVO vo = submissionService.detail(5L, student);

        assertThat(vo.getTextChars()).isEqualTo(2500);
        assertThat(vo.getTextPreview()).hasSize(2000);
    }

    /** 例19 未解析（PENDING/FAILED 无textKey）：preview/textChars 为null，URL照常返回 */
    @Test
    void detailWithoutParsedTextKeepsPreviewNull() {
        Submission submission = ownedSubmission();
        submission.setParseStatus("PENDING");
        when(submissionMapper.selectById(5L)).thenReturn(submission);
        when(evaluationResultMapper.selectList(any())).thenReturn(List.of());
        when(fileStorage.presignedGetUrl(anyString(), eq(300)))
                .thenReturn("https://minio.example/signed");

        SubmissionDetailVO vo = submissionService.detail(5L, student);

        assertThat(vo.getTextPreview()).isNull();
        assertThat(vo.getTextChars()).isNull();
        assertThat(vo.getDownloadUrl()).isEqualTo("https://minio.example/signed");
        verify(fileStorage, never()).openStream(anyString());
    }

    /** 例20 提取文本对象读取失败 → 50000 系统异常 */
    @Test
    void detailPropagatesSystemErrorWhenPreviewUnreadable() throws IOException {
        Submission submission = ownedSubmission();
        submission.setParsedTextKey("parsed/3.txt");
        when(submissionMapper.selectById(5L)).thenReturn(submission);
        when(evaluationResultMapper.selectList(any())).thenReturn(List.of());
        when(fileStorage.presignedGetUrl(anyString(), eq(300)))
                .thenReturn("https://minio.example/signed");
        InputStream broken = mock(InputStream.class);
        when(broken.readAllBytes()).thenThrow(new IOException("boom"));
        when(fileStorage.openStream("parsed/3.txt")).thenReturn(broken);

        assertThatThrownBy(() -> submissionService.detail(5L, student))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
                    assertThat(e.getMessage()).isEqualTo("提取文本读取失败");
                });
    }

    /** 例21 S-04 下载：302目标=5分钟预签名URL（与S-03同源同有效期） */
    @Test
    void downloadUrlReturnsPresignedUrl() {
        when(submissionMapper.selectById(5L)).thenReturn(ownedSubmission());
        when(fileStorage.presignedGetUrl("k".repeat(32), 300))
                .thenReturn("https://minio.example/signed");

        assertThat(submissionService.downloadUrl(5L, student))
                .isEqualTo("https://minio.example/signed");
    }
}
