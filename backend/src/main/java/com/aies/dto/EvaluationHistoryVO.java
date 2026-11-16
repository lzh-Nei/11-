package com.aies.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 评价历史版本VO（03-03 §七 E-04列表项 / E-05单版本快照；UC-25）。
 *
 * <p>E-04列表场景仅填充 versionNo/source/totalScore/reviewerName/createTime；
 * E-05快照额外填充 metricScores/aiComment/teacherComment/traceId。
 * traceId仅教师/管理员视角返回（学生裁剪，UC-25备选流A2）。</p>
 */
@Data
public class EvaluationHistoryVO {

    /** 版本号：1=AI初评，2+=复核/重评 */
    private Integer versionNo;

    /** 字典：AI_INITIAL/TEACHER_REVIEW/RE_EVALUATE */
    private String source;

    /** 总分快照 */
    private BigDecimal totalScore;

    /** 操作人姓名（AI版本为null；TEACHER_REVIEW/RE_EVALUATE为教师real_name） */
    private String reviewerName;

    /** AI 版本模型标识（教师版本为空） */
    private String aiModel;

    /** 版本生成时间 */
    private LocalDateTime createTime;

    /* ===== E-05 快照扩展字段 ===== */

    /** 指标分快照（JSON已解析） */
    private Map<String, BigDecimal> metricScores;

    /** AI评语快照 */
    private String aiComment;

    /** 复核理由（TEACHER_REVIEW版本非空） */
    private String teacherComment;

    /** 留痕对象key（仅教师/管理员视角；AI版本关联冷桶，教师可跳留痕回放） */
    private String traceId;
}
