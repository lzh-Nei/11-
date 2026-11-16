package com.aies.model;

/**
 * 文件中心列表项：在文件元数据之上补上传者姓名与班级，供管理端/教师端展示。
 */
public record FileMetaVO(String id, String name, long size, String uploadTime,
                         Long uploaderId, String uploaderName, String className) {
}
