package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志（V4 §7 audit_log，仅追加——S-09不可篡改：应用仅INSERT+SELECT，
 * 无更新/删除方法调用；生产由DBA执行REVOKE UPDATE/DELETE）。
 *
 * <p>W8-M1首用：UC-23重评触发审计（module=evaluation，action=RE_EVALUATE）。</p>
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人 */
    private Long operatorId;

    /** 模块字典：auth/user/task/submission/evaluation/report/system */
    private String module;

    /** 动作（如LOGIN/REVIEW/RE_EVALUATE） */
    private String action;

    /** 操作对象标识（如evaluation:{id}） */
    private String target;

    /** 明细快照（JSON串，脱敏后） */
    private String detail;

    /** 客户端IP（兼容IPv6） */
    private String ip;

    private LocalDateTime createTime;
}
