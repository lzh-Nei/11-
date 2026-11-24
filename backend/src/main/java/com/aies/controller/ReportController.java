package com.aies.controller;

import com.aies.common.PermissionConstants;
import com.aies.common.Result;
import com.aies.dto.ReportExportVO;
import com.aies.model.ReportOverviewDTO;
import com.aies.security.LoginPrincipal;
import com.aies.service.ReportCsvFormatter;
import com.aies.service.ReportExportService;
import com.aies.service.ReportQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 报表域控制器（UC-25/UC-30 对标，C1 v1.0 D-36 裁决版）。
 *
 * <p>权限矩阵（D-36 确认点1 批准）：教师=任教任务聚合；学生=/me 独立端点
 * （端点设计层消除 studentId 暴露，IDOR 同形 40400 保留为纵深防御）；
 * 管理员全局聚合=M3+ 候选（留痕见 03-API 回写注记）。</p>
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportQueryService reportQueryService;
    private final ReportExportService reportExportService;
    private final ReportCsvFormatter csvFormatter;

    /** 教师四图聚合（任教任务）。 */
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_VIEW_TEACHER + "')")
    @GetMapping("/overview")
    public Result<ReportOverviewDTO> overview() {
        return Result.success(reportQueryService.overviewForTeacher(currentPrincipal().userId()));
    }

    /** 学生个人四图（仅本人提交）。 */
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_VIEW_STUDENT + "')")
    @GetMapping("/overview/me")
    public Result<ReportOverviewDTO> overviewMe() {
        return Result.success(reportQueryService.overviewForMe(currentPrincipal().userId()));
    }

    /** 缓存强制重建（UC-30 对标；教师 scope 或学生 scope 由权限点天然隔离）。 */
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_REFRESH + "')")
    @PostMapping("/refresh")
    public Result<Void> refresh() {
        LoginPrincipal p = currentPrincipal();
        String scope = p.role().equals("TEACHER") ? "teacher" : "me";
        reportQueryService.refresh(scope, p.userId());
        return Result.success(null);
    }

    /**
     * 报表导出（report:export）：复用当前用户可见范围的聚合结果，输出 UTF-8 BOM CSV。
     * 教师=任教任务范围；其余角色=本人范围（与 /overview(/me) 口径一致）。
     */
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_EXPORT + "')")
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        LoginPrincipal p = currentPrincipal();
        ReportOverviewDTO dto = "TEACHER".equals(p.role())
                ? reportQueryService.overviewForTeacher(p.userId())
                : reportQueryService.overviewForMe(p.userId());
        writeCsv(response, dto, "报表-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + ".csv");
    }

    /** 创建异步导出任务（QUEUED→GENERATING→DONE/FAILED，由创建人轮询） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_EXPORT + "')")
    @PostMapping("/exports")
    public Result<ReportExportVO> createExport() {
        ReportExportVO task = reportExportService.create(currentPrincipal());
        reportExportService.generate(task.id());
        return Result.success(task);
    }

    /** 导出任务状态（仅创建人） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_EXPORT + "')")
    @GetMapping("/exports/{id}")
    public Result<ReportExportVO> exportStatus(@PathVariable("id") long id) {
        return Result.success(reportExportService.status(id, currentPrincipal()));
    }

    /** 导出结果下载（仅创建人且任务已完成） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_EXPORT + "')")
    @GetMapping("/exports/{id}/download")
    public void downloadExport(@PathVariable("id") long id,
                               HttpServletResponse response) throws IOException {
        ReportExportService.ExportFile file = reportExportService.download(id, currentPrincipal());
        response.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8").toString());
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encoded);
        try (InputStream in = file.stream(); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        }
    }

    private void writeCsv(HttpServletResponse response, ReportOverviewDTO dto, String fileName)
            throws IOException {
        response.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8").toString());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encoded);
        // UTF-8 BOM：Excel 直接双击打开不乱码
        response.getWriter().write('\uFEFF');
        response.getWriter().write(csvFormatter.toCsv(dto));
        response.getWriter().flush();
    }

    private LoginPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginPrincipal principal)) {
            throw new IllegalStateException("未认证请求不应到达本方法");
        }
        return principal;
    }
}
