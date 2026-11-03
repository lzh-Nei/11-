package com.group11.aievaluate.model;

/**
 * 批量上传中单个文件的结果（互不影响，逐个返回成败）。
 */
public record BatchItemResult(String fileName, boolean ok, String fileId, String message) {
}
