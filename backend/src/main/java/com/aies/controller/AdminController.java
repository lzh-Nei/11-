package com.aies.controller;

import com.aies.common.PermissionConstants;
import com.aies.common.Result;
import com.aies.dto.admin.AdminDtos.AdminClassVO;
import com.aies.dto.admin.AdminDtos.AdminEvaluationVO;
import com.aies.dto.admin.AdminDtos.AdminPermissionVO;
import com.aies.dto.admin.AdminDtos.AdminRoleVO;
import com.aies.dto.admin.AdminDtos.AdminTeacherVO;
import com.aies.dto.admin.AdminDtos.AdminUserVO;
import com.aies.dto.admin.AdminDtos.AuditLogFilter;
import com.aies.dto.admin.AdminDtos.AuditLogVO;
import com.aies.dto.admin.AdminDtos.AuditStatsVO;
import com.aies.dto.admin.AdminDtos.RuntimeLogPageVO;
import com.aies.dto.admin.AdminDtos.ClassCreateRequest;
import com.aies.dto.admin.AdminDtos.ClassUpdateRequest;
import com.aies.dto.admin.AdminDtos.CreatePromptRequest;
import com.aies.dto.admin.AdminDtos.CreateUserRequest;
import com.aies.dto.admin.AdminDtos.GoldenSampleVO;
import com.aies.dto.admin.AdminDtos.LlmConfigVO;
import com.aies.dto.admin.AdminDtos.LlmTestResult;
import com.aies.dto.admin.AdminDtos.MqMonitorVO;
import com.aies.dto.admin.AdminDtos.PromptDetailVO;
import com.aies.dto.admin.AdminDtos.PromptTemplateVO;
import com.aies.dto.admin.AdminDtos.ResetPasswordResult;
import com.aies.dto.admin.AdminDtos.ResetUserPasswordRequest;
import com.aies.dto.admin.AdminDtos.SystemConfigUpdateRequest;
import com.aies.dto.admin.AdminDtos.SystemConfigVO;
import com.aies.dto.admin.AdminDtos.ToggleActiveRequest;
import com.aies.dto.admin.AdminDtos.UpdateLlmConfigRequest;
import com.aies.dto.admin.AdminDtos.UpdateRolePermissionsRequest;
import com.aies.dto.admin.AdminDtos.UpdateUserRolesRequest;
import com.aies.dto.admin.AdminDtos.UpdateUserRequest;
import com.aies.dto.admin.AdminDtos.UpdateUserStatusRequest;
import com.aies.dto.admin.AdminDtos.UserDeletionImpactVO;
import com.aies.security.PrincipalUtils;
import com.aies.service.AdminAiService;
import com.aies.service.AdminClassService;
import com.aies.service.AdminEvaluationService;
import com.aies.service.AdminLogService;
import com.aies.service.AdminRbacService;
import com.aies.service.AdminSystemService;
import com.aies.service.AdminUserService;
import com.aies.service.MqMonitorService;
import com.aies.service.MqReplayService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * M3 管理端接口（用户/角色权限/班级/审计/LLM/Prompt/黄金集/系统配置）。
 *
 * <p>权限点全部复用 V1 预留的 system 段（user/role/class/system/audit/llm/prompt/golden），
 * 不新增矩阵项。所有写操作经 AdminAuditService 留痕（S-09）。</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
public class AdminController {

    private final AdminUserService userService;
    private final AdminRbacService rbacService;
    private final AdminClassService classService;
    private final AdminAiService aiService;
    private final AdminSystemService systemService;
    private final AdminLogService logService;
    private final AdminEvaluationService evaluationService;
    private final MqMonitorService mqMonitorService;
    private final MqReplayService mqReplayService;

