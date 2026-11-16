package com.aies.service;

import com.aies.config.AiProperties;
import com.aies.entity.LlmConfig;
import com.aies.model.LlmRawResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OllamaLlmClient 单测（MockWebServer，R6单测禁真实Ollama）。
 * 覆盖：200解析/非200/超时计重试语义/请求形状（model+temperature来自llm_config，R4）。
 */
class OllamaLlmClientTest {

    private MockWebServer server;
    private OllamaLlmClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        AiProperties props = new AiProperties();
        props.getOllama().setBaseUrl(server.url("/").toString().replaceAll("/$", ""));
        client = new OllamaLlmClient(props, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private LlmConfig cfg(int timeoutSeconds) {
        LlmConfig cfg = new LlmConfig();
        cfg.setModelName("qwen2.5:7b");
        cfg.setTemperature(new BigDecimal("0.0"));
        cfg.setTimeoutSeconds(timeoutSeconds);
        cfg.setMaxRetry(3);
        return cfg;
    }

    /** 200：response字段→rawResponse，model字段→modelVersion，elapsedMs≥0 */
    @Test
    void successResponseParsed() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"model\":\"qwen2.5:7b\",\"response\":\"{\\\"total_score\\\":85}\",\"done\":true}"));

        LlmRawResult raw = client.evaluate("prompt-text", cfg(60));

        assertThat(raw.rawResponse()).isEqualTo("{\"total_score\":85}");
        assertThat(raw.modelVersion()).isEqualTo("qwen2.5:7b");
        assertThat(raw.elapsedMs()).isGreaterThanOrEqualTo(0);
    }

    /** 请求形状：/api/generate 非流式，model/temperature 来自 llm_config（红线R4） */
    @Test
    void requestShapeUsesConfigValues() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"model\":\"qwen2.5:7b\",\"response\":\"ok\",\"done\":true}"));

        client.evaluate("prompt-text", cfg(60));

        var recorded = server.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/api/generate");
        String body = recorded.getBody().readUtf8();
        assertThat(body).contains("\"model\":\"qwen2.5:7b\"")
                .contains("\"stream\":false")
                .contains("\"temperature\":0")
                .contains("\"prompt\":\"prompt-text\"");
    }

    /** 非200：统一 LlmInvocationException（不外泄HTTP细节，由管道重试） */
    @Test
    void non200ThrowsInvocationException() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThatThrownBy(() -> client.evaluate("p", cfg(60)))
                .isInstanceOf(LlmInvocationException.class)
                .hasMessageContaining("500");
    }

    /** 超时：callTimeout=llm_config.timeout_seconds → 超时语义=一次失败重试（规范§三） */
    @Test
    void timeoutMappedToInvocationException() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        assertThatThrownBy(() -> client.evaluate("p", cfg(1)))
                .isInstanceOf(LlmInvocationException.class)
                .hasMessageContaining("超时");
    }

    /** 空响应体：缺response字段防御（readTree空串返回MissingNode不抛——归一为调用失败重试） */
    @Test
    void emptyBodyThrowsInvocationException() {
        server.enqueue(new MockResponse().setResponseCode(200));

        assertThatThrownBy(() -> client.evaluate("p", cfg(60)))
                .isInstanceOf(LlmInvocationException.class);
    }

    /** 信号量泄漏回归（W7-C2实弹教训）：连续多次调用后许可必须全额归还——
     * 防双重获取（tryAcquire后又acquire）净泄漏1许可/次的bug复发（该bug单测夹具
     * 原本无法暴露——每次测试仅1次调用，泄漏只在第2次调用起体现） */
    @Test
    void semaphorePermitsFullyRestoredAfterSequentialCalls() {
        server.enqueue(new MockResponse().setBody("{\"model\":\"qwen2.5:7b\",\"response\":\"ok1\",\"done\":true}"));
        server.enqueue(new MockResponse().setBody("{\"model\":\"qwen2.5:7b\",\"response\":\"ok2\",\"done\":true}"));
        server.enqueue(new MockResponse().setBody("{\"model\":\"qwen2.5:7b\",\"response\":\"ok3\",\"done\":true}"));

        client.evaluate("p1", cfg(60));
        client.evaluate("p2", cfg(60));
        client.evaluate("p3", cfg(60));

        assertThat(client.permitsAvailable()).isEqualTo(2);
    }
}
