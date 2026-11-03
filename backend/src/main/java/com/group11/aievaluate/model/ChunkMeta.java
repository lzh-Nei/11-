package com.group11.aievaluate.model;

/**
 * 服务端为每个分片落盘保存的校验信息（持久化为 {index}.meta.json）。
 * 合并前重新计算分片 MD5 与之比对 → 检测传输后/存储期间发生的损坏。
 */
public record ChunkMeta(String md5, long size) {
}
