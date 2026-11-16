-- 真实大模型供应商配置：供应商、OpenAI 兼容地址、加密 API Key。
-- API Key 仅存密文与尾号提示，接口永不回传明文。
ALTER TABLE llm_config
    ADD COLUMN provider VARCHAR(32) NOT NULL DEFAULT 'OLLAMA'
        COMMENT '供应商：OLLAMA/OPENAI/DEEPSEEK/DASHSCOPE/MOONSHOT/ZHIPU/CUSTOM' AFTER id,
    ADD COLUMN api_base_url VARCHAR(255) NULL
        COMMENT 'OpenAI 兼容 Base URL（OLLAMA 可空，取 ai.ollama.base-url）' AFTER provider,
    ADD COLUMN api_key_cipher VARCHAR(1024) NULL
        COMMENT 'AES-GCM 加密后的 API Key（密文）' AFTER api_base_url,
    ADD COLUMN api_key_hint VARCHAR(8) NULL
        COMMENT 'API Key 尾号提示（仅展示用）' AFTER api_key_cipher;

UPDATE llm_config SET provider = 'OLLAMA' WHERE provider IS NULL OR provider = '';
