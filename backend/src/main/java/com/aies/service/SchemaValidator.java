package com.aies.service;

import com.aies.model.ValidationResult;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Schema校验器（04-05 §五：纯函数式校验器，入rawResponse→出ValidationResult，便于单测参数化）。
 *
 * <p>校验链：①剥markdown代码块/提取平衡JSON对象（LLM输出健壮性最大坑的承载点——
 * 截断/包裹/前后缀噪音在此归一）②Jackson解析 ③总分0~100 ④指标齐备且
 * 各分不超权重上限 ⑤Σ指标分=总分±0.5 ⑥评语非空≤2000字。
 * 任一失败→invalid（驱动重试），禁止静默通过（红线R5）。</p>
 */
@Service
public class SchemaValidator {

    /** Σ指标分与总分的容差（规范§五：±0.5） */
    static final BigDecimal SUM_TOLERANCE = new BigDecimal("0.5");
    /** 评语长度上限（规范§五：≤2000字） */
    static final int COMMENT_MAX_LEN = 2000;

    private final ObjectMapper objectMapper;
    /** LLM宽松解析器（W7-C2实弹根因#2：qwen2.5:7b在JSON字符串值内输出裸换行符
     * （CTRL-CHAR 10，严格JSON非法但LLM输出高发共性）——ALLOW_UNESCAPED_CONTROL_CHARS
     * 是LLM JSON解析的业界标准处置；其余校验纪律不变，仍禁静默通过） */
    private final ObjectMapper lenientMapper;

    public SchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        JsonFactory lenientFactory = JsonFactory.builder()
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                .build();
        this.lenientMapper = new ObjectMapper(lenientFactory);
    }

    public ValidationResult validate(String rawResponse, Map<String, BigDecimal> weightCaps) {
        String json = extractJson(rawResponse);
        if (json == null) {
            return ValidationResult.fail("未找到JSON对象（可能被markdown包裹或输出截断）");
        }
        JsonNode root;
        try {
            root = lenientMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return ValidationResult.fail("JSON解析失败: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            return ValidationResult.fail("根节点不是JSON对象");
        }

        // 总分：存在/数值/0~100
        JsonNode totalNode = root.get("total_score");
        if (totalNode == null || !totalNode.isNumber()) {
            return ValidationResult.fail("total_score缺失或非数值");
        }
        BigDecimal total = totalNode.decimalValue();
        if (total.compareTo(BigDecimal.ZERO) < 0 || total.compareTo(new BigDecimal("100")) > 0) {
            return ValidationResult.fail("total_score超出0~100: " + total);
        }

        // 指标分：对象/齐备/不超权重上限
        JsonNode metricNode = root.get("metric_scores");
        if (metricNode == null || !metricNode.isObject() || metricNode.isEmpty()) {
            return ValidationResult.fail("metric_scores缺失或非对象");
        }
        Map<String, BigDecimal> metricScores = new LinkedHashMap<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (Iterator<String> it = metricNode.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            JsonNode value = metricNode.get(name);
            if (value == null || !value.isNumber()) {
                return ValidationResult.fail("指标分非数值: " + name);
            }
            BigDecimal score = value.decimalValue();
            if (score.compareTo(BigDecimal.ZERO) < 0) {
                return ValidationResult.fail("指标分为负: " + name);
            }
            BigDecimal cap = weightCaps.get(name);
            if (cap == null) {
                return ValidationResult.fail("指标不在任务指标集内: " + name);
            }
            if (score.compareTo(cap) > 0) {
                return ValidationResult.fail("指标分超权重上限(" + name + "): " + score + ">" + cap);
            }
            metricScores.put(name, score);
            sum = sum.add(score);
        }
        if (!metricScores.keySet().equals(weightCaps.keySet())) {
            return ValidationResult.fail("指标不齐备: 缺" + diff(weightCaps.keySet(), metricScores.keySet()));
        }

        // Σ指标分 = 总分 ± 0.5
        if (sum.subtract(total).abs().compareTo(SUM_TOLERANCE) > 0) {
            return ValidationResult.fail("指标分之和与总分差超容差: Σ" + sum + " vs " + total);
        }

        // 评语：非空≤2000字
        JsonNode commentNode = root.get("ai_comment");
        if (commentNode == null || !commentNode.isTextual() || commentNode.asText().isBlank()) {
            return ValidationResult.fail("ai_comment缺失或为空");
        }
        String comment = commentNode.asText();
        if (comment.length() > COMMENT_MAX_LEN) {
            return ValidationResult.fail("ai_comment超长(>" + COMMENT_MAX_LEN + "字)");
        }
        return ValidationResult.ok(total, metricScores, comment);
    }

    /**
     * LLM输出归一（健壮性核心）：①剥```markdown代码块围栏 ②截取首个平衡{...}块
     * （容忍前后解释文字/截断尾巴；不平衡（截断）→null→重试）。
     */
    String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        // 剥markdown围栏：```json ... ``` / ``` ... ```
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        // 平衡括号扫描（字符串字面量内的{}不计入深度——评语可能含花括号）
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                if (inString) {
                    escaped = true;
                }
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private String diff(java.util.Set<String> expected, java.util.Set<String> actual) {
        java.util.Set<String> missing = new java.util.LinkedHashSet<>(expected);
        missing.removeAll(actual);
        return missing.toString();
    }
}
