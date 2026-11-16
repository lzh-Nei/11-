package com.aies.controller;

import com.aies.common.PermissionConstants;
import com.aies.common.Result;
import com.aies.dto.EvaluationVO;
import com.aies.dto.TeacherWorkflowVO;
import com.aies.security.PrincipalUtils;
import com.aies.service.TeacherWorkflowService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师评价工作流：提交队列、手动 AI 初评、一键通过 AI 结果。
 */
@RestController
@RequestMapping("/api/teacher/workflow")
@RequiredArgsConstructor
@Validated
public class TeacherWorkflowController {

    private final TeacherWorkflowService workflowService;

    @PreAuthorize("hasAuthority('" + PermissionConstants.SUBMISSION_VIEW + "')")
    @GetMapping("/submissions")
    public Result<Page<TeacherWorkflowVO>> submissions(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为1") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为1")
            @Max(value = 100, message = "每页条数最大为100") long size,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String stage) {
        return Result.success(workflowService.list(page, size, classId, keyword, stage,
                PrincipalUtils.current()));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.TASK_EDIT + "')")
    @PostMapping("/submissions/{id}/ai-evaluate")
    public Result<Void> triggerAi(@PathVariable("id") long id) {
        workflowService.triggerAi(id, PrincipalUtils.current());
        return Result.success();
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.EVALUATION_REVIEW + "')")
    @PostMapping("/evaluations/{id}/approve")
    public Result<EvaluationVO> approve(@PathVariable("id") long id) {
        return Result.success(workflowService.approve(id, PrincipalUtils.current()));
    }
}
