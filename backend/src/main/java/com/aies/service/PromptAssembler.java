package com.aies.service;

import com.aies.config.AiProperties;
import com.aies.entity.TaskMetric;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Prompt组装器（04-05 §四；红线R1：业务Prompt字面量不入代码——模板一律
 * prompt_template 表读取，Assembler 仅做占位符替换与文本长度裁剪）。
 *
 * <p>占位符集：{metrics}=指标清单（名称+权重）、{parsed_text}=提取文本。
 * 截断策略：文本超限时保头保尾+截断标记（头70%/尾30%），防7B上下文溢出。</p>
 */
@Service
@RequiredArgsConstructor
public class PromptAssembler {

    /** 提取文本占位符（V1模板两个固定占位） */
    static final String PLACEHOLDER_METRICS = "{metrics}";
    static final String PLACEHOLDER_TEXT = "{parsed_text}";
    /** 截断标记（工具性标记，非业务Prompt语句——规范§四"保头保尾+截断标记"承载点） */
    static final String TRUNCATE_MARK = "\n…[文本过长，中间内容省略]…\n";

    private final AiProperties aiProperties;

    /**
     * 组装完整Prompt。
     *
     * @param templateContent 模板原文（prompt_template.content，enabled=1启用版）
     * @param metrics         任务指标集（TaskService SPI提供，规范§四禁直查task表Mapper）
     * @param parsedText      解析提取文本（parsed/ 落桶原文）
     * @return 组装结果（prompt全文 + 模板版本号落痕）
     */
    public AssembledPrompt assemble(String templateContent, List<TaskMetric> metrics, String parsedText) {
        String metricText = formatMetrics(metrics);
        String text = truncate(parsedText, aiProperties.getPrompt().getTextLimit());
        String prompt = templateContent
                .replace(PLACEHOLDER_METRICS, metricText)
                .replace(PLACEHOLDER_TEXT, text);
        return new AssembledPrompt(prompt);
    }

    /** 指标清单格式化：名称（权重N）、…（权重数据格式化为整数语义，避免"40.00"噪音） */
    private String formatMetrics(List<TaskMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalStateException("任务指标集为空，无法组装评分Prompt");
        }
        StringBuilder sb = new StringBuilder();
        for (TaskMetric metric : metrics) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(metric.getMetricName())
                    .append("（权重").append(strip(metric.getWeight())).append("）");
        }
        return sb.toString();
    }

    /** 保头保尾截断（头70%+尾30%，中间截断标记） */
    private String truncate(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text == null ? "" : text;
        }
        int head = (int) (limit * 0.7);
        int tail = limit - head;
        return text.substring(0, head) + TRUNCATE_MARK + text.substring(text.length() - tail);
    }

    private String strip(BigDecimal weight) {
        return weight == null ? "?" : weight.stripTrailingZeros().toPlainString();
    }

    /**
     * 组装产物：prompt 全文。模板版本号由调用方从 {@code PromptTemplate.versionNo}
     * 落痕（规范§四：随 trace_id 写入留痕），不混入Prompt文本。
     */
    public record AssembledPrompt(String text) {
    }
}