    /* ==================== 用户管理（user:manage） ==================== */

    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_MANAGE + "')")
    @GetMapping("/users")
    public Result<Page<AdminUserVO>> users(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为1") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为1")
            @Max(value = 100, message = "每页条数最大为100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String roleCode) {
        return Result.success(userService.list(page, size, keyword, status, roleCode));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_MANAGE + "')")
    @PostMapping("/users")
    public Result<AdminUserVO> createUser(@Valid @RequestBody CreateUserRequest request) {
        return Result.success(userService.create(request, PrincipalUtils.current()));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_MANAGE + "')")
    @PutMapping("/users/{id}")
    public Result<AdminUserVO> updateUser(@PathVariable("id") @Positive long id,
                                          @Valid @RequestBody UpdateUserRequest request) {
        return Result.success(userService.update(id, request, PrincipalUtils.current()));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_MANAGE + "')")
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable("id") @Positive long id) {
        userService.delete(id, PrincipalUtils.current());
        return Result.success();
    }

    /** 删除前关联记录快照（普通删除失败时，管理端据此提示是否强制清理） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_MANAGE + "')")
    @GetMapping("/users/{id}/deletion-impact")
    public Result<UserDeletionImpactVO> userDeletionImpact(@PathVariable("id") @Positive long id) {
        return Result.success(userService.deletionImpact(id, PrincipalUtils.current()));
    }

    /** 强制删除：清理关联记录后删除用户，必须由管理端二次确认后调用 */
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_MANAGE + "')")
    @DeleteMapping("/users/{id}/force")
    public Result<Void> forceDeleteUser(@PathVariable("id") @Positive long id) {
        userService.forceDelete(id, PrincipalUtils.current());
        return Result.success();
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_MANAGE + "')")
    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable("id") @Positive long id,
                                         @Valid @RequestBody UpdateUserStatusRequest request) {
        userService.updateStatus(id, request.getStatus(), PrincipalUtils.current());
        return Result.success();
    }

    /** 重置用户密码：留空生成临时密码（仅生成时回传一次），并置首登改密标记 */
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_MANAGE + "')")
    @PostMapping("/users/{id}/reset-password")
    public Result<ResetPasswordResult> resetPassword(
            @PathVariable("id") @Positive long id,
            @Valid @RequestBody ResetUserPasswordRequest request) {
        return Result.success(userService.resetPassword(id, request, PrincipalUtils.current()));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_MANAGE + "')")
    @PutMapping("/users/{id}/roles")
    public Result<Void> updateUserRoles(@PathVariable("id") @Positive long id,
                                        @Valid @RequestBody UpdateUserRolesRequest request) {
        userService.updateRoles(id, request.getRoleCodes(), PrincipalUtils.current());
        return Result.success();
    }

    /** 教师下拉（班级管理创建/换教师共用；user:manage 或 class:manage 可读） */
    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.USER_MANAGE + "','"
            + PermissionConstants.CLASS_MANAGE + "')")
    @GetMapping("/teachers")
    public Result<List<AdminTeacherVO>> teachers() {
        return Result.success(userService.teachers());
    }

    /* ==================== 管理端评价列表（管理员查看教师评价/复核历史） ==================== */

    @PreAuthorize("hasAuthority('" + PermissionConstants.EVALUATION_VIEW + "')")
    @GetMapping("/evaluations")
    public Result<Page<AdminEvaluationVO>> evaluations(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为1") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为1")
            @Max(value = 100, message = "每页条数最大为100") long size,
            @RequestParam(required = false) String status) {
        return Result.success(evaluationService.list(page, size, status, PrincipalUtils.current()));
    }

    /* ==================== 角色权限管理（role:manage） ==================== */

    @PreAuthorize("hasAuthority('" + PermissionConstants.ROLE_MANAGE + "')")
    @GetMapping("/roles")
    public Result<List<AdminRoleVO>> roles() {
        return Result.success(rbacService.roles());
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.ROLE_MANAGE + "')")
    @GetMapping("/permissions")
    public Result<List<AdminPermissionVO>> permissions() {
        return Result.success(rbacService.permissions());
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.ROLE_MANAGE + "')")
    @PutMapping("/roles/{id}/permissions")
    public Result<Void> updateRolePermissions(@PathVariable("id") @Positive long id,
                                              @Valid @RequestBody UpdateRolePermissionsRequest request) {
        rbacService.updateRolePermissions(id, request.getPermCodes(), PrincipalUtils.current());
        return Result.success();
    }

    /* ==================== 班级管理（class:manage） ==================== */

    @PreAuthorize("hasAuthority('" + PermissionConstants.CLASS_MANAGE + "')")
    @GetMapping("/classes")
    public Result<Page<AdminClassVO>> classes(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为1") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为1")
            @Max(value = 100, message = "每页条数最大为100") long size) {
        return Result.success(classService.list(page, size));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.CLASS_MANAGE + "')")
    @PostMapping("/classes")
    public Result<AdminClassVO> createClass(@Valid @RequestBody ClassCreateRequest request) {
        return Result.success(classService.create(request, PrincipalUtils.current()));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.CLASS_MANAGE + "')")
    @PutMapping("/classes/{id}")
    public Result<AdminClassVO> updateClass(@PathVariable("id") @Positive long id,
                                            @Valid @RequestBody ClassUpdateRequest request) {
        return Result.success(classService.update(id, request, PrincipalUtils.current()));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.CLASS_MANAGE + "')")
    @DeleteMapping("/classes/{id}")
    public Result<Void> deleteClass(@PathVariable("id") @Positive long id) {
        classService.delete(id, PrincipalUtils.current());
        return Result.success();
    }

    /* ==================== 日志采集与审计窗口（audit:view，仅管理员） ==================== */

    @PreAuthorize("hasAuthority('" + PermissionConstants.AUDIT_VIEW + "')")
    @GetMapping("/audit-logs")
    public Result<Page<AuditLogVO>> auditLogs(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为1") long page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数最小为1")
            @Max(value = 100, message = "每页条数最大为100") long size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        AuditLogFilter filter = new AuditLogFilter(module, action, keyword, operator, start, end);
        return Result.success(systemService.auditLogs(page, size, filter));
    }

    /** 审计窗口概览：总量/今日/近24h + 模块/动作分布 + 近7日趋势 */
    @PreAuthorize("hasAuthority('" + PermissionConstants.AUDIT_VIEW + "')")
    @GetMapping("/audit-logs/stats")
    public Result<AuditStatsVO> auditStats() {
        return Result.success(logService.auditStats(PrincipalUtils.current()));
    }

    /** 运行日志分页（内存采集缓冲，级别阈值/关键字/logger 过滤） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.AUDIT_VIEW + "')")
    @GetMapping("/logs/runtime")
    public Result<RuntimeLogPageVO> runtimeLogs(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为1") long page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数最小为1")
            @Max(value = 200, message = "每页条数最大为200") long size,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String logger) {
        return Result.success(logService.runtimeLogs(page, size, level, keyword, logger,
                PrincipalUtils.current()));
    }

    /** 清空内存采集缓冲（审计留痕，仅管理员） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.AUDIT_VIEW + "')")
    @DeleteMapping("/logs/runtime")
    public Result<Void> clearRuntimeLogs() {
        logService.clearRuntimeLogs(PrincipalUtils.current());
        return Result.success();
    }

    /* ==================== 系统配置（system:config，只读） ==================== */

    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_CONFIG + "')")
    @GetMapping("/system/config")
    public Result<SystemConfigVO> systemConfig() {
        return Result.success(systemService.config());
    }

    /** 更新白名单系统参数（类型/范围校验，变更后报表缓存全量失效） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_CONFIG + "')")
    @PutMapping("/system/config")
    public Result<Void> updateSystemConfig(@Valid @RequestBody SystemConfigUpdateRequest request) {
        systemService.updateConfig(request.getValues(), PrincipalUtils.current());
        return Result.success();
    }

    /* ==================== 消息队列监控与重放（system:config） ==================== */

    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_CONFIG + "')")
    @GetMapping("/mq/status")
    public Result<MqMonitorVO> mqStatus() {
        return Result.success(mqMonitorService.status());
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_CONFIG + "')")
    @PostMapping("/mq/submissions/{id}/replay-parse")
    public Result<Void> replayParse(@PathVariable("id") @Positive long id) {
        mqReplayService.replayParse(id, PrincipalUtils.current());
        return Result.success();
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_CONFIG + "')")
    @PostMapping("/mq/submissions/{id}/replay-evaluation")
    public Result<Void> replayEvaluation(@PathVariable("id") @Positive long id) {
        mqReplayService.replayEvaluation(id, PrincipalUtils.current());
        return Result.success();
    }

    /* ==================== LLM 参数（llm:config） ==================== */

    @PreAuthorize("hasAuthority('" + PermissionConstants.LLM_CONFIG + "')")
    @GetMapping("/llm-configs")
    public Result<List<LlmConfigVO>> llmConfigs() {
        return Result.success(aiService.llmConfigs());
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.LLM_CONFIG + "')")
    @PutMapping("/llm-configs/{id}")
    public Result<Void> updateLlmConfig(@PathVariable("id") @Positive long id,
                                        @Valid @RequestBody UpdateLlmConfigRequest request) {
        aiService.updateLlmConfig(id, request, PrincipalUtils.current());
        return Result.success();
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.LLM_CONFIG + "')")
    @PostMapping("/llm-configs/{id}/test")
    public Result<LlmTestResult> testLlmConfig(@PathVariable("id") @Positive long id) {
        return Result.success(aiService.testLlmConfig(id));
    }

    /* ==================== Prompt 模板（prompt:manage） ==================== */

    @PreAuthorize("hasAuthority('" + PermissionConstants.PROMPT_MANAGE + "')")
    @GetMapping("/prompts")
    public Result<List<PromptTemplateVO>> prompts() {
        return Result.success(aiService.prompts());
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.PROMPT_MANAGE + "')")
    @GetMapping("/prompts/{id}")
    public Result<PromptDetailVO> promptDetail(@PathVariable("id") @Positive long id) {
        return Result.success(aiService.promptDetail(id));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.PROMPT_MANAGE + "')")
    @PostMapping("/prompts")
    public Result<PromptDetailVO> createPrompt(@Valid @RequestBody CreatePromptRequest request) {
        return Result.success(aiService.createPrompt(request, PrincipalUtils.current()));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.PROMPT_MANAGE + "')")
    @PutMapping("/prompts/{id}/enable")
    public Result<Void> enablePrompt(@PathVariable("id") @Positive long id) {
        aiService.enablePrompt(id, PrincipalUtils.current());
        return Result.success();
    }

    /** 编辑模板（仅未启用版本） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.PROMPT_MANAGE + "')")
    @PutMapping("/prompts/{id}")
    public Result<PromptDetailVO> updatePrompt(@PathVariable("id") @Positive long id,
                                               @Valid @RequestBody CreatePromptRequest request) {
        return Result.success(aiService.updatePrompt(id, request, PrincipalUtils.current()));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.PROMPT_MANAGE + "')")
    @PutMapping("/prompts/{id}/disable")
    public Result<Void> disablePrompt(@PathVariable("id") @Positive long id) {
        aiService.disablePrompt(id, PrincipalUtils.current());
        return Result.success();
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.PROMPT_MANAGE + "')")
    @DeleteMapping("/prompts/{id}")
    public Result<Void> deletePrompt(@PathVariable("id") @Positive long id) {
        aiService.deletePrompt(id, PrincipalUtils.current());
        return Result.success();
    }

    /* ==================== 黄金数据集（golden:manage） ==================== */

    @PreAuthorize("hasAuthority('" + PermissionConstants.GOLDEN_MANAGE + "')")
    @GetMapping("/golden-samples")
    public Result<Page<GoldenSampleVO>> goldenSamples(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为1") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为1")
            @Max(value = 100, message = "每页条数最大为100") long size) {
        return Result.success(aiService.goldenSamples(page, size));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.GOLDEN_MANAGE + "')")
    @PutMapping("/golden-samples/{id}/active")
    public Result<Void> toggleGoldenActive(@PathVariable("id") @Positive long id,
                                           @Valid @RequestBody ToggleActiveRequest request) {
        aiService.setGoldenActive(id, request.getActive(), PrincipalUtils.current());
        return Result.success();
    }

    /** 上传黄金样本（docx/pdf ≤20MB，教师总分 0-100，可选指标 JSON） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.GOLDEN_MANAGE + "')")
    @PostMapping(value = "/golden-samples", consumes = "multipart/form-data")
    public Result<GoldenSampleVO> createGoldenSample(
            @RequestParam("file") MultipartFile file,
            @RequestParam("teacherTotal") BigDecimal teacherTotal,
            @RequestParam(value = "teacherMetrics", required = false) String teacherMetrics) {
        return Result.success(aiService.createGoldenSample(file, teacherTotal, teacherMetrics,
                PrincipalUtils.current()));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.GOLDEN_MANAGE + "')")
    @DeleteMapping("/golden-samples/{id}")
    public Result<Void> deleteGoldenSample(@PathVariable("id") @Positive long id) {
        aiService.deleteGoldenSample(id, PrincipalUtils.current());
        return Result.success();
    }
}
