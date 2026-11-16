package com.aies.service;

import com.aies.entity.PromptTemplate;
import com.aies.model.EvaluationOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 留痕记录器（04-05 §六；红线R2：留痕禁止与评价主事务同事务——
 * 调用方必须在事务提交后调用本组件）。
 *
 * <p>冷桶 trace/{traceId}.json，字段口径按规范§六；写失败→V6补偿表
 * （trace_compensation：payload原文+定时任务重试补写，W8-M1落地），
 * 不阻塞评价主链路（红线R2）。偏差闭环：W7-C2"MVP以ERROR日志承载补偿入口"
 * 已由V6补偿表+TraceCompensationService定时任务替代（04-05偏差注记兑现）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceRecorder {

    /** 冷桶留痕key前缀（规范§六：trace/{traceId}.json） */
    static final String TRACE_PREFIX = "trace/";

    private final FileStorage fileStorage;
    private final ObjectMapper objectMapper;
    private final TraceCompensationService traceCompensationService;

    /**
     * 写留痕（调用时机：result事务提交后；本方法内部不触碰任何DB事务资源）。
     *
     * @param traceId       评价开始即生成的UUID32（贯穿日志/DB/留痕）
     * @param template      Prompt模板（版本落痕；前置失败可为null）
     * @param outcome       管道产物（raw_response/schema_valid/retry_count/elapsed_ms）
     * @param requestDigest 请求payload摘要（Prompt前200字符+长度，防全量敏感文本入冷桶）
     */
    public void record(String traceId, PromptTemplate template, EvaluationOutcome outcome, String requestDigest) {
        String key = TRACE_PREFIX + traceId + ".json";
        byte[] bytes = null;
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("trace_id", traceId);
            node.put("prompt_template_version", template == null ? 0 : template.getVersionNo());
            node.put("model_name", outcome.modelVersion());
            node.put("temperature", 0.0);
            node.put("request_payload_digest", requestDigest);
            node.put("raw_response", outcome.rawResponse());
            node.put("schema_valid", outcome.success());
            node.put("schema_fail_reason", outcome.failReason());
            node.put("retry_count", outcome.retryCount());
            node.put("elapsed_ms", outcome.elapsedMs());
            node.put("create_time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            bytes = objectMapper.writeValueAsBytes(node);
            fileStorage.storeCold(key, new ByteArrayInputStream(bytes), bytes.length);
            log.info("评价留痕已落冷桶: key={} retryCount={} valid={} elapsed={}ms",
                    key, outcome.retryCount(), outcome.success(), outcome.elapsedMs());
        } catch (Exception e) {
            // 补偿入口（R2不阻塞主链路）：V6补偿表承载（payload原文入队，定时任务重试补写；
            // 序列化即败则bytes为null，enqueue降级仅留traceId可人工补写）
            log.warn("评价留痕写冷桶失败(已入补偿表): traceId={} err={}", traceId, e.getMessage());
            traceCompensationService.enqueue(traceId, key, bytes == null ? new byte[0] : bytes, e.getMessage());
        }
    }
}
