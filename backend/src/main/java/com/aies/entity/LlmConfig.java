package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LLM运行参数（V4__ai_report_audit.sql §3.7 单行表；红线R4：禁止硬编码，一律本表读取）。
 *
 * <p>temperature 锁定 0（DB CHECK 约束，复现性/NFR-03一致性前置，仅展示不可改）；
 * timeout_seconds 默认60（NFR-01）；max_retry 默认3（指数退避1s/2s/4s）。</p>
 */
@Data
@TableName("llm_config")
public class LlmConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 供应商：OLLAMA/OPENAI/DEEPSEEK/DASHSCOPE/MOONSHOT/ZHIPU/CUSTOM */
    private String provider;

    /** OpenAI 兼容 Base URL（Ollama 可空，取 ai.ollama.base-url） */
    private String apiBaseUrl;

    /** AES-GCM 加密后的 API Key（密文，接口不返回） */
    private String apiKeyCipher;

    /** API Key 尾号提示 */
    private String apiKeyHint;

    /** 模型名（本地Ollama 7B，ADR-001数据不出域） */
    private String modelName;

    /** 锁定0（复现性） */
    private BigDecimal temperature;

    /** 调用超时（秒，NFR-01） */
    private Integer timeoutSeconds;

    /** Schema校验失败重试上限（指数退避1s/2s/4s） */
    private Integer maxRetry;

    private LocalDateTime updateTime;
}
