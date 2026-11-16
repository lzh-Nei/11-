package com.aies.service;

import com.aies.entity.GoldenDataset;
import com.aies.mapper.GoldenDatasetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 黄金集解析回放工具（W7-C1 验收载体，W8 一致性回归复用）。
 *
 * <p>触发：{@code java -jar aies-server-1.0.0.jar --parse.replay=true}
 * （建议叠加 {@code --spring.main.web-application-type=none} 容器内离线执行）。</p>
 *
 * <p>口径（登记）：对 golden_dataset active=1 的样本逐个走与 ParseWorker 相同的
 * 解析链（FileStorage.openStream → ParseService 提取 → 文本落 parsed/ 前缀），
 * 逐行输出 key|状态|字符数；不写 submission/evaluation 业务表（黄金回放不污染数据）。
 * 退出码：12/12 SUCCESS→0；存在 FAILED→1（验收硬指标判据）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "parse.replay", havingValue = "true")
public class ParseReplayRunner implements CommandLineRunner {

    private final GoldenDatasetMapper goldenDatasetMapper;
    private final FileStorage fileStorage;
    private final ParseService parseService;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(String... args) {
        List<GoldenDataset> samples = goldenDatasetMapper.selectList(
                new LambdaQueryWrapper<GoldenDataset>().eq(GoldenDataset::getActive, 1));
        log.info("=== 黄金集解析回放开始: {} 个active样本 ===", samples.size());

        int success = 0;
        for (GoldenDataset sample : samples) {
            String key = sample.getSampleFileKey();
            try (InputStream in = fileStorage.openStream(key)) {
                String text = key.endsWith(".pdf")
                        ? parseService.extractPdf(in)
                        : parseService.extractDocx(in);
                String textKey = "parsed/" + key.replace('/', '_') + ".txt";
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                fileStorage.store(textKey, new ByteArrayInputStream(bytes), bytes.length);
                log.info("回放: {} | SUCCESS | textKey={} | chars={}", key, textKey, text.length());
                success++;
            } catch (Exception e) {
                log.error("回放: {} | FAILED | err={}", key, e.getMessage());
            }
        }

        log.info("=== 黄金集解析回放结束: {}/{} SUCCESS ===", success, samples.size());
        // 回放为一次性离线任务：跑完即退出，退出码作验收判据（docker exec 可捕获）
        System.exit(success == samples.size() && !samples.isEmpty() ? 0 : 1);
    }
}
