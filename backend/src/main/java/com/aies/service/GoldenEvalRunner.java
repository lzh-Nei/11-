package com.aies.service;

import com.aies.entity.GoldenDataset;
import com.aies.entity.LlmConfig;
import com.aies.entity.PromptTemplate;
import com.aies.entity.TaskMetric;
import com.aies.mapper.GoldenDatasetMapper;
import com.aies.mapper.TaskMetricMapper;
import com.aies.model.EvaluationOutcome;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 黄金集送评回放工具（WP-2025-W7-C2 任务3：验收口径载体；W8 NFR-03正式回归的基线预演）。
 *
 * <p>触发：{@code java -jar aies-server-1.0.0.jar --eval.replay=true}
 * （容器内离线执行，与 {@link ParseReplayRunner} 同风格）。</p>
 *
 * <p>口径：对 golden_dataset active=1 样本逐个走与评价管道完全相同的链路
 * （模板/llm_config/指标权重/文本/PromptAssembler/重试/Schema校验——复用
 * {@link EvaluationService#runPipeline}），AI总分 vs 教师标注总分按
 * |AI-教师|≤总分×5% 判一致（golden_dataset.last_consistency 注释口径），
 * 逐样本回写 last_consistency（一致=1.000/不一致=0.000）。
 * 逐行输出偏差分布表（验收物+答辩素材），退出码：12/12评分成功→0（判"评分成功"
 * 不判一致率——一致率≥70%属W8 NFR-03正式回归目标，本表仅基线预演）。</p>
 *
 * <p>指标权重来源：task_metric 全表按指标名匹配（黄金集三指标与任务2指标集同名
 * ——教师标注结构即按任务指标集生成）；黄金样本不关联 submission/evaluation
 * 业务表（W7-C1"黄金回放不污染数据"既定口径）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "eval.replay", havingValue = "true")
public class GoldenEvalRunner implements CommandLineRunner {

    /** 一致判定阈值：|AI-教师| ≤ 教师总分×5%（golden_dataset.last_consistency 注释口径） */
    private static final BigDecimal CONSISTENCY_THRESHOLD_RATIO = new BigDecimal("0.05");

    private final GoldenDatasetMapper goldenDatasetMapper;
    private final TaskMetricMapper taskMetricMapper;
    private final FileStorage fileStorage;
    private final ParseService parseService;
    private final EvaluationService evaluationService;
    private final PromptAssembler promptAssembler;
    private final TraceRecorder traceRecorder;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(String... args) throws Exception {
        List<GoldenDataset> samples = goldenDatasetMapper.selectList(
                new LambdaQueryWrapper<GoldenDataset>().eq(GoldenDataset::getActive, 1));
        log.info("=== 黄金集送评开始: {} 个active样本 ===", samples.size());

        PromptTemplate template = evaluationService.loadTemplate();
        if (template == null) {
            log.error("启用版prompt_template缺失，送评中止");
            System.exit(1);
            return;
        }
        LlmConfig cfg = evaluationService.loadLlmConfig();
        Map<String, BigDecimal> nameWeight = loadMetricWeights();

        // 双线程送评（本机多核并行，OLLAMA_NUM_PARALLEL=2双槽GPU推理承接）：
        // evaluateOne样本间无共享可变状态（模板/cfg/权重表只读；llmClient Semaphore(2)
        // 恰好匹配双线程），逐样本按提交顺序回收结果行，输出顺序与串行版一致
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(2);
        List<java.util.concurrent.Future<String>> futures = new ArrayList<>();
        for (GoldenDataset sample : samples) {
            futures.add(pool.submit(() -> evaluateOne(sample, template, cfg, nameWeight)));
        }
        int evaluated = 0;
        int consistent = 0;
        List<String> tableRows = new ArrayList<>();
        for (java.util.concurrent.Future<String> future : futures) {
            String row = future.get();
            tableRows.add(row);
            // 统计口径：成功行=无FAILED/SKIP标记（成功行格式为"样本|教师|AI|Δ|阈值|指标偏差|判定|重试|耗时"）
            if (!row.contains("FAILED") && !row.contains("SKIP")) {
                evaluated++;
                if (row.contains("一致")) {
                    consistent++;
                }
            }
        }
        pool.shutdown();

        log.info("=== 黄金集偏差分布表（逐样本 |AI-教师| vs 教师总分×5%）===");
        log.info("样本            | 教师总分 | AI总分 | |Δ|   | 阈值  | 指标偏差                    | 判定   | 重试 | 耗时");
        tableRows.forEach(log::info);
        log.info("=== 送评汇总: {}/{} 评分成功; 一致率 {}/{}（W8 NFR-03正式回归目标≥70%）===",
                evaluated, samples.size(), consistent, samples.size());

        System.exit(evaluated == samples.size() && !samples.isEmpty() ? 0 : 1);
    }

