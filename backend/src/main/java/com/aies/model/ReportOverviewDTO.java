package com.aies.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报表四图聚合 DTO（C1 v1.0 §二；数据口径 LLD §五：JOIN result 当前值、
 * FAILED 计入提交率分母不计入均分）。
 */
public record ReportOverviewDTO(
        List<TaskStat> tasks,
        List<RangeCount> scoreDist,
        List<RadarPoint> metricRadar,
        List<RangeCount> scoreGapDist) {

    /** 空报表（无任务/无提交时的四图空态） */
    public static ReportOverviewDTO empty() {
        return new ReportOverviewDTO(List.of(),
                List.of(new RangeCount("0-59", 0), new RangeCount("60-69", 0),
                        new RangeCount("70-79", 0), new RangeCount("80-89", 0),
                        new RangeCount("90-100", 0)),
                List.of(),
                List.of(new RangeCount("≤5", 0), new RangeCount("6-10", 0),
                        new RangeCount("11-20", 0), new RangeCount(">20", 0),
                        new RangeCount("无复核", 0)));
    }

    /** 任务级统计：submitted=提交记录数/scored=已出分/avgScore=已出分均分/rate=去重提交学生数占班级应交学生数百分比（无应交=0） */
    public record TaskStat(Long taskId, String title, int submitted, int scored,
                           BigDecimal avgScore, BigDecimal rate) {
    }

    /** 分布段：scoreDist=均分分布段；scoreGapDist=教师-AI 分差段 */
    public record RangeCount(String range, long count) {
    }

    /** 指标雷达点：任务指标集逐指标均分 */
    public record RadarPoint(String metricName, BigDecimal avgScore) {
    }
}
