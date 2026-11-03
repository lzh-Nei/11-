package com.group11.aievaluate.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 已入库文件的元数据（同时用作服务端持久化 JSON 与 API 响应）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileMeta(String id, String name, long size, String md5, String uploadTime) {
}
