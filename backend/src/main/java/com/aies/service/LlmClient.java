package com.aies.service;

import com.aies.entity.LlmConfig;
import com.aies.model.LlmRawResult;

/**
 * LLM唯一出口（04-05 §二契约；NFR-06可测试性关键，红线R3：禁止任何代码绕过本接口直连Ollama）。
 *
 * <p>实现类 OllamaLlmClient（生产）/ MockLlmClient（单测，R6：单测禁止依赖真实Ollama）；
 * 调用纪律：全项目仅 evaluation 域可注入。</p>
 */
public interface LlmClient {

    /**
     * 发起一次评分调用。
     *
     * @param promptText 组装完成的完整Prompt（模板+指标+提取文本）
     * @param cfg        运行参数（model/temperature/timeout，llm_config单行读取）
     * @return 原始输出（不解析内容，健壮性归 SchemaValidator）
     * @throws LlmInvocationException 网络/超时/非200统一封装（内部异常不外泄，由管道重试处置）
     */
    LlmRawResult evaluate(String promptText, LlmConfig cfg);
}
