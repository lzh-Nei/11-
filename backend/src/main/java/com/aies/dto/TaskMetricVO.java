package com.aies.dto;

import com.aies.entity.TaskMetric;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 指标视图（只读；UC-13 学生视角指标集同形展示）。
 */
@Data
public class TaskMetricVO {

    private Long id;
    private String metricName;
    private BigDecimal weight;
    private Integer sortOrder;

    public static TaskMetricVO from(TaskMetric metric) {
        TaskMetricVO vo = new TaskMetricVO();
        vo.setId(metric.getId());
        vo.setMetricName(metric.getMetricName());
        vo.setWeight(metric.getWeight());
        vo.setSortOrder(metric.getSortOrder());
        return vo;
    }
}
