package com.aies.service;

import com.aies.config.AiProperties;
import com.aies.entity.TaskMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PromptAssembler 单测（04-05 §四：占位符替换/保头保尾截断/指标清单格式化；
 * 红线R1旁证——组装器内无业务Prompt语句，只做替换与裁剪）。
 */
class PromptAssemblerTest {

    private static final String TEMPLATE =
            "请依据以下评价指标对作业文本进行评分：{metrics}。作业提取文本：{parsed_text}。仅输出JSON。";

    private PromptAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new PromptAssembler(new AiProperties());
    }

    private TaskMetric metric(String name, String weight) {
        TaskMetric m = new TaskMetric();
        m.setMetricName(name);
        m.setWeight(new BigDecimal(weight));
        return m;
    }

    /** 占位符替换：{metrics}=名称（权重N）清单；{parsed_text}=原文 */
    @Test
    void placeholdersAreReplaced() {
        String prompt = assembler.assemble(TEMPLATE,
                List.of(metric("功能正确性", "40"), metric("代码质量", "35.00")),
                "作业正文内容").text();

        assertThat(prompt)
                .contains("功能正确性（权重40）")
                .contains("代码质量（权重35）")
                .doesNotContain("{metrics}")
                .contains("作业正文内容")
                .doesNotContain("{parsed_text}");
    }

    /** 截断策略：超限文本保头保尾+截断标记（头70%+尾30%，尾部标记保留） */
    @Test
    void overLimitTextTruncatedKeepHeadAndTail() {
        AiProperties props = new AiProperties();
        props.getPrompt().setTextLimit(100);
        PromptAssembler limited = new PromptAssembler(props);
        String text = "A".repeat(300) + "TAIL";

        String prompt = limited.assemble(TEMPLATE, List.of(metric("m", "40")), text).text();

        assertThat(prompt).contains("AAA").contains("TAIL")
                .contains(PromptAssembler.TRUNCATE_MARK);
        assertThat(prompt.length()).isLessThan(TEMPLATE.length() + 200);
    }

    /** 未超限文本不截断（保真） */
    @Test
    void withinLimitTextUntouched() {
        String text = "短文本";
        assertThat(assembler.assemble(TEMPLATE, List.of(metric("m", "40")), text).text())
                .contains(text).doesNotContain(PromptAssembler.TRUNCATE_MARK);
    }

    /** 空指标集 → IllegalStateException（组装前置失败，防无指标评分） */
    @Test
    void emptyMetricsRejected() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> assembler.assemble(TEMPLATE, List.of(), "x"))
                .isInstanceOf(IllegalStateException.class);
    }
}
