package com.aies.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Schema校验结果（04-05 §五：纯函数式校验器入rawResponse→出ValidationResult，便于参数化单测）。
 *
 * @param valid        是否通过全部校验（JSON合法/总分0~100/指标齐备且Σ=总分±0.5/评语非空≤2000字/指标分≤权重上限）
 * @param reason       失败原因（valid=true时为null；驱动重试计数与留痕schema_valid）
 * @param totalScore   总分（valid=true时非空）
 * @param metricScores 指标分（valid=true时齐备）
 * @param aiComment    评语（valid=true时非空）
 */
public record ValidationResult(boolean valid, String reason, BigDecimal totalScore,
                               Map<String, BigDecimal> metricScores, String aiComment) {

    /** 合法结果快捷构造 */
    public static ValidationResult ok(BigDecimal totalScore, Map<String, BigDecimal> metricScores,
                                      String aiComment) {
        return new ValidationResult(true, null, totalScore, metricScores, aiComment);
    }

    /** 失败结果快捷构造 */
    public static ValidationResult fail(String reason) {
        return new ValidationResult(false, reason, null, null, null);
    }
}
