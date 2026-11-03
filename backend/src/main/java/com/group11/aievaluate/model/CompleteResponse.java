package com.group11.aievaluate.model;

import java.util.List;

/**
 * 合并结果。
 * success=false 且 badChunks 非空：服务端校验发现损坏/缺失分片，客户端只需重传这些分片。
 * success=false 且 badChunks 为空：其他错误，message 说明原因。
 */
public record CompleteResponse(
        boolean success,
        String fileId,
        String name,
        long size,
        String md5,
        List<Integer> badChunks,
        String message) {

    public static CompleteResponse ok(FileMeta meta, String message) {
        return new CompleteResponse(true, meta.id(), meta.name(), meta.size(), meta.md5(), List.of(), message);
    }

    public static CompleteResponse fail(List<Integer> badChunks, String message) {
        return new CompleteResponse(false, null, null, 0, null, badChunks, message);
    }
}
