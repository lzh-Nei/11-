package com.aies.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI评价管道运行配置（W7-C2）。运行参数（model/temperature/timeout/max_retry）
 * 一律 llm_config 表读取（红线R4），本类仅承载基础设施配置：Ollama端点、并发守卫、文本截断。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private final Ollama ollama = new Ollama();
    private final Semaphore semaphore = new Semaphore();
    private final Prompt prompt = new Prompt();

    /** API Key 加密密钥（生产必须由 AI_CONFIG_SECRET 注入；默认值仅用于本地开发） */
    private String configSecret = "aies-dev-config-secret-change-me";

    @Getter
    @Setter
    public static class Ollama {
        /** Ollama端点（compose容器网络 http://ollama:11434；本地调试 localhost:11434） */
        private String baseUrl = "http://localhost:11434";
    }

    @Getter
    @Setter
    public static class Semaphore {
        /** LLM并发许可（本地显存约束 03-01 §三） */
        private int permits = 2;
        /** 排队超时告警阈值（秒，排队超30s打WARN，不拒绝） */
        private int queueWarnSeconds = 30;
    }

    @Getter
    @Setter
    public static class Prompt {
        /** 塞入模板前的提取文本上限（字符；保头保尾+截断标记，防7B上下文溢出） */
        private int textLimit = 12000;
    }
}
