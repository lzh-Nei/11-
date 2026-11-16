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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 提交域业务（03-03 §六 S-01 / UC-16 / 04-04 S-07 文件安全 + §三 IDOR清单）。
 *
 * <p>校验顺序（S-07：先校验后落盘）：S-05可见性(40400同形) → 截止(40002)
 * → 大小(30003) → 扩展名+MIME双重白名单(30001) → 病毒钩子 → 版本号落盘落库。</p>
 *
 * <p>桶化兑现（W7-C1，D-23闭环）：落盘走 {@link FileStorage} 抽象——
 * filestorage.mode=minio 时真实落 swe-hot（key=UUID32 与本地实现同形，
 * golden_dataset 零改动）；local 为无容器兜底。W6-C2 的过渡偏差就此收回。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private static final String ROLE_STUDENT = "STUDENT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_CLOSED = "CLOSED";

    /** S-07 扩展名→MIME 双重白名单（两个键必须同时命中同一类型） */
    private static final Map<String, String> MIME_WHITELIST = Map.of(
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "pdf", "application/pdf");

    /** UC-16 E2：≤20MB（先校验后落盘，S-07） */
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    /** DB file_name varchar(128) 上限（超长截断防插入失败） */
    private static final int MAX_FILE_NAME_LEN = 128;
    /** 解析状态机（V3 §7.1 四态） */
    private static final String PARSE_STATUS_PENDING = "PENDING";
    /** S-03/S-04 签名URL有效期（契约：5分钟） */
    private static final int PRESIGN_EXPIRY_SECONDS = 300;
    /** S-03 文本预览截断长度（全量在 parsed_text_key 对象） */
    private static final int TEXT_PREVIEW_LIMIT = 2000;

    /** S-02 分页上界（防 size 过大拖库） */
    private static final long MAX_PAGE_SIZE = 100;

    private final SubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final TaskService taskService;
    private final FileStorage fileStorage;
    private final VirusScanHook virusScanHook;
    private final RabbitTemplate rabbitTemplate;
    private final EvaluationResultMapper evaluationResultMapper;

    /**
     * S-01 提交作业（UC-16）：版本号自增（重复上传新版本覆盖展示、旧版保留可追溯——UC-16 A1）。
     *
     * <p>UC-16 基本流第5步已接入（W7-C1）：落库 PENDING → 事务提交后发解析消息 →
     * ParseWorker 推进 QUEUED→SUCCESS/FAILED（三级解析的 OCR 级属后续扩展）。</p>
     */
    @Transactional
    public SubmissionVO submit(long taskId, MultipartFile file, LoginPrincipal principal) {
        // 权限点 submission:upload 仅学生组（@PreAuthorize 已拦教师/管理员），防御兜底
        if (!ROLE_STUDENT.equals(principal.role())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        // S-05 IDOR：任务可见性复用 task 域同形规则（学生=本班+PUBLISHED，否则40400防探测）
        Task task = taskService.requireVisible(taskId, principal);

        // UC-16 E3：已截止（或已关闭）→40002；以应用服务器当前时间为基准（DB create_time 为落库时刻）
        if (STATUS_CLOSED.equals(task.getStatus())
                || task.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TASK_NOT_OPEN);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "文件为空");
        }
        // UC-16 E2：≤20MB →30003（S-07：先校验后落盘）
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        // UC-16 E1：扩展名+MIME 双重白名单 →30001（S-07：禁只信扩展名或只信 Content-Type）
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extensionOf(originalName);
        String expectedMime = MIME_WHITELIST.get(ext);
        if (expectedMime == null || !expectedMime.equalsIgnoreCase(file.getContentType())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
        }
        // R-02 病毒扫描钩子（W7 接真实实现；查杀命中→同径 30001 拒绝）
        if (!virusScanHook.clean(file)) {
            log.warn("病毒扫描命中，拒绝落盘: taskId={}, student={}, name={}",
                    taskId, principal.userId(), originalName);
            throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED.getCode(), "文件未通过安全扫描");
        }

        // 版本号自增（uk_task_student_version 唯一键兜底并发重复提交）
        int version = submissionMapper.selectMaxVersion(taskId, principal.userId()) + 1;
        // key=UUID32（S-07 用户输入禁入 key），与 MinIO 对象 key 同形（D-23 桶化零迁移）
        String fileKey = UUID.randomUUID().toString().replace("-", "");
        try (InputStream in = file.getInputStream()) {
            fileStorage.store(fileKey, in, file.getSize());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "文件落盘失败，请重试");
        }

        Submission submission = new Submission();
        submission.setTaskId(taskId);
        submission.setStudentId(principal.userId());
        submission.setFileKey(fileKey);
        submission.setFileName(truncateName(originalName));
        submission.setFileSize(file.getSize());
        submission.setFileType(ext);
        submission.setVersionNo(version);
        submission.setParseStatus(PARSE_STATUS_PENDING);
        try {
            submissionMapper.insert(submission);
        } catch (DuplicateKeyException e) {
            // 并发撞唯一键：全局处理器把 DuplicateKey 映射为 30004（学号语义），此处就地纠正
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "提交冲突，请重试");
        }
        // UC-16 基本流第5步：事务提交后再发解析消息（避免 worker 先于提交可见读到空）
        sendParseMessageAfterCommit(submission.getId());
        return SubmissionVO.from(submission);
    }

    /** S-02 我的提交列表（UC-17）：仅本人服务端过滤，task_id 可选筛选，按任务分组+版本倒序 */
    public Page<SubmissionMineVO> mine(long studentId, Long taskId, long page, long size) {
        Page<Submission> result = submissionMapper.selectPage(
                new Page<>(Math.min(Math.max(page, 1), MAX_PAGE_SIZE), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)),
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getStudentId, studentId)
                        .eq(taskId != null, Submission::getTaskId, taskId)
                        .orderByAsc(Submission::getTaskId)
                        .orderByDesc(Submission::getVersionNo));
        Map<Long, EvaluationResult> evalMap = evaluationResultMap(result.getRecords());
        Page<SubmissionMineVO> vos = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        vos.setRecords(result.getRecords().stream()
                .map(s -> {
                    EvaluationResult er = evalMap.get(s.getId());
                    return SubmissionMineVO.from(s, er == null ? null : er.getId(),
                            er == null ? null : er.getStatus());
                })
                .toList());
        return vos;
    }

    /** S-03 提交详情（UC-18）：归属校验(40400同形) + 签名URL(5min) + 提取文本预览 + 评价状态 */
    public SubmissionDetailVO detail(long submissionId, LoginPrincipal principal) {
        Submission submission = requireOwned(submissionId, principal);
        EvaluationResult eval = evaluationResultMap(List.of(submission)).get(submissionId);
        SubmissionDetailVO vo = new SubmissionDetailVO();
        vo.setId(submission.getId());
        vo.setTaskId(submission.getTaskId());
        vo.setFileName(submission.getFileName());
        vo.setFileSize(submission.getFileSize());
        vo.setFileType(submission.getFileType());
        vo.setVersionNo(submission.getVersionNo());
        vo.setParseStatus(submission.getParseStatus());
        vo.setCreateTime(submission.getCreateTime());
        vo.setEvaluationStatus(eval == null ? "PENDING" : eval.getStatus());
        // W7-C2 加法细化：UC-21 报告页跳转锚点
        vo.setEvaluationId(eval == null ? null : eval.getId());

        // S-04 同源：GET 预签名 URL（minio 模式；本地模式→50000，登记口径）
        vo.setDownloadUrl(fileStorage.presignedGetUrl(submission.getFileKey(), PRESIGN_EXPIRY_SECONDS));

        if (submission.getParsedTextKey() != null) {
            try (InputStream in = fileStorage.openStream(submission.getParsedTextKey())) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                vo.setTextChars(text.length());
                vo.setTextPreview(text.substring(0, Math.min(text.length(), TEXT_PREVIEW_LIMIT)));
            } catch (IOException e) {
                log.error("提取文本读取失败: key={}", submission.getParsedTextKey(), e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "提取文本读取失败");
            }
        }
        return vo;
    }

    /** S-04 下载签名URL（302 目标，S-03 downloadUrl 同源同有效期） */
    public String downloadUrl(long submissionId, LoginPrincipal principal) {
        Submission submission = requireOwned(submissionId, principal);
        return fileStorage.presignedGetUrl(submission.getFileKey(), PRESIGN_EXPIRY_SECONDS);
    }

    /** IDOR 防线（S-05/04-04 §三）：仅本人可见，非本人/不存在一律 40400 同形防探测 */
    private Submission requireOwned(long submissionId, LoginPrincipal principal) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null || !submission.getStudentId().equals(principal.userId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return submission;
    }

    /** 批量联查评价记录（UC-17列表 + UC-21锚点；无记录→VO层回填PENDING/null） */
    private Map<Long, EvaluationResult> evaluationResultMap(List<Submission> submissions) {
        List<Long> ids = submissions.stream().map(Submission::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return evaluationResultMapper.selectList(new LambdaQueryWrapper<EvaluationResult>()
                        .in(EvaluationResult::getSubmissionId, ids))
                .stream()
                .collect(Collectors.toMap(EvaluationResult::getSubmissionId,
                        er -> er, (a, b) -> a));
    }

    /** UC-16 第5步：事务提交后投递解析消息；发送失败留 PENDING 可重放（黄金集回放同口径） */
    private void sendParseMessageAfterCommit(long submissionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchParseMessage(submissionId);
                }
            });
        } else {
            dispatchParseMessage(submissionId);
        }
    }

    private void dispatchParseMessage(long submissionId) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.PARSE_EXCHANGE,
                    RabbitConfig.PARSE_ROUTING_KEY, new ParseMessage(submissionId));
            log.info("解析消息已投递: submissionId={}", submissionId);
        } catch (Exception e) {
            // 提交已成功，解析链可重放（PENDING 留存），不让消息故障反噬提交接口
            log.error("解析消息发送失败(留PENDING可重放): submissionId={} err={}", submissionId, e.getMessage());
        }
    }

    /** 小写扩展名（无扩展名→空串即白名单不命中）；文件名仅取路径最后一节（S-07 防路径携带） */
    private String extensionOf(String originalName) {
        String name = Paths.get(originalName.replace("\\", "/")).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    private String truncateName(String originalName) {
        return originalName.length() <= MAX_FILE_NAME_LEN
                ? originalName : originalName.substring(0, MAX_FILE_NAME_LEN);
    }
}
