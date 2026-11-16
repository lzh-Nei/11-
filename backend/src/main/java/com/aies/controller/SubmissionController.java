package com.aies.controller;

import com.aies.common.PermissionConstants;
import com.aies.common.Result;
import com.aies.dto.SubmissionDetailVO;
import com.aies.dto.SubmissionMineVO;
import com.aies.dto.SubmissionVO;
import com.aies.security.LoginPrincipal;
import com.aies.service.SubmissionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 提交接口（03-03 §六 S-01~S-04 契约；W7-C1 全量接入）。
 *
 * <p>写接口 @PreAuthorize 权限点对应 PermissionConstants（S-06）；
 * 校验顺序与偏差登记见 SubmissionService。</p>
 */
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /** S-01 提交作业（UC-16）：截止→40002；格式→30001；大小→30003；权限点 submission:upload */
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUBMISSION_UPLOAD + "')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<SubmissionVO> submit(@RequestParam("taskId") long taskId,
                                       @RequestParam("file") MultipartFile file) {
        return Result.success(submissionService.submit(taskId, file, currentPrincipal()));
    }

    /** S-02 我的提交列表（UC-17）：仅本人服务端过滤，task_id 筛选+分页，含评价状态 */
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUBMISSION_VIEW + "')")
    @GetMapping("/mine")
    public Result<Page<SubmissionMineVO>> mine(
            @RequestParam(value = "taskId", required = false) Long taskId,
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "10") long size) {
        return Result.success(submissionService.mine(currentPrincipal().userId(), taskId, page, size));
    }

    /** S-03 提交详情（UC-18）：归属校验(40400同形) + 签名URL(5min) + 提取文本预览 */
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUBMISSION_VIEW + "')")
    @GetMapping("/{id}")
    public Result<SubmissionDetailVO> detail(@PathVariable long id) {
        return Result.success(submissionService.detail(id, currentPrincipal()));
    }

    /** S-04 下载（UC-18）：302 至 MinIO 预签名 URL（5分钟有效，私有桶直连） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUBMISSION_VIEW + "')")
    @GetMapping("/{id}/download")
    public ResponseEntity<Void> download(@PathVariable long id) {
        String url = submissionService.downloadUrl(id, currentPrincipal());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }

    /** 当前主体（JWT过滤器装配；未登录时此路径不可达） */
    private LoginPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginPrincipal principal)) {
            throw new com.aies.common.BusinessException(com.aies.common.ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }
}
