package com.aies.dto;

import java.time.LocalDateTime;

/**
 * 学生评价申诉视图（学生本人 / 被指派教师 / 管理员可见）。
 */
public record AppealVO(Long id, Long evaluationId, Long submissionId, String taskTitle,
                       String studentName, String reason, String status, Long assignedTeacherId,
                       String assignedTeacherName, String rejectReason,
                       LocalDateTime createTime, LocalDateTime resolvedTime) {
}
