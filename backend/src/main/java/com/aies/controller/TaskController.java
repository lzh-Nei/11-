package com.aies.controller;

import com.aies.common.PermissionConstants;
import com.aies.common.Result;
import com.aies.dto.TaskCreateRequest;
import com.aies.dto.TaskVO;
import com.aies.security.LoginPrincipal;
import com.aies.service.TaskService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务接口（03-03 §五 T-01/T-04/T-05 契约）。
 *
 * <p>写接口 @PreAuthorize 权限点对应 PermissionConstants（S-06）；
 * 学生/教师视角裁剪在 TaskService 内执行（S-05 IDOR清单）。</p>
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Validated
public class TaskController {

    private final TaskService taskService;

    /** T-01 创建任务（UC-10）：Σ≠100→40001、deadline非法→30006；权限点 task:create */
    @PreAuthorize("hasAuthority('" + PermissionConstants.TASK_CREATE + "')")
    @PostMapping
    public Result<TaskVO> create(@Valid @RequestBody TaskCreateRequest request) {
        return Result.success(taskService.create(request, currentPrincipal()));
    }

    /** T-02 编辑任务（仅草稿）：指标集可改，发布后锁定；权限点 task:edit */
    @PreAuthorize("hasAuthority('" + PermissionConstants.TASK_EDIT + "')")
    @PutMapping("/{id}")
    public Result<TaskVO> update(@PathVariable("id") long id,
                                 @Valid @RequestBody TaskCreateRequest request) {
        return Result.success(taskService.update(id, request, currentPrincipal()));
    }

    /** T-02 删除任务（仅草稿且无提交）：指标集同事务清理；权限点 task:edit */
    @PreAuthorize("hasAuthority('" + PermissionConstants.TASK_EDIT + "')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") long id) {
        taskService.delete(id, currentPrincipal());
        return Result.success();
    }

    /** T-03 任务发布：DRAFT→PUBLISHED 状态机，发布后指标集锁定；权限点 task:publish */
    @PreAuthorize("hasAuthority('" + PermissionConstants.TASK_PUBLISH + "')")
    @PostMapping("/{id}/publish")
    public Result<TaskVO> publish(@PathVariable("id") long id) {
        return Result.success(taskService.publish(id, currentPrincipal()));
    }

    /** T-04 任务详情（UC-13）：角色视角裁剪+归属校验；权限点 task:view */
    @PreAuthorize("hasAuthority('" + PermissionConstants.TASK_VIEW + "')")
    @GetMapping("/{id}")
    public Result<TaskVO> detail(@PathVariable("id") long id) {
        return Result.success(taskService.getById(id, currentPrincipal()));
    }

    /** T-05 任务列表：学生=本班已发布/教师=本人全部/管理员=全局；班级与状态筛选+分页；权限点 task:view */
    @PreAuthorize("hasAuthority('" + PermissionConstants.TASK_VIEW + "')")
    @GetMapping
    public Result<Page<TaskVO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为1") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为1")
            @Max(value = 100, message = "每页条数最大为100") long size,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String status) {
        return Result.success(taskService.list(page, size, classId, status, currentPrincipal()));
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
