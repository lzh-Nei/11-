package com.aies.service;

import com.aies.model.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SchemaValidator 单测（04-05 §八：夹具参数化覆盖；LLM输出健壮性最大坑——
 * markdown包裹/前后噪音/截断/非法JSON/指标缺失/超权重/和≠总分 全分支）。
 */
class SchemaValidatorTest {

    /** 黄金集同构指标权重（task 2：功能正确性40/代码质量35/文档规范25） */
    private static final Map<String, BigDecimal> CAPS = caps();

    private SchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SchemaValidator(new ObjectMapper());
    }

    private static Map<String, BigDecimal> caps() {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put("功能正确性", new BigDecimal("40"));
        map.put("代码质量", new BigDecimal("35"));
        map.put("文档规范", new BigDecimal("25"));
        return map;
    }

    private String fixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/llm-fixtures/" + name)) {
            assert in != null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 夹具 valid_happy：干净JSON一次通过（总分/指标/Σ=总分/评语全过） */
    @Test
    void validHappyFixturePasses() throws Exception {
        ValidationResult vr = validator.validate(fixture("valid_happy.json"), CAPS);

        assertThat(vr.valid()).isTrue();
        assertThat(vr.totalScore()).isEqualByComparingTo("85");
        assertThat(vr.metricScores()).containsEntry("功能正确性", new BigDecimal("34"));
        assertThat(vr.aiComment()).isNotBlank();
    }

    /** 夹具 valid_markdown_wrapped：```json 围栏包裹自动剥离（已知最大坑之一） */
    @Test
    void markdownWrappedFixturePasses() throws Exception {
        ValidationResult vr = validator.validate(fixture("valid_markdown_wrapped.json"), CAPS);

        assertThat(vr.valid()).isTrue();
        assertThat(vr.totalScore()).isEqualByComparingTo("72");
    }

    /** 参数化：夹具三连失败（指标缺失/超权重/非法JSON）——规范§八预置响应 */
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "invalid_missing_metric.json",
            "invalid_over_score.json",
            "malformed.json"})
    void invalidFixturesFailWithReason(String name) throws Exception {
        ValidationResult vr = validator.validate(fixture(name), CAPS);

        assertThat(vr.valid()).isFalse();
        assertThat(vr.reason()).isNotBlank();
    }

    /** 前后解释文字噪音：JSON前后允许任意文字（平衡块提取） */
    @Test
    void surroundingNoiseIsTolerated() {
        String raw = "评分结果如下：\n{\"total_score\": 88, \"metric_scores\": "
                + "{\"功能正确性\": 36, \"代码质量\": 32, \"文档规范\": 20}, "
                + "\"ai_comment\": \"良好\"}\n以上。";
        ValidationResult vr = validator.validate(raw, CAPS);

        assertThat(vr.valid()).isTrue();
        assertThat(vr.totalScore()).isEqualByComparingTo("88");
    }

    /** 评语含花括号（字符串字面量内{}不计入括号深度——真实7B输出会复述JSON示例） */
    @Test
    void bracesInsideCommentDoNotBreakExtraction() {
        String raw = "{\"total_score\": 70, \"metric_scores\": "
                + "{\"功能正确性\": 30, \"代码质量\": 25, \"文档规范\": 15}, "
                + "\"ai_comment\": \"输出格式应为 {\\\"total_score\\\":数值} 之类\"}";
        ValidationResult vr = validator.validate(raw, CAPS);

        assertThat(vr.valid()).isTrue();
    }

    /** 裸换行符（W7-C2实弹根因#2）：qwen2.5:7b在ai_comment内输出原始\n（严格JSON非法）
     * ——ALLOW_UNESCAPED_CONTROL_CHARS宽松解析，分数结构与校验纪律不变 */
    @Test
    void rawNewlineInCommentTolerated() {
        String raw = "{\"total_score\": 58, \"metric_scores\": "
                + "{\"功能正确性\": 23, \"代码质量\": 19, \"文档规范\": 16}, "
                + "\"ai_comment\": \"评语：具体分析如下：\n\n1. 功能正确性（23/40）：存在缺陷。\n2. 代码质量（19/35）：结构欠佳。\"}";

        ValidationResult vr = validator.validate(raw, CAPS);

        assertThat(vr.valid()).isTrue();
        assertThat(vr.totalScore()).isEqualByComparingTo("58");
        assertThat(vr.aiComment()).contains("功能正确性");
    }

    /** Σ指标分与总分差超容差±0.5 → 失败（进入重试） */
    @Test
    void sumMismatchBeyondToleranceFails() {
        String raw = "{\"total_score\": 90, \"metric_scores\": "
                + "{\"功能正确性\": 30, \"代码质量\": 30, \"文档规范\": 20}, \"ai_comment\": \"x\"}";
        ValidationResult vr = validator.validate(raw, CAPS);

        assertThat(vr.valid()).isFalse();
        assertThat(vr.reason()).contains("容差");
    }

    /** 容差边界：Σ=总分±0.5 恰好通过 */
    @Test
    void sumWithinTolerancePasses() {
        String raw = "{\"total_score\": 85.5, \"metric_scores\": "
                + "{\"功能正确性\": 35, \"代码质量\": 30, \"文档规范\": 20.5}, \"ai_comment\": \"x\"}";
        ValidationResult vr = validator.validate(raw, CAPS);

        assertThat(vr.valid()).isTrue();
    }

    /** 总分越界 0~100 → 失败 */
    @Test
    void totalOutOfRangeFails() {
        String raw = "{\"total_score\": 120, \"metric_scores\": "
                + "{\"功能正确性\": 40, \"代码质量\": 35, \"文档规范\": 25}, \"ai_comment\": \"x\"}";
        ValidationResult vr = validator.validate(raw, CAPS);

        assertThat(vr.valid()).isFalse();
        assertThat(vr.reason()).contains("0~100");
    }

    /** 评语缺失/空串/超2000字 → 失败 */
    @Test
    void commentIssuesFail() {
        String base = "\"metric_scores\": {\"功能正确性\": 30, \"代码质量\": 30, \"文档规范\": 25}";
        assertThat(validator.validate(
                "{\"total_score\": 85, " + base + "}", CAPS).valid()).isFalse();
        assertThat(validator.validate(
                "{\"total_score\": 85, " + base + ", \"ai_comment\": \"   \"}", CAPS).valid()).isFalse();
        String longComment = "评".repeat(2001);
        assertThat(validator.validate(
                "{\"total_score\": 85, " + base + ", \"ai_comment\": \"" + longComment + "\"}",
                CAPS).valid()).isFalse();
    }

    /** 未知指标/非数值分 → 失败 */
    @Test
    void unknownMetricOrNonNumericFails() {
        assertThat(validator.validate("{\"total_score\": 85, \"metric_scores\": "
                + "{\"功能正确性\": 30, \"代码质量\": 30, \"文档规范\": 25, \"幻觉指标\": 10}, "
                + "\"ai_comment\": \"x\"}", CAPS).reason()).contains("不在任务指标集内");
        assertThat(validator.validate("{\"total_score\": 85, \"metric_scores\": "
                + "{\"功能正确性\": \"三十\", \"代码质量\": 30, \"文档规范\": 25}, "
                + "\"ai_comment\": \"x\"}", CAPS).reason()).contains("非数值");
    }

    /** 空输入/无JSON → 失败（null驱动重试） */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"抱歉，我无法评分。", "```json\n{\"total\":"})
    void blankOrNoJsonFails(String raw) {
        ValidationResult vr = validator.validate(raw, CAPS);

        assertThat(vr.valid()).isFalse();
    }
}
