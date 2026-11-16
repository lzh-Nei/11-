package com.aies.service;

import com.aies.config.AiProperties;
import com.aies.entity.LlmConfig;
import com.aies.model.LlmRawResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Ollama实现（04-05 §三；W7-C2）。
 *
 * <p>实现选型登记（偏差，待04-05注记）：规约原文"LangChain4j构建"——本项目
 * pom已含 okhttp 4.12（零新增依赖），且 LlmClient/LlmRawResult 接口契约不变、
 * 红线R1~R6全部不受影响，故以 OkHttp 直连 /api/generate 非流式承载；
 * temperature/model/timeout/max_retry 一律 llm_config 读取（红线R4不豁免）。</p>
 *
 * <p>并发守卫：Semaphore(2) 包裹调用（本地显存约束 03-01 §三），
 * 排队超30s打WARN后继续等待（不拒绝）；finally release。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ai.client", havingValue = "ollama", matchIfMissing = true)
public class OllamaLlmClient implements LlmClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient baseClient;
    private final ObjectMapper objectMapper;
    private final Semaphore permits;
    private final int queueWarnSeconds;
    private final String baseUrl;

    public OllamaLlmClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.baseClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = objectMapper;
        this.permits = new Semaphore(aiProperties.getSemaphore().getPermits());
        this.queueWarnSeconds = aiProperties.getSemaphore().getQueueWarnSeconds();
        this.baseUrl = aiProperties.getOllama().getBaseUrl();
        log.info("OllamaLlmClient 装配: baseUrl={}, permits={}", baseUrl, permits.availablePermits());
    }

    @Override
    public LlmRawResult evaluate(String promptText, LlmConfig cfg) {
        // 排队守卫：tryAcquire成功即已持有许可（不得再acquire二次扣减——W7-C2实弹教训：
        // 双重获取每次调用净泄漏1许可，2次后永久阻塞，黄金送评样本2起全卡死即此因）
        try {
            if (!permits.tryAcquire(queueWarnSeconds, TimeUnit.SECONDS)) {
                log.warn("LLM并发排队超过{}s（permits={}），继续等待",
                        queueWarnSeconds, permits.availablePermits());
                permits.acquireUninterruptibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmInvocationException("LLM调用排队等待被中断", e);
        }
        try {
            return doCall(promptText, cfg);
        } finally {
            permits.release();
        }
    }

    /** 测试访问器：当前剩余许可数（信号量泄漏回归测试用，生产无调用方） */
    int permitsAvailable() {
        return permits.availablePermits();
    }

    /** 实际HTTP调用（timeout取llm_config，非200/网络异常/超时统一LlmInvocationException） */
    private LlmRawResult doCall(String promptText, LlmConfig cfg) {
        // /api/generate 非流式；temperature 锁定0由 llm_config 承载（R4：禁止硬编码）
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", cfg.getModelName());
        body.put("prompt", promptText);
        body.put("stream", false);
        body.putObject("options").put("temperature", cfg.getTemperature());
        OkHttpClient client = baseClient.newBuilder()
                .callTimeout(cfg.getTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(baseUrl + "/api/generate")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        long start = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() != 200) {
                throw new LlmInvocationException("Ollama非200响应: HTTP " + response.code());
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new LlmInvocationException("Ollama响应体为空");
            }
            JsonNode root = objectMapper.readTree(responseBody.string());
            JsonNode respNode = root.get("response");
            if (respNode == null || !respNode.isTextual()) {
                // 空体/缺response字段（readTree空串返回MissingNode不抛）：归一为调用失败（重试路径）
                throw new LlmInvocationException("Ollama响应缺response字段");
            }
            String modelVersion = root.path("model").asText(cfg.getModelName());
            return new LlmRawResult(respNode.asText(""), System.currentTimeMillis() - start, modelVersion);
        } catch (LlmInvocationException e) {
            throw e;
        } catch (java.io.InterruptedIOException e) {
            // 超时语义（规范§三）：计为一次失败重试（okio AsyncTimeout超时=InterruptedIOException
            // "timeout"，含SocketTimeoutException子类）
            throw new LlmInvocationException("Ollama调用超时(" + cfg.getTimeoutSeconds() + "s)", e);
        } catch (Exception e) {
            throw new LlmInvocationException("Ollama调用失败: " + e.getMessage(), e);
        }
    }
}
