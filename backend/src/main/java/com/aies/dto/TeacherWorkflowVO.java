package com.aies.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 教师评价工作流行：提交 + 当前评价 + 最近评价方 + 申诉改派信息。
 */
@Data
public class TeacherWorkflowVO {

    private Long submissionId;
    private Long taskId;
    private String taskTitle;
    private Long classId;
    private String className;
    private Long studentId;
    private String studentName;
    private String studentUsername;
    private String fileName;
    private Integer versionNo;
    private String parseStatus;
    private LocalDateTime submitTime;

    private Long evaluationId;
    private String evaluationStatus;
    private BigDecimal totalScore;
    private Integer currentVersionNo;
    private String aiModel;
    private String lastEvaluator;
    private LocalDateTime evaluationUpdateTime;
    private boolean canTriggerAi;

    private Long appealId;
    private String appealStatus;
    private Long assignedTeacherId;
    private String assignedTeacherName;
    private String appealReason;
}
