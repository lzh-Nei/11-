package com.aies.dto;

import com.aies.entity.Submission;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提交视图（S-01 响应：提交记录+版本号）。
 *
 * <p>file_key 为内部存储key不外泄（S-03下载走签名URL，W7接入）。</p>
 */
@Data
public class SubmissionVO {

    private Long id;
    private Long taskId;
    private Long studentId;
    private Integer versionNo;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String parseStatus;
    private LocalDateTime createTime;

    public static SubmissionVO from(Submission s) {
        SubmissionVO vo = new SubmissionVO();
        vo.setId(s.getId());
        vo.setTaskId(s.getTaskId());
        vo.setStudentId(s.getStudentId());
        vo.setVersionNo(s.getVersionNo());
        vo.setFileName(s.getFileName());
        vo.setFileSize(s.getFileSize());
        vo.setFileType(s.getFileType());
        vo.setParseStatus(s.getParseStatus());
        vo.setCreateTime(s.getCreateTime());
        return vo;
    }
}
