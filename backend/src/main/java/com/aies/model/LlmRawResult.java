package com.aies.model;

/**
 * LLM原始调用结果（04-05 §二 LlmClient契约字段）。
 *
 * <p>rawResponse 为模型输出原文（可能含markdown包裹/截断/非法JSON——
 * 健壮性由 SchemaValidator 处置，LlmClient 不做内容解析）。</p>
 *
 * @param rawResponse  模型输出原文
 * @param elapsedMs    本次调用耗时（毫秒，留痕+监控口径P50/P90）
 * @param modelVersion 模型标识（Ollama返回的model字段，如 qwen2.5:7b）
 */
public record LlmRawResult(String rawResponse, long elapsedMs, String modelVersion) {
}
