package com.aies.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 评价当前值VO（03-03 §七 E-01；UC-21学生报告页数据源，W7-C2）。
 *
 * <p>metricScores 已解析为指标名→分值映射（DB存JSON串）；traceId 仅教师/管理员
 * 视角返回（E-01响应要点"教师视角另含trace_id"——留痕对象key涉内部路径，学生不可见）。</p>
 */
@Data
public class EvaluationVO {

    private Long id;

    private Long submissionId;

    /** 评价状态机字典：PENDING/AI_DONE/REVIEWED/FAILED */
    private String status;

    /** 当前生效版本号 */
    private Integer currentVersionNo;

    /** 总分（FAILED时null） */
    private BigDecimal totalScore;

    /** 指标分快照（JSON已解析） */
    private Map<String, BigDecimal> metricScores;

    /** AI评语 */
    private String aiComment;

    /** 失败码（FAILED时40003） */
    private Integer errorCode;

    private LocalDateTime updateTime;

    /** 留痕对象key（仅教师/管理员视角） */
    private String traceId;
}
