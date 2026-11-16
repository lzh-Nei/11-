package com.aies.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提交详情视图（S-03 / UC-18：签名URL(5分钟) + 提取文本预览 + 评价入口状态）。
 *
 * <p>签名URL由 {@code FileStorage.presignedGetUrl} 生成（minio 模式，S-04 同源）；
 * 提取文本预览截断 2000 字符（全量在 parsed_text_key 对象中，DB 不入文本）。</p>
 */
@Data
public class SubmissionDetailVO {

    private Long id;
    private Long taskId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private Integer versionNo;
    private String parseStatus;
    private String evaluationStatus;
    /** 评价记录id（UC-21报告页跳转锚点；无评价记录null——W7-C2加法细化） */
    private Long evaluationId;
    private LocalDateTime createTime;

    /** 原件下载签名URL（5分钟有效，S-03/S-04 契约） */
    private String downloadUrl;

    /** 提取文本预览（前2000字符；FAILED/PENDING 时为 null） */
    private String textPreview;

    /** 提取文本总字符数（前端判断是否截断展示） */
    private Integer textChars;
}
