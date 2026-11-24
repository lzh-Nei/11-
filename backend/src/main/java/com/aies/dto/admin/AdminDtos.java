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
import java.util.Map;

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

    /** 删除用户前的关联记录快照（强制删除需管理员二次确认） */
    public record UserDeletionImpactVO(Long userId, String username, String realName,
                                       List<String> roleCodes, boolean protectedAccount,
                                       String protectedReason, long ownedClasses, long ownedTasks,
                                       long ownedSubmissions, long ownedEvaluationResults,
                                       long ownedEvaluationHistories, long appeals, long auditLogs,
                                       long reportExports, long reviewReferences,
                                       long unboundStudents, long totalRecords) {
    }

    /** 管理端评价列表（管理员查看教师评价与复核历史入口） */
    public record AdminEvaluationVO(Long id, Long submissionId, Long taskId, String taskTitle,
                                    String studentName, String teacherName, String status,
                                    BigDecimal totalScore, Integer currentVersionNo,
                                    LocalDateTime updateTime) {
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

    /** 管理员重置密码：newPassword 留空则生成临时密码（响应仅在生成时回传一次） */
    @Data
    public static class ResetUserPasswordRequest {
        @Size(max = 32, message = "密码长度不能超过32位")
        private String newPassword;
    }

    public record ResetPasswordResult(String username, String temporaryPassword, boolean generated) {
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

    /** 管理员编辑用户全部资料：基础资料 + 账号/状态/角色 + 可选重置密码 */
    @Data
    public static class UpdateUserRequest {
        /** 学号/工号；留空表示不修改 */
        @Pattern(regexp = "^$|^[A-Za-z0-9_]{4,20}$",
                message = "学号/工号需为4-20位字母、数字或下划线")
        private String username;

        @NotBlank(message = "姓名不能为空")
        @Size(max = 32, message = "姓名最长32字")
        private String realName;

        @Pattern(regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "邮箱格式不正确")
        private String email;

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;

        private Long classId;

        /** 1=正常，0=禁用；留空表示不修改 */
        @Min(value = 0, message = "状态取值非法")
        @Max(value = 1, message = "状态取值非法")
        private Integer status;

        /** 角色编码集合；留空表示不修改 */
        private List<@NotBlank(message = "角色编码不能为空") String> roleCodes;

        /** 新密码；留空表示不修改，填写后该账号首次登录需改密 */
        @Size(max = 32, message = "密码长度不能超过32位")
        private String newPassword;
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

    public record LlmConfigVO(Long id, String provider, String apiBaseUrl, String modelName,
                              BigDecimal temperature, Integer timeoutSeconds, Integer maxRetry,
                              boolean apiKeyConfigured, String apiKeyHint,
                              LocalDateTime updateTime) {
    }

    public record LlmTestResult(boolean success, String message, String modelVersion,
                                long elapsedMs) {
    }

    @Data
    public static class UpdateLlmConfigRequest {
        @NotBlank(message = "模型供应商不能为空")
        private String provider;

        @Size(max = 255, message = "Base URL 最长255字")
        private String apiBaseUrl;

        /** 新 API Key；留空表示保持已配置密钥 */
        @Size(max = 512, message = "API Key 过长")
        private String apiKey;

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
                                 String llmModel, Integer activePromptVersion, long goldenActiveCount,
                                 Map<String, String> editable) {
    }

    /** 系统参数更新：仅白名单键，值为字符串（服务端按类型/范围校验） */
    @Data
    public static class SystemConfigUpdateRequest {
        @NotEmpty(message = "没有可更新的配置项")
        private Map<String, String> values;
    }

    /* ==================== 消息队列监控（system:config） ==================== */

    /** 单个队列快照：available=false 时 error 说明原因（例如 broker 未连上） */
    public record MqQueueVO(String key, String label, String queueName, String exchange,
                            String routingKey, boolean available, Integer readyMessages,
                            Integer consumers, String error) {
    }

    /** 解析/评价流水线计数（按数据库状态汇总，不读取消息体） */
    public record MqStatsVO(long parseQueued, long parseSuccess, long parseFailed,
                            long evaluationPending, long evaluationAiDone,
                            long evaluationReviewed, long evaluationFailed) {
    }

    /** 最近一次异步处理事件（提交 → 解析 → 评价的合并视图） */
    public record MqEventVO(Long submissionId, String fileName, String studentName, String taskTitle,
                            String parseStatus, String evaluationStatus, BigDecimal totalScore,
                            String aiModel, LocalDateTime updateTime) {
    }

    /** 队列告警：level=OK/WARN/ERROR，messages 与后端巡检阈值保持一致 */
    public record MqAlertVO(boolean alert, String level, List<String> messages) {
    }

    /** 管理端 MQ 监控快照：连接状态 + 队列水位 + 告警 + 数据库侧流水 + 最近事件 */
    public record MqMonitorVO(boolean rabbitConnected, String broker, LocalDateTime sampledAt,
                              List<MqQueueVO> queues, MqAlertVO alert,
                              MqStatsVO stats, List<MqEventVO> recentEvents) {
    }

    /* ==================== 日志采集与审计窗口（仅管理员） ==================== */

    /** 审计检索条件：模块/动作/关键字/操作人 + 时间窗 */
    public record AuditLogFilter(String module, String action, String keyword, String operator,
                                 LocalDateTime start, LocalDateTime end) {
    }

    /** 运行日志条目（采集器内存快照，detail 为异常堆栈） */
    public record RuntimeLogVO(long seq, LocalDateTime time, String level, String logger,
                               String thread, String message, String detail) {
    }

    /** 运行日志采集指标 */
    public record RuntimeLogStatsVO(int capacity, int captured, long total, long errorCount,
                                    long warnCount, LocalDateTime startedAt, boolean capturing) {
    }

    /** 运行日志分页结果（附带采集指标，便于窗口顶部展示） */
    public record RuntimeLogPageVO(RuntimeLogStatsVO stats, List<RuntimeLogVO> records,
                                   long total, long page, long size) {
    }

    /** 审计统计分组（模块/动作维度） */
    public record AuditBucketVO(String label, long count) {
    }

    /** 审计量日趋势（近7日） */
    public record AuditTrendVO(String day, long count) {
    }

    /** 审计窗口概览：总量/今日/近24h + 模块/动作分布 + 日趋势 */
    public record AuditStatsVO(long total, long today, long last24h, List<AuditBucketVO> byModule,
                               List<AuditBucketVO> byAction, List<AuditTrendVO> trend) {
    }
}
