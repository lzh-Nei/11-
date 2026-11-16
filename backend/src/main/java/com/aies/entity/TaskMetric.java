package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 评价指标（03-数据库设计 §3.3 / §7.1 / V2__org_task.sql）。
 *
 * <p>权重 0&lt;w≤100 由 DB CHECK 兜底；跨行 Σ=100 为应用层事务校验→40001
 * （03-02 §3.3 勘误：不做 DB 跨行约束）。发布后禁止增删改（UC-11，应用层拦截）。</p>
 */
@Data
@TableName("task_metric")
public class TaskMetric {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属任务 */
    private Long taskId;

    /** 指标名（2~64字符；task 内唯一 uk_task_metric） */
    private String metricName;

    /** 指标权重：0<w≤100，单任务 Σ=100 应用层校验→40001 */
    private BigDecimal weight;

    /** 展示序 */
    private Integer sortOrder;
}
