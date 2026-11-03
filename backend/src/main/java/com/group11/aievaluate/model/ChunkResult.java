package com.group11.aievaluate.model;

/**
 * 单个分片的接收结果（ok=false 表示该分片需要重传）。
 */
public record ChunkResult(boolean ok, int chunkIndex, String md5, String message) {
}
