package com.aies.model;

/**
 * 解析消息体（UC-16 基本流第5步：上传PENDING→发RabbitMQ→worker消费置QUEUED→SUCCESS/FAILED）。
 *
 * <p>仅携带 submissionId：文件内容不进消息（S-07 落桶已就绪，worker 按 file_key 取流）。</p>
 *
 * <p><b>消息契约：</b>幂等键为 submissionId；消费端以 Redis 单飞锁 + 解析状态幂等。
 * 瞬时故障最多重试 3 次（1s/2s/4s 退避），耗尽或不可重试异常进入解析死信队列。</p>
 */
public record ParseMessage(long submissionId) {
}
