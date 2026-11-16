package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.admin.AdminDtos.CreatePromptRequest;
import com.aies.dto.admin.AdminDtos.GoldenSampleVO;
import com.aies.dto.admin.AdminDtos.LlmConfigVO;
import com.aies.dto.admin.AdminDtos.LlmTestResult;
import com.aies.dto.admin.AdminDtos.PromptDetailVO;
import com.aies.dto.admin.AdminDtos.PromptTemplateVO;
import com.aies.dto.admin.AdminDtos.UpdateLlmConfigRequest;
import com.aies.entity.GoldenDataset;
import com.aies.entity.LlmConfig;
import com.aies.entity.PromptTemplate;
import com.aies.mapper.GoldenDatasetMapper;
import com.aies.mapper.LlmConfigMapper;
import com.aies.mapper.PromptTemplateMapper;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M3 管理端 AI 域配置模块（UC-33/37/38/39）。
 *
 * <p>LLM 参数（R4 红线）读写 llm_config；temperature 锁定只读。
 * Prompt 模板唯一启用版由应用层保证（启用即全局置0再置1）；新版本默认不启用。
 * 黄金数据集仅做样本启停与标注查看（新增样本需文件入库，M3+ 延后）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAiService {

    private static final String PLACEHOLDER_METRICS = "{metrics}";
    private static final String PLACEHOLDER_TEXT = "{parsed_text}";

    private final LlmConfigMapper llmConfigMapper;
    private final PromptTemplateMapper promptTemplateMapper;
    private final GoldenDatasetMapper goldenDatasetMapper;
    private final ObjectMapper objectMapper;
    private final AdminAuditService auditService;
    private final ApiKeyCipher apiKeyCipher;
    private final LlmClient llmClient;

    /* ==================== LLM 参数 ==================== */

    public List<LlmConfigVO> llmConfigs() {
        return llmConfigMapper.selectList(new LambdaQueryWrapper<LlmConfig>()
                        .orderByAsc(LlmConfig::getId))
                .stream()
                .map(cfg -> new LlmConfigVO(cfg.getId(), cfg.getProvider(),
                        cfg.getApiBaseUrl(), cfg.getModelName(), cfg.getTemperature(),
                        cfg.getTimeoutSeconds(), cfg.getMaxRetry(),
                        cfg.getApiKeyCipher() != null && !cfg.getApiKeyCipher().isBlank(),
                        cfg.getApiKeyHint(), cfg.getUpdateTime()))
                .toList();
    }

    /** 更新供应商/地址/密钥/运行参数（temperature 锁定不改；下次评价调用生效） */
    public void updateLlmConfig(long id, UpdateLlmConfigRequest request, LoginPrincipal principal) {
        LlmConfig config = llmConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "LLM 配置不存在");
        }
        LlmProvider provider;
        try {
            provider = LlmProvider.of(request.getProvider());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, e.getMessage());
        }
        String baseUrl = trimToNull(request.getApiBaseUrl());
        if (baseUrl == null && provider.defaultBaseUrl() != null) {
            baseUrl = provider.defaultBaseUrl();
        }
        if (provider.requiresKey() && baseUrl == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请填写该供应商的 Base URL");
        }
        String newKey = trimToNull(request.getApiKey());
        String cipher = config.getApiKeyCipher();
        String hint = config.getApiKeyHint();
        if (newKey != null) {
            cipher = apiKeyCipher.encrypt(newKey);
            hint = apiKeyCipher.hint(newKey);
        }
        if (provider.requiresKey() && (cipher == null || cipher.isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "该供应商需要 API Key，请填写后再保存");
        }
        llmConfigMapper.update(null, new UpdateWrapper<LlmConfig>()
                .eq("id", id)
                .set("provider", provider.name())
                .set("api_base_url", baseUrl)
                .set("api_key_cipher", cipher)
                .set("api_key_hint", hint)
                .set("model_name", request.getModelName().trim())
                .set("timeout_seconds", request.getTimeoutSeconds())
                .set("max_retry", request.getMaxRetry()));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("provider", provider.name());
        detail.put("apiBaseUrl", baseUrl);
        detail.put("modelName", request.getModelName().trim());
        detail.put("timeoutSeconds", request.getTimeoutSeconds());
        detail.put("maxRetry", request.getMaxRetry());
        detail.put("apiKeyChanged", newKey != null);
        detail.put("apiKeyHint", hint);
        auditService.record(principal, "system", "UPDATE_LLM_CONFIG", "llm_config:" + id, detail);
    }

    /** 使用已保存配置发起一次轻量真实调用（仅管理员入口；不回传密钥）。 */
    public LlmTestResult testLlmConfig(long id) {
        LlmConfig config = llmConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "LLM 配置不存在");
        }
        try {
            var raw = llmClient.evaluate("请只回复：OK", config);
            return new LlmTestResult(true, "连接成功", raw.modelVersion(), raw.elapsedMs());
        } catch (LlmInvocationException e) {
            return new LlmTestResult(false, e.getMessage(), null, 0L);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /* ==================== Prompt 模板 ==================== */

    public List<PromptTemplateVO> prompts() {
        return promptTemplateMapper.selectList(new LambdaQueryWrapper<PromptTemplate>()
                        .orderByDesc(PromptTemplate::getVersionNo))
                .stream()
                .map(template -> new PromptTemplateVO(template.getId(), template.getVersionNo(),
                        template.getEnabled(), template.getContent().length(),
                        preview(template.getContent()), template.getCreateTime()))
                .toList();
    }

    public PromptDetailVO promptDetail(long id) {
        PromptTemplate template = requirePrompt(id);
        return new PromptDetailVO(template.getId(), template.getVersionNo(), template.getEnabled(),
                template.getContent(), template.getCreateTime());
    }

    /** 新建模板版本（默认不启用；必须含两个占位符，否则评分链会断） */
    @Transactional
    public PromptDetailVO createPrompt(CreatePromptRequest request, LoginPrincipal principal) {
        String content = request.getContent().trim();
        if (!content.contains(PLACEHOLDER_METRICS) || !content.contains(PLACEHOLDER_TEXT)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "模板必须包含 {metrics} 与 {parsed_text} 两个占位符");
        }
        List<PromptTemplate> latest = promptTemplateMapper.selectList(
                new LambdaQueryWrapper<PromptTemplate>()
                        .orderByDesc(PromptTemplate::getVersionNo).last("LIMIT 1"));
        int nextVersion = latest.isEmpty() ? 1 : latest.get(0).getVersionNo() + 1;

        PromptTemplate entity = new PromptTemplate();
        entity.setVersionNo(nextVersion);
        entity.setContent(content);
        entity.setEnabled(0);
        promptTemplateMapper.insert(entity);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("versionNo", nextVersion);
        detail.put("contentLength", content.length());
        auditService.record(principal, "system", "CREATE_PROMPT", "prompt:v" + nextVersion, detail);

        return new PromptDetailVO(entity.getId(), entity.getVersionNo(), entity.getEnabled(),
                entity.getContent(), entity.getCreateTime());
    }

    /** 启用指定版本（全局唯一启用版：先全置0再置1） */
    @Transactional
    public void enablePrompt(long id, LoginPrincipal principal) {
        PromptTemplate target = requirePrompt(id);
        promptTemplateMapper.update(null, new UpdateWrapper<PromptTemplate>().set("enabled", 0));
        promptTemplateMapper.update(null, new UpdateWrapper<PromptTemplate>().eq("id", id).set("enabled", 1));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("versionNo", target.getVersionNo());
        auditService.record(principal, "system", "ENABLE_PROMPT", "prompt:v" + target.getVersionNo(), detail);
    }

    /* ==================== 黄金数据集 ==================== */

    public Page<GoldenSampleVO> goldenSamples(long page, long size) {
        Page<GoldenDataset> result = goldenDatasetMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<GoldenDataset>().orderByAsc(GoldenDataset::getId));
        List<GoldenSampleVO> records = result.getRecords().stream()
                .map(sample -> new GoldenSampleVO(sample.getId(), sample.getSampleFileKey(),
                        sample.getTeacherTotal(), parseJson(sample.getTeacherMetrics()),
                        sample.getActive(), sample.getLastConsistency()))
                .toList();
        Page<GoldenSampleVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    /** 样本启停（0=不参与一致性回归；1=参与 NFR-03 回归） */
    public void setGoldenActive(long id, int active, LoginPrincipal principal) {
        GoldenDataset sample = goldenDatasetMapper.selectById(id);
        if (sample == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "黄金样本不存在");
        }
        goldenDatasetMapper.update(null, new UpdateWrapper<GoldenDataset>().eq("id", id).set("active", active));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sampleFileKey", sample.getSampleFileKey());
        detail.put("active", active);
        auditService.record(principal, "system", active == 1 ? "ENABLE_GOLDEN" : "DISABLE_GOLDEN",
                "golden:" + id, detail);
    }

    private PromptTemplate requirePrompt(long id) {
        PromptTemplate template = promptTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Prompt 模板不存在");
        }
        return template;
    }

    private String preview(String content) {
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= 160 ? flat : flat.substring(0, 160) + "…";
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("黄金样本指标 JSON 解析失败：{}", e.getMessage());
            return null;
        }
    }
}
