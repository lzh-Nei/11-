package com.group11.aievaluate.model;

import java.util.List;

/**
 * 初始化分片上传的响应。
 * uploadedChunks 是服务端已落盘的分片序号 —— 断点续传的核心：
 * 客户端刷新页面 / 暂停后恢复时，根据它跳过已传分片，只传缺的部分。
 */
public record InitResponse(
        String uploadId,
        String fileName,
        long fileSize,
        int totalChunks,
        List<Integer> uploadedChunks) {
}
