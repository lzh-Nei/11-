package com.group11.aievaluate.model;

/**
 * 分片上传会话信息（持久化为 upload.json），合并时需要总分片数等信息。
 */
public record UploadSession(String fileName, long fileSize, int totalChunks, long chunkSize) {
}
