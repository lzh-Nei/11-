package com.aies.common;

import java.util.Set;

/**
 * 权限点常量（S-06）：与 02-04 RTM、sys_permission 种子（V1迁移）一一对应，共22个。
 *
 * <p>角色-权限映射勾稽：ADMIN 22 + TEACHER 14 + STUDENT 7 = 43 行
 * （D-16：auth:logout 回归基线分配全部三角色；sys_role_permission 实测口径）。
 * 写接口一律 {@code @PreAuthorize("hasAuthority(...)")} 引用本类常量，禁止裸字符串。</p>
 */
public final class PermissionConstants {

    private PermissionConstants() {
    }

    /* ===== auth（1） ===== */
    public static final String AUTH_LOGOUT = "auth:logout";

    /* ===== user（3） ===== */
    public static final String USER_ME = "user:me";
    public static final String USER_MANAGE = "user:manage";

    /* ===== task（4） ===== */
    public static final String TASK_CREATE = "task:create";
    public static final String TASK_PUBLISH = "task:publish";
    public static final String TASK_EDIT = "task:edit";
    public static final String TASK_VIEW = "task:view";

    /* ===== submission（2） ===== */
    public static final String SUBMISSION_UPLOAD = "submission:upload";
    public static final String SUBMISSION_VIEW = "submission:view";

    /* ===== evaluation（4） ===== */
    public static final String EVALUATION_VIEW = "evaluation:view";
    public static final String EVALUATION_REVIEW = "evaluation:review";
    public static final String EVALUATION_RE_EVALUATE = "evaluation:re-evaluate";
    public static final String EVALUATION_HISTORY_VIEW = "evaluation:history:view";

    /* ===== report（2） ===== */
    public static final String REPORT_VIEW = "report:view";
    public static final String REPORT_EXPORT = "report:export";

    /* ===== system（6） ===== */
    public static final String ROLE_MANAGE = "role:manage";
    public static final String SYSTEM_CONFIG = "system:config";
    public static final String CLASS_MANAGE = "class:manage";
    public static final String AUDIT_VIEW = "audit:view";
    public static final String LLM_CONFIG = "llm:config";
    public static final String PROMPT_MANAGE = "prompt:manage";
    public static final String GOLDEN_MANAGE = "golden:manage";

    /** TEACHER 角色权限集（14个，D-16后与 sys_role_permission 种子勾稽一致） */
    public static final Set<String> TEACHER_PERMS = Set.of(
            AUTH_LOGOUT, USER_ME, TASK_CREATE, TASK_PUBLISH, TASK_EDIT, TASK_VIEW,
            SUBMISSION_VIEW, EVALUATION_VIEW, EVALUATION_REVIEW, EVALUATION_RE_EVALUATE,
            EVALUATION_HISTORY_VIEW, REPORT_VIEW, REPORT_EXPORT, CLASS_MANAGE);

    /** STUDENT 角色权限集（7个，D-16后与 sys_role_permission 种子勾稽一致） */
    public static final Set<String> STUDENT_PERMS = Set.of(
            AUTH_LOGOUT, USER_ME, TASK_VIEW, SUBMISSION_UPLOAD,
            SUBMISSION_VIEW, EVALUATION_VIEW, REPORT_VIEW);
}
