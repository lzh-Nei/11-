package com.aies.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 教师复核/人工评分请求体（03-03 §七 E-02/E-06：totalScore + metricScores +
 * reviewComment(≥10字)；理由空/不足10字→30007应用层校验）。
 *
 * <p>指标分结构校验（键集=任务指标集/权重上限/Σ=总分±0.5）在应用层对齐
 * SchemaValidator纪律执行，违规→30000。</p>
 */
@Data
public class ReviewEvaluationRequest {

    /** 复核后总分（0~100，应用层校验） */
    @NotNull(message = "总分不能为空")
    private BigDecimal totalScore;

    /** 指标名→分值（键集必须与任务指标集一致） */
    @NotEmpty(message = "指标分不能为空")
    private Map<String, BigDecimal> metricScores;

    /** 复核理由（trim后≥10字，否则30007） */
    private String reviewComment;
}
