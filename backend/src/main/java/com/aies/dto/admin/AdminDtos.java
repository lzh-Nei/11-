package com.aies.dto.admin;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * M3 管理端契约集合（用户/角色权限/班级/审计/LLM/Prompt/黄金集/系统配置）。
 *
 * <p>响应一律 record（ArchUnit 规则4：controller 不暴露 entity）；请求体用 @Data +
 * Bean Validation。所有接口权限点取自 V1 预留的 system 段，未新增权限点。</p>
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /* ==================== 用户管理 ==================== */

    public record AdminUserVO(Long id, String username, String realName, String phone, String email,
                              Long classId, String className, Integer status, Integer firstLogin,
                              List<String> roleCodes, LocalDateTime createTime) {
    }

    public record AdminTeacherVO(Long id, String username, String realName) {
    }

    @Data
    public static class UpdateUserStatusRequest {
        /** 1=正常，0=禁用（禁用后登录 40103、刷新令牌作废） */
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态取值非法")
        @Max(value = 1, message = "状态取值非法")
        private Integer status;
    }

    @Data
    public static class UpdateUserRolesRequest {
        @NotEmpty(message = "至少保留一个角色")
        private List<@NotBlank(message = "角色编码不能为空") String> roleCodes;
    }

    @Data
    public static class CreateUserRequest {
        @NotBlank(message = "学号/工号不能为空")
        @Pattern(regexp = "[A-Za-z0-9_]{4,20}", message = "学号/工号需为4-20位字母、数字或下划线")
        private String username;

        @NotBlank(message = "姓名不能为空")
        @Size(max = 32, message = "姓名最长32字")
        private String realName;

        @NotBlank(message = "初始密码不能为空")
        @Size(min = 8, max = 32, message = "密码长度需为8-32位")
        private String password;

        @Pattern(regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "邮箱格式不正确")
        private String email;

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;

        private Long classId;

        @NotEmpty(message = "至少选择一个角色")
        private List<@NotBlank(message = "角色编码不能为空") String> roleCodes;
    }

    @Data
    public static class UpdateUserRequest {
        @NotBlank(message = "姓名不能为空")
        @Size(max = 32, message = "姓名最长32字")
        private String realName;

        @Pattern(regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "邮箱格式不正确")
        private String email;

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;

        private Long classId;
    }

    /* ==================== 角色权限管理 ==================== */

    public record AdminRoleVO(Long id, String roleCode, String roleName, Integer builtIn,
                              long userCount, List<String> permCodes) {
    }

    public record AdminPermissionVO(Long id, String permCode, String permName, String module) {
    }

    @Data
    public static class UpdateRolePermissionsRequest {
        @NotNull(message = "权限集不能为空")
        private List<@NotBlank(message = "权限编码不能为空") String> permCodes;
    }

    /* ==================== 班级管理 ==================== */

    public record AdminClassVO(Long id, String className, String classCode, Long teacherId,
                               String teacherName, long studentCount, long taskCount,
                               LocalDateTime createTime) {
    }

    @Data
    public static class ClassCreateRequest {
        @NotBlank(message = "班级名称不能为空")
        @Size(max = 64, message = "班级名称最长64字")
        private String className;

        @NotBlank(message = "班级码不能为空")
        @Pattern(regexp = "[A-Za-z0-9-]{4,16}", message = "班级码需为4-16位字母/数字/短横线")
        private String classCode;

        @NotNull(message = "授课教师不能为空")
        private Long teacherId;
    }

    @Data
    public static class ClassUpdateRequest {
        @NotBlank(message = "班级名称不能为空")
        @Size(max = 64, message = "班级名称最长64字")
        private String className;

        @NotNull(message = "授课教师不能为空")
        private Long teacherId;
    }

    /* ==================== LLM 参数 ==================== */

    public record LlmConfigVO(Long id, String modelName, BigDecimal temperature,
                              Integer timeoutSeconds, Integer maxRetry, LocalDateTime updateTime) {
    }

    @Data
    public static class UpdateLlmConfigRequest {
        @NotBlank(message = "模型名不能为空")
        @Size(max = 64, message = "模型名最长64字")
        private String modelName;

        @NotNull(message = "超时时间不能为空")
        @Min(value = 10, message = "超时至少10秒")
        @Max(value = 300, message = "超时最多300秒")
        private Integer timeoutSeconds;

        @NotNull(message = "重试次数不能为空")
        @Min(value = 0, message = "重试次数不能为负")
        @Max(value = 5, message = "重试次数最多5次")
        private Integer maxRetry;
    }

    /* ==================== Prompt 模板 ==================== */

    public record PromptTemplateVO(Long id, Integer versionNo, Integer enabled, int contentLength,
                                   String contentPreview, LocalDateTime createTime) {
    }

    public record PromptDetailVO(Long id, Integer versionNo, Integer enabled, String content,
                                 LocalDateTime createTime) {
    }

    @Data
    public static class CreatePromptRequest {
        @NotBlank(message = "模板内容不能为空")
        @Size(max = 20000, message = "模板内容最长20000字")
        private String content;
    }

    /* ==================== 黄金数据集 ==================== */

    public record GoldenSampleVO(Long id, String sampleFileKey, BigDecimal teacherTotal,
                                 JsonNode teacherMetrics, Integer active, BigDecimal lastConsistency) {
    }

    @Data
    public static class ToggleActiveRequest {
        @NotNull(message = "启用状态不能为空")
        @Min(value = 0, message = "启用状态取值非法")
        @Max(value = 1, message = "启用状态取值非法")
        private Integer active;
    }

    /* ==================== 审计日志 ==================== */

    public record AuditLogVO(Long id, Long operatorId, String operatorName, String module,
                             String action, String target, String detail, String ip,
                             LocalDateTime createTime) {
    }

    /* ==================== 系统配置（只读运行快照） ==================== */

    public record CountsVO(long users, long classes, long tasks, long submissions, long evaluations) {
    }

    public record SystemConfigVO(CountsVO counts, boolean smtpConfigured, String smtpHost,
                                 long captchaExpireSeconds, int captchaCodeCount,
                                 long emailExpireSeconds, long emailCooldownSeconds,
                                 String llmModel, Integer activePromptVersion, long goldenActiveCount) {
    }
}
