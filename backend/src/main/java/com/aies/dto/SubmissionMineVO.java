package com.aies.dto;

import com.aies.entity.Submission;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的提交列表视图（S-02 / UC-17：仅本人，按任务分组，含评价状态）。
 *
 * <p>评价状态取 evaluation_result.status；无评价记录→PENDING（UC-17 字典初始态）。
 * file_key 不外泄（S-07，下载走 S-03 签名URL）。
 * evaluationId 为 W7-C2 加法契约细化（D-21径）：UC-21 评价报告页入口
 * （GET /api/evaluations/{id}）的行级跳转锚点，无评价记录为null。</p>
 */
@Data
public class SubmissionMineVO {

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

    public static SubmissionMineVO from(Submission s, Long evaluationId, String evaluationStatus) {
        SubmissionMineVO vo = new SubmissionMineVO();
        vo.setId(s.getId());
        vo.setTaskId(s.getTaskId());
        vo.setFileName(s.getFileName());
        vo.setFileSize(s.getFileSize());
        vo.setFileType(s.getFileType());
        vo.setVersionNo(s.getVersionNo());
        vo.setParseStatus(s.getParseStatus());
        vo.setEvaluationStatus(evaluationStatus == null ? "PENDING" : evaluationStatus);
        vo.setEvaluationId(evaluationId);
        vo.setCreateTime(s.getCreateTime());
        return vo;
    }
}
