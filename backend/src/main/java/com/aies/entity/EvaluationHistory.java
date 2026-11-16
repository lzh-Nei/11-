package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评价历史版本（V4 §3.6，ADR-004只增不改不删——审计证据链）。
 *
 * <p>uk_eval_version(evaluation_id, version_no) 保障版本不重号；
 * 只读红线：无更新/删除接口（写请求→40301）。</p>
 */
@Data
@TableName("evaluation_history")
public class EvaluationHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** =evaluation_result.id */
    private Long evaluationId;

    /** 提交id（冗余，提交维度可查） */
    private Long submissionId;

    /** 版本号：1=AI初评，2+=复核/重评 */
    private Integer versionNo;

    /** 字典：AI_INITIAL/TEACHER_REVIEW/RE_EVALUATE */
    private String source;

    /** 总分快照 */
    private BigDecimal totalScore;

    /** 各维度得分快照（JSON） */
    private String metricScores;

    /** AI评语快照 */
    private String aiComment;

    /** 复核理由（TEACHER_REVIEW时必填≥10字→应用层30007） */
    private String teacherComment;

    /** 教师（AI版本为NULL） */
    private Long reviewerId;

    /** AI版本关联留痕对象key（trace/{traceId}.json） */
    private String traceId;

    private LocalDateTime createTime;
}