    /** 单样本送评：提取→组装→管道→比对→回写last_consistency；返回表格行 */
    private String evaluateOne(GoldenDataset sample, PromptTemplate template, LlmConfig cfg,
                               Map<String, BigDecimal> nameWeight) {
        String key = sample.getSampleFileKey();
        String label = key.substring(key.lastIndexOf('/') + 1);
        String traceId = java.util.UUID.randomUUID().toString().replace("-", "");
        try {
            // 指标集=教师标注键序（保序），权重=task_metric同名匹配（缺权重→跳过该样本防误判）
            Map<String, BigDecimal> teacherMetrics = objectMapper.readValue(
                    sample.getTeacherMetrics(), new TypeReference<LinkedHashMap<String, BigDecimal>>() {
                    });
            List<TaskMetric> metrics = new ArrayList<>();
            Map<String, BigDecimal> caps = new LinkedHashMap<>();
            for (Map.Entry<String, BigDecimal> entry : teacherMetrics.entrySet()) {
                BigDecimal weight = nameWeight.get(entry.getKey());
                if (weight == null) {
                    log.error("指标权重缺失(检查task_metric种子): {} | {}", label, entry.getKey());
                    return String.format("%-14s | %-7s | SKIP：指标权重缺失(%s)", label, "-", entry.getKey());
                }
                TaskMetric metric = new TaskMetric();
                metric.setMetricName(entry.getKey());
                metric.setWeight(weight);
                metrics.add(metric);
                caps.put(entry.getKey(), weight);
            }

            String text;
            try (InputStream in = fileStorage.openStream(key)) {
                text = key.endsWith(".pdf")
                        ? parseService.extractPdf(in)
                        : parseService.extractDocx(in);
            }
            // Prompt组装与评价管道同源（PromptAssembler，防两套组装逻辑漂移）
            var assembled = promptAssembler.assemble(template.getContent(), metrics, text);
            EvaluationService.PreparedEvaluation prep = new EvaluationService.PreparedEvaluation(
                    -sample.getId(), template, cfg, assembled.text(), Map.copyOf(caps), traceId);

            EvaluationOutcome outcome = evaluationService.runPipeline(prep);
            // 留痕冷桶（送评同样留痕，规范§六；失败不阻塞）
            traceRecorder.record(traceId, template, outcome, evaluationService.digest(assembled.text()));

            if (!outcome.success()) {
                goldenDatasetMapper.updateById(withConsistency(sample, null));
                return String.format("%-14s | %-7s | FAILED：%.80s",
                        label, sample.getTeacherTotal(), outcome.failReason());
            }

            // 一致性比对：|AI-教师| ≤ 教师总分×5%
            BigDecimal teacherTotal = sample.getTeacherTotal();
            BigDecimal aiTotal = outcome.totalScore();
            BigDecimal deviation = aiTotal.subtract(teacherTotal).abs();
            BigDecimal threshold = teacherTotal.multiply(CONSISTENCY_THRESHOLD_RATIO)
                    .setScale(2, RoundingMode.HALF_UP);
            boolean isConsistent = deviation.compareTo(threshold) <= 0;

            // 逐指标偏差（教师分→AI分）
            Map<String, BigDecimal> aiMetrics = objectMapper.readValue(
                    outcome.metricScoresJson(), new TypeReference<LinkedHashMap<String, BigDecimal>>() {
                    });
            StringBuilder metricDev = new StringBuilder();
            for (Map.Entry<String, BigDecimal> entry : teacherMetrics.entrySet()) {
                BigDecimal aiScore = aiMetrics.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                BigDecimal d = aiScore.subtract(entry.getValue()).abs();
                if (metricDev.length() > 0) {
                    metricDev.append(" ");
                }
                metricDev.append(entry.getKey()).append(":Δ").append(d.stripTrailingZeros().toPlainString());
            }

            goldenDatasetMapper.updateById(withConsistency(sample,
                    isConsistent ? BigDecimal.ONE : BigDecimal.ZERO));
            return String.format("%-14s | %-7s | %-6s | %-5s | %-5s | %-26s | %-5s | %-3d  | %dms",
                    label,
                    teacherTotal.stripTrailingZeros().toPlainString(),
                    aiTotal.stripTrailingZeros().toPlainString(),
                    deviation.stripTrailingZeros().toPlainString(),
                    threshold.stripTrailingZeros().toPlainString(),
                    metricDev.toString(),
                    isConsistent ? "一致" : "偏差",
                    outcome.retryCount(),
                    outcome.elapsedMs());
        } catch (Exception e) {
            log.error("送评异常: {} err={}", label, e.getMessage(), e);
            return String.format("%-14s | %-7s | FAILED：%s", label, sample.getTeacherTotal(), e.getMessage());
        }
    }

    /** name→weight 全表映射（黄金三指标与任务指标集同名；同名异权取首个——dev库指标名唯一） */
    private Map<String, BigDecimal> loadMetricWeights() {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (TaskMetric metric : taskMetricMapper.selectList(null)) {
            map.putIfAbsent(metric.getMetricName(), metric.getWeight());
        }
        return map;
    }

    /** 回写last_consistency（1.000/0.000；评分失败置null表示本轮无有效回归值） */
    private GoldenDataset withConsistency(GoldenDataset sample, BigDecimal consistency) {
        GoldenDataset update = new GoldenDataset();
        update.setId(sample.getId());
        update.setLastConsistency(consistency == null ? null
                : consistency.setScale(3, RoundingMode.HALF_UP));
        return update;
    }
}
