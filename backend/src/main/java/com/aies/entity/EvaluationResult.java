package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评价当前值（每提交唯一一行，V4__ai_report_audit.sql §3.5，ADR-004）。
 *
 * <p>W7-C1 仅消费 status 字段（UC-17 列表评价状态联查）；
 * 评分写入/复核流转属 W8 AI 评价域。</p>
 */
@Data
@TableName("evaluation_result")
public class EvaluationResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提交id（每提交唯一行，1:1） */
    private Long submissionId;

    /** 评价状态机字典：PENDING/AI_DONE/REVIEWED/FAILED（§7.1） */
    private String status;

    /** 当前生效版本号（报表统计仅读此表） */
    private Integer currentVersionNo;

    /** 总分（FAILED时为NULL） */
    private BigDecimal totalScore;

    /** 各指标得分快照（JSON） */
    private String metricScores;

    /** AI评语 */
    private String aiComment;

    /** 最近复核教师 */
    private Long reviewerId;

    /** 最近复核时间 */
    private LocalDateTime reviewTime;

    /** 失败码（FAILED时记录，如40003） */
    private Integer errorCode;

    /** 关联冷bucket留痕（AI版本） */
    private String traceId;

    /** 乐观锁@Version字段（§3.5；MybatisPlusConfig OptimisticLockerInnerInterceptor承载） */
    @Version
    private Integer version;

    private LocalDateTime updateTime;
}
