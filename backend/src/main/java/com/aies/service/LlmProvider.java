package com.aies.service;

import java.util.Locale;

/**
 * 真实大模型供应商（OpenAI 兼容协议）+ 本地 Ollama。
 */
public enum LlmProvider {

    OLLAMA("Ollama 本地模型", null, false),
    OPENAI("OpenAI", "https://api.openai.com/v1", true),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", true),
    DASHSCOPE("通义千问（ DashScope ）", "https://dashscope.aliyuncs.com/compatible-mode/v1", true),
    MOONSHOT("Moonshot Kimi", "https://api.moonshot.cn/v1", true),
    ZHIPU("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", true),
    CUSTOM("自定义 OpenAI 兼容", null, true);

    private final String label;
    private final String defaultBaseUrl;
    private final boolean requiresKey;

    LlmProvider(String label, String defaultBaseUrl, boolean requiresKey) {
        this.label = label;
        this.defaultBaseUrl = defaultBaseUrl;
        this.requiresKey = requiresKey;
    }

    public String label() {
        return label;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public boolean requiresKey() {
        return requiresKey;
    }

    public static LlmProvider of(String code) {
        if (code == null || code.isBlank()) {
            return OLLAMA;
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的模型供应商: " + code);
        }
    }
}
