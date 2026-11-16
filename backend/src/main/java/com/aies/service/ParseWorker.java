package com.aies.service;

import com.aies.config.RabbitConfig;
import com.aies.entity.Submission;
import com.aies.mapper.SubmissionMapper;
import com.aies.model.EvalMessage;
import com.aies.model.ParseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 解析 worker（UC-16 基本流第5步消费者，W7-C1）。
 *
 * <p>状态机推进（V3 §7.1 四态）：PENDING --消息--> QUEUED --提取落桶--> SUCCESS/FAILED。</p>
 *
 * <p>可靠性口径：①幂等——SUCCESS 直接跳过（MQ 至少一次投递语义）；
 * ②提取失败（毒文件/格式异常）捕获后置 FAILED 正常 ack，不 requeue 死循环；
 * ③中间崩溃留 QUEUED——PENDING/QUEUED 均可通过黄金集回放工具（ParseReplayRunner）
 * 或重新投递重放，不做假实现。</p>
 *
 * <p>W7-C2 增量：SUCCESS 后投递评价消息（UC-20 MQ内部链路，03-03 §七契约——
 * 无REST触发接口）；发送失败留 SUCCESS 可经 E-03 重评/重放恢复，不回滚解析状态。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParseWorker {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_FAILED = "FAILED";
    /** S-03 提取文本预览上限（全量落桶，DB 仅存 key） */
    static final String PARSED_PREFIX = "parsed/";

    private final SubmissionMapper submissionMapper;
    private final FileStorage fileStorage;
    private final ParseService parseService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitConfig.PARSE_QUEUE)
    public void onMessage(ParseMessage message) {
        long id = message.submissionId();
        Submission submission = submissionMapper.selectById(id);
        if (submission == null) {
            // 事务提交早于消息可见的极小窗口/脏消息：留 PENDING 可重放，跳过不 ack 失败
            log.warn("解析消息对应提交不存在，跳过: submissionId={}", id);
            return;
        }
        if (STATUS_SUCCESS.equals(submission.getParseStatus())) {
            log.info("重复投递跳过（已SUCCESS）: submissionId={}", id);
            return;
        }

        submission.setParseStatus(STATUS_QUEUED);
        submissionMapper.updateById(submission);
        log.info("解析开始(QUEUED): submissionId={} file={}", id, submission.getFileName());

        try {
            String text;
            try (InputStream in = fileStorage.openStream(submission.getFileKey())) {
                text = "pdf".equals(submission.getFileType())
                        ? parseService.extractPdf(in)
                        : parseService.extractDocx(in);
            }
            String textKey = PARSED_PREFIX + UUID.randomUUID().toString().replace("-", "") + ".txt";
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            fileStorage.store(textKey, new ByteArrayInputStream(bytes), bytes.length);

            submission.setParsedTextKey(textKey);
            submission.setParseStatus(STATUS_SUCCESS);
            submissionMapper.updateById(submission);
            log.info("解析SUCCESS: submissionId={} textKey={} chars={}", id, textKey, text.length());
            // UC-20（W7-C2）：解析完成→评价链触发（MQ内部链路）；失败留SUCCESS可恢复
            dispatchEvalMessage(id);
        } catch (Exception e) {
            submission.setParsedTextKey(null);
            submission.setParseStatus(STATUS_FAILED);
            submissionMapper.updateById(submission);
            // 前端 UC-16 E4 口径：30002 提示转标准 PDF 重传（评价队列不进入）
            log.warn("解析FAILED(UC-16 E4→30002): submissionId={} type={} err={}",
                    id, submission.getFileType(), e.getMessage());
        }
    }

    /** UC-20 触发（W7-C2）：SUCCESS→eval.submit；发送失败仅告警（可重评恢复，不反噬解析状态） */
    private void dispatchEvalMessage(long submissionId) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EVAL_EXCHANGE,
                    RabbitConfig.EVAL_ROUTING_KEY, new EvalMessage(submissionId));
            log.info("评价消息已投递: submissionId={}", submissionId);
        } catch (Exception e) {
            log.error("评价消息发送失败(留SUCCESS可重评): submissionId={} err={}",
                    submissionId, e.getMessage());
        }
    }
}
