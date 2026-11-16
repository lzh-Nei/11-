package com.aies.model;

/**
 * 评价消息体（UC-20：MQ系统内部链路，03-03 §七注记——无REST触发接口）。
 *
 * <p>解析链（ParseWorker SUCCESS）提交后投递本消息，EvaluationWorker 消费。
 * 仅携带 submissionId：提取文本经 parsed_text_key 落桶取流（与 ParseMessage 同口径，
 * 文本内容不进消息）。</p>
 */
public record EvalMessage(long submissionId) {
}
