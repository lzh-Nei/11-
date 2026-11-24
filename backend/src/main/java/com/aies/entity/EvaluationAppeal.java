package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生评价申诉：随机改派其他教师复评，处理完成标记 RESOLVED。
 */
@Data
@TableName("evaluation_appeal")
public class EvaluationAppeal {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long evaluationId;

    private Long submissionId;

    private Long studentId;

    private String reason;

    /** PENDING/RESOLVED/REJECTED/CANCELED */
    private String status;

    private Long assignedTeacherId;

    private LocalDateTime createTime;

    private LocalDateTime resolvedTime;

    /** 教师驳回理由（REJECTED 时非空） */
    private String rejectReason;

    /** 处理时间（撤回/驳回/复评完成） */
    private LocalDateTime handledTime;
}
