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

    /** PENDING/RESOLVED */
    private String status;

    private Long assignedTeacherId;

    private LocalDateTime createTime;

    private LocalDateTime resolvedTime;
}
