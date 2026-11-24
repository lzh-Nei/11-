package com.aies.service;

import com.aies.model.ReportOverviewDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 报表四图 CSV 格式化（同步下载与异步导出共用，UTF-8 BOM 由调用方决定）。
 */
@Service
public class ReportCsvFormatter {

    /** 四图数据摊平成多段 CSV（段间空行分隔，首行带列名） */
    public String toCsv(ReportOverviewDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务统计\n任务,提交数,已出分,平均分,提交率\n");
        for (ReportOverviewDTO.TaskStat t : dto.tasks()) {
            sb.append(cell(t.title())).append(',')
                    .append(t.submitted()).append(',')
                    .append(t.scored()).append(',')
                    .append(num(t.avgScore())).append(',')
                    .append(num(t.rate())).append('\n');
        }
        sb.append("\n成绩分布\n区间,数量\n");
        dto.scoreDist().forEach(r -> sb.append(cell(r.range())).append(',')
                .append(r.count()).append('\n'));
        sb.append("\n指标雷达\n指标,平均分\n");
        dto.metricRadar().forEach(r ->
                sb.append(cell(r.metricName())).append(',').append(num(r.avgScore())).append('\n'));
        sb.append("\n分差分布\n区间,数量\n");
        dto.scoreGapDist().forEach(r -> sb.append(cell(r.range())).append(',')
                .append(r.count()).append('\n'));
        return sb.toString();
    }

    private static String num(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    /** CSV 单元格转义：含逗号/引号/换行时加引号并把内部引号翻倍 */
    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
