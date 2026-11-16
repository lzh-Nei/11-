package com.aies.service;

import com.aies.config.AiProperties;
import com.aies.entity.LlmConfig;
import com.aies.model.LlmRawResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ApiKeyCipher apiKeyCipher;

    public OllamaLlmClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this(aiProperties, objectMapper, new ApiKeyCipher(aiProperties));
    }

    @Autowired
    public OllamaLlmClient(AiProperties aiProperties, ObjectMapper objectMapper,
                           ApiKeyCipher apiKeyCipher) {
        this.baseClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = objectMapper;
        this.permits = new Semaphore(aiProperties.getSemaphore().getPermits());
        this.queueWarnSeconds = aiProperties.getSemaphore().getQueueWarnSeconds();
        this.baseUrl = aiProperties.getOllama().getBaseUrl();
        this.apiKeyCipher = apiKeyCipher;
        log.info("LlmClient 装配: ollamaBaseUrl={}, permits={}", baseUrl, permits.availablePermits());
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

    /** 按 llm_config.provider 路由：Ollama 本地 / 云端 OpenAI 兼容。 */
    private LlmRawResult doCall(String promptText, LlmConfig cfg) {
        LlmProvider provider = LlmProvider.of(cfg.getProvider());
        if (provider == LlmProvider.OLLAMA) {
            return doOllamaCall(promptText, cfg);
        }
        return doOpenAiCompatibleCall(promptText, cfg, provider);
    }

    /** Ollama /api/generate 非流式。 */
    private LlmRawResult doOllamaCall(String promptText, LlmConfig cfg) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", cfg.getModelName());
        body.put("prompt", promptText);
        body.put("stream", false);
        body.putObject("options").put("temperature", cfg.getTemperature());
        String endpoint = (cfg.getApiBaseUrl() == null || cfg.getApiBaseUrl().isBlank()
                ? baseUrl : cfg.getApiBaseUrl().trim().replaceAll("/+$", "")) + "/api/generate";
        OkHttpClient client = baseClient.newBuilder()
                .callTimeout(cfg.getTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        long start = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() != 200) {
                throw new LlmInvocationException("Ollama 非200响应: HTTP " + response.code());
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new LlmInvocationException("Ollama 响应体为空");
            }
            JsonNode root = objectMapper.readTree(responseBody.string());
            JsonNode respNode = root.get("response");
            if (respNode == null || !respNode.isTextual()) {
                throw new LlmInvocationException("Ollama 响应缺 response 字段");
            }
            String modelVersion = root.path("model").asText(cfg.getModelName());
            return new LlmRawResult(respNode.asText(""), System.currentTimeMillis() - start, modelVersion);
        } catch (LlmInvocationException e) {
            throw e;
        } catch (java.io.InterruptedIOException e) {
            throw new LlmInvocationException("Ollama 调用超时(" + cfg.getTimeoutSeconds() + "s)", e);
        } catch (Exception e) {
            throw new LlmInvocationException("Ollama 调用失败: " + e.getMessage(), e);
        }
    }

    /** OpenAI 兼容接口 /chat/completions（OpenAI/DeepSeek/通义/Kimi/智谱/自定义）。 */
    private LlmRawResult doOpenAiCompatibleCall(String promptText, LlmConfig cfg,
                                                LlmProvider provider) {
        String configuredBase = cfg.getApiBaseUrl() == null || cfg.getApiBaseUrl().isBlank()
                ? provider.defaultBaseUrl() : cfg.getApiBaseUrl().trim();
        if (configuredBase == null || configuredBase.isBlank()) {
            throw new LlmInvocationException(provider.label() + " 未配置 Base URL");
        }
        String base = configuredBase.replaceAll("/+$", "");
        String endpoint = base.endsWith("/chat/completions") ? base : base + "/chat/completions";
        String apiKey;
        try {
            apiKey = apiKeyCipher.decrypt(cfg.getApiKeyCipher());
        } catch (IllegalArgumentException e) {
            throw new LlmInvocationException(e.getMessage(), e);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmInvocationException(provider.label() + " 未配置 API Key");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", cfg.getModelName());
        ArrayNode messages = body.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", promptText);
        body.put("temperature", 0);
        body.put("stream", false);
        OkHttpClient client = baseClient.newBuilder()
                .callTimeout(cfg.getTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        long start = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String raw = responseBody == null ? "" : responseBody.string();
            if (response.code() != 200) {
                throw new LlmInvocationException(provider.label() + " 非200响应: HTTP "
                        + response.code() + " " + truncate(raw));
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) {
                throw new LlmInvocationException(provider.label() + " 响应缺 choices[0].message.content");
            }
            String modelVersion = root.path("model").asText(cfg.getModelName());
            return new LlmRawResult(content.asText(""), System.currentTimeMillis() - start, modelVersion);
        } catch (LlmInvocationException e) {
            throw e;
        } catch (java.io.InterruptedIOException e) {
            throw new LlmInvocationException(provider.label() + " 调用超时("
                    + cfg.getTimeoutSeconds() + "s)", e);
        } catch (Exception e) {
            throw new LlmInvocationException(provider.label() + " 调用失败: " + e.getMessage(), e);
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= 180 ? compact : compact.substring(0, 180) + "…";
    }
}
