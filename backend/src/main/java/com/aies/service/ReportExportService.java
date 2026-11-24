package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.ReportExportVO;
import com.aies.entity.ReportExportTask;
import com.aies.mapper.ReportExportTaskMapper;
import com.aies.model.ReportOverviewDTO;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * 异步报表导出：创建任务 → 线程池生成 CSV 落对象存储 → 轮询状态 → 仅创建人下载。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportService {

    static final String STATUS_QUEUED = "QUEUED";
    static final String STATUS_GENERATING = "GENERATING";
    static final String STATUS_DONE = "DONE";
    static final String STATUS_FAILED = "FAILED";
    private static final int MAX_ERROR_LEN = 500;
    private static final String SCOPE_TEACHER = "teacher";

    private final ReportExportTaskMapper taskMapper;
    private final ReportQueryService reportQueryService;
    private final ReportCsvFormatter csvFormatter;
    private final FileStorage fileStorage;
    private final ObjectMapper objectMapper;

    /** 创建导出任务并返回 QUEUED 状态（controller 随后调用 {@link #generate(long)} 异步执行） */
    @Transactional
    public ReportExportVO create(LoginPrincipal principal) {
        String scope = SCOPE_TEACHER.equals(principal.role()) ? SCOPE_TEACHER : "me";
        ReportExportTask task = new ReportExportTask();
        task.setRequestId(UUID.randomUUID().toString().replace("-", ""));
        task.setRequesterId(principal.userId());
        task.setFilterJson("{\"scope\":\"" + scope + "\"}");
        task.setStatus(STATUS_QUEUED);
        task.setCreateTime(LocalDateTime.now());
        taskMapper.insert(task);
        return toVO(task);
    }

    /** 异步生成（独立线程池；状态从 QUEUED → GENERATING → DONE/FAILED） */
    @Async("reportExportExecutor")
    public void generate(long id) {
        ReportExportTask task = taskMapper.selectById(id);
        if (task == null || !STATUS_QUEUED.equals(task.getStatus())) {
            return;
        }
        taskMapper.update(null, new UpdateWrapper<ReportExportTask>()
                .eq("id", id)
                .set("status", STATUS_GENERATING));
        try {
            ReportOverviewDTO dto = SCOPE_TEACHER.equals(scopeOf(task))
                    ? reportQueryService.overviewForTeacher(task.getRequesterId())
                    : reportQueryService.overviewForMe(task.getRequesterId());
            String csv = "\uFEFF" + csvFormatter.toCsv(dto);
            byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
            String key = "report/" + UUID.randomUUID().toString().replace("-", "") + ".csv";
            fileStorage.store(key, new ByteArrayInputStream(bytes), bytes.length);
            String fileName = "报表-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + id + ".csv";
            taskMapper.update(null, new UpdateWrapper<ReportExportTask>()
                    .eq("id", id)
                    .set("status", STATUS_DONE)
                    .set("file_key", key)
                    .set("file_name", fileName)
                    .set("error_msg", null)
                    .set("finish_time", LocalDateTime.now()));
            log.info("报表导出完成: id={} key={} bytes={}", id, key, bytes.length);
        } catch (Exception e) {
            log.error("报表导出失败: id={} err={}", id, e.getMessage());
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (message.length() > MAX_ERROR_LEN) {
                message = message.substring(0, MAX_ERROR_LEN);
            }
            taskMapper.update(null, new UpdateWrapper<ReportExportTask>()
                    .eq("id", id)
                    .set("status", STATUS_FAILED)
                    .set("error_msg", message)
                    .set("finish_time", LocalDateTime.now()));
        }
    }

    /** 状态查询（仅创建人；越权与不存在同形 40400） */
    public ReportExportVO status(long id, LoginPrincipal principal) {
        return toVO(requireOwned(id, principal));
    }

    /** 下载结果（仅 DONE 且创建人） */
    public ExportFile download(long id, LoginPrincipal principal) {
        ReportExportTask task = requireOwned(id, principal);
        if (!STATUS_DONE.equals(task.getStatus()) || task.getFileKey() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "导出任务尚未完成");
        }
        return new ExportFile(task.getFileName(), fileStorage.openStream(task.getFileKey()));
    }

    /** 下载流元信息（调用方负责关闭 stream） */
    public record ExportFile(String fileName, InputStream stream) {
    }

    private ReportExportTask requireOwned(long id, LoginPrincipal principal) {
        ReportExportTask task = taskMapper.selectById(id);
        if (task == null || !Objects.equals(task.getRequesterId(), principal.userId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return task;
    }

    private String scopeOf(ReportExportTask task) {
        try {
            JsonNode node = objectMapper.readTree(task.getFilterJson());
            return node.path("scope").asText("me");
        } catch (Exception e) {
            log.warn("导出任务筛选条件解析失败，按学生视角处理: id={}", task.getId());
            return "me";
        }
    }

    private ReportExportVO toVO(ReportExportTask task) {
        return new ReportExportVO(task.getId(), task.getStatus(), task.getFileName(),
                task.getErrorMsg(), task.getCreateTime(), task.getFinishTime());
    }
}
