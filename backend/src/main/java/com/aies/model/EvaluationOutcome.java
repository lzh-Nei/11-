package com.aies.model;

import java.math.BigDecimal;

/**
 * 评价管道产物（LLM重试循环结束后的最终结果，供落库/留痕/黄金回归比对消费）。
 *
 * @param success         true=Schema校验通过（→AI_DONE）；false=重试耗尽（→FAILED 40003）
 * @param totalScore      AI总分（success=false为null）
 * @param metricScoresJson 指标分JSON快照（落 evaluation_result.metric_scores / history）
 * @param aiComment       AI评语
 * @param retryCount      实际重试次数（0=首试通过；监控口径：均值>1.5持续3日告警）
 * @param elapsedMs       累计调用耗时（毫秒）
 * @param modelVersion    模型标识（llm_config.model_name 实测返回值）
 * @param failReason      末次失败原因（校验失败文案或调用异常摘要；success=false时必填）
 * @param rawResponse     末次LLM原始输出（留痕raw_response；模板缺失等未发起调用时为null）
 */
public record EvaluationOutcome(boolean success, BigDecimal totalScore, String metricScoresJson,
                                String aiComment, int retryCount, long elapsedMs,
                                String modelVersion, String failReason, String rawResponse) {

    /** 失败结果快捷构造（未发起LLM调用：模板缺失/配置缺失等前置失败） */
    public static EvaluationOutcome failed(String failReason) {
        return new EvaluationOutcome(false, null, null, null, 0, 0, null, failReason, null);
    }
}
