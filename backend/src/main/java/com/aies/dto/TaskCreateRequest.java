package com.aies.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * T-01 创建任务请求（UC-10：标题/说明/截止时间/目标班级/指标集）。
 *
 * <p>截止时间不使用 {@code @Future}：契约要求 deadline 非法→30006（专用码），
 * 而 Bean Validation 失败统一→30000，故放 {@link com.aies.service.TaskService} 应用层校验。
 * 权重单值 0&lt;w≤100 双端校验在此拦截；Σ=100 在 Service 事务内校验→40001（UC-10 E1）。</p>
 */
@Data
public class TaskCreateRequest {

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 128, message = "任务标题不能超过128字符")
    private String title;

    @Size(max = 10000, message = "任务描述不能超过10000字符")
    private String description;

    /** 目标班级（task.class_id NOT NULL；须为本人绑定班级，Service内归属校验） */
    @NotNull(message = "目标班级不能为空")
    private Long classId;

    @NotNull(message = "截止时间不能为空")
    private LocalDateTime deadline;

    @Valid
    @NotNull(message = "评价指标集不能为空")
    @Size(min = 2, message = "评价指标至少2项")
    private List<MetricItem> metrics;

    /**
     * 评价指标项（UC-10：权重 0&lt;w≤100 双端校验；Σ=100 由 Service 校验）。
     */
    @Data
    public static class MetricItem {

        @NotBlank(message = "指标名不能为空")
        @Size(min = 2, max = 64, message = "指标名长度须在2~64字符之间")
        private String metricName;

        @NotNull(message = "指标权重不能为空")
        @DecimalMin(value = "0.01", message = "指标权重必须大于0")
        @DecimalMax(value = "100", message = "指标权重不能超过100")
        private BigDecimal weight;

        @Min(value = 0, message = "展示序不能为负")
        @Max(value = 999, message = "展示序过大")
        private Integer sortOrder = 0;
    }
}
