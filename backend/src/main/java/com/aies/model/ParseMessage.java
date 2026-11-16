package com.aies.model;

/**
 * 解析消息体（UC-16 基本流第5步：上传PENDING→发RabbitMQ→worker消费置QUEUED→SUCCESS/FAILED）。
 *
 * <p>仅携带 submissionId：文件内容不进消息（S-07 落桶已就绪，worker 按 file_key 取流）。</p>
 */
public record ParseMessage(long submissionId) {
}
