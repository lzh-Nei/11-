package com.aies.model;

/**
 * 评价消息体（UC-20：MQ系统内部链路，03-03 §七注记——无REST触发接口）。
 *
 * <p>解析链（ParseWorker SUCCESS）提交后投递本消息，EvaluationWorker 消费。
 * 仅携带 submissionId：提取文本经 parsed_text_key 落桶取流（与 ParseMessage 同口径，
 * 文本内容不进消息）。</p>
 *
 * <p><b>消息契约：</b>幂等键为 submissionId；消费端以 Redis 单飞锁 + 评价状态幂等，
 * 避免重复 LLM 计费。瞬时故障最多重试 2 次，耗尽后进入评价死信队列供人工重放。</p>
 */
public record EvalMessage(long submissionId) {
}
