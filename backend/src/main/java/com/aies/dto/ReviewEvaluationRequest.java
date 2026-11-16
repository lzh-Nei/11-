package com.aies.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 教师复核/人工评分请求体（E-02/E-06）：metricScores + reviewComment（≥10字）。
 *
 * <p>总分不再由客户端决定：后端以各指标分之和计算并回写；Σ>100 直接拒绝。
 * 保留 totalScore 字段仅为旧客户端兼容，服务端忽略。</p>
 */
@Data
public class ReviewEvaluationRequest {

    /** 兼容字段：服务端忽略，以 metricScores 之和为准 */
    private BigDecimal totalScore;

    /** 指标名→分值（键集必须与任务指标集一致） */
    @NotEmpty(message = "指标分不能为空")
    private Map<String, BigDecimal> metricScores;

    /** 复核理由（trim后≥10字，否则30007） */
    private String reviewComment;
}
