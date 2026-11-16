package com.aies.controller;

import com.aies.common.PermissionConstants;
import com.aies.common.Result;
import com.aies.model.ReportOverviewDTO;
import com.aies.security.LoginPrincipal;
import com.aies.service.ReportQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private LoginPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginPrincipal principal)) {
            throw new IllegalStateException("未认证请求不应到达本方法");
        }
        return principal;
    }
}
