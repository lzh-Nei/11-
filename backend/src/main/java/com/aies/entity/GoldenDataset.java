package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 黄金数据集样本（V4__ai_report_audit.sql §3.7，NFR-03 一致性回归载体，UC-39）。
 *
 * <p>W7-C1 仅消费 sample_file_key（解析回放）；teacher_total/metrics 的
 * AI 一致性回归属 W8 评价域。</p>
 */
@Data
@TableName("golden_dataset")
public class GoldenDataset {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样本文件MinIO热bucket对象key（golden/前缀） */
    private String sampleFileKey;

    /** 教师标注总分 */
    private BigDecimal teacherTotal;

    /** 教师标注各指标分（JSON） */
    private String teacherMetrics;

    /** 1=参与回归；0=停用不参与 */
    private Integer active;

    /** 最近回归一致率（单样本|AI-教师|≤总分×5%判一致；总体≥70%，NFR-03） */
    private BigDecimal lastConsistency;
}
