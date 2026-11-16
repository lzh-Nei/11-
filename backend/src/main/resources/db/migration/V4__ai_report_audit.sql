-- ============================================================
-- SWE-AIES Flyway V4__ai_report_audit.sql（WP-2025-W5-C1/T6）
-- evaluation_result / evaluation_history / report_export_task /
-- prompt_template / llm_config / golden_dataset / audit_log
-- 依据：03-数据库设计 V1.2 §3.5 / §3.6（ADR-004权威承接）/ §3.7 + §六迁移规划
-- ENUM→VARCHAR翻译规则（W3决策）；ENGINE=InnoDB、utf8mb4
-- ============================================================

-- ------------------------------------------------------------
-- 1. evaluation_result 评价当前值（每提交唯一一行，红线：只存当前值）§3.5
-- ------------------------------------------------------------
CREATE TABLE evaluation_result (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（=evaluation_history.evaluation_id）',
    submission_id      BIGINT       NOT NULL COMMENT '提交id（每提交唯一行，1:1）',
    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '评价状态机字典：PENDING/AI_DONE/REVIEWED/FAILED（§7.1，状态迁移03-04 §4.1）',
    current_version_no INT          NOT NULL COMMENT '当前生效版本号（报表统计仅读此表）',
    total_score        DECIMAL(5,2) NULL COMMENT '总分（FAILED时为NULL）',
    metric_scores      JSON         NULL COMMENT '各指标得分快照',
    ai_comment         TEXT         NULL COMMENT 'AI评语',
    reviewer_id        BIGINT       NULL COMMENT '最近复核教师',
    review_time        DATETIME     NULL COMMENT '最近复核时间',
    error_code         INT          NULL COMMENT '失败码（FAILED时记录，如40003）',
    trace_id           VARCHAR(64)  NULL COMMENT '关联冷bucket留痕（AI版本）',
    version            INT          NOT NULL DEFAULT 0 COMMENT '乐观锁@Version字段（§3.5）',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_result_submission (submission_id),
    CONSTRAINT fk_result_submission FOREIGN KEY (submission_id) REFERENCES submission (id),
    CONSTRAINT fk_result_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评价当前值表（ADR-004）';

-- ------------------------------------------------------------
-- 2. evaluation_history 评价历史版本（ADR-004：只增不改不删，审计证据链）§3.6
-- ------------------------------------------------------------
CREATE TABLE evaluation_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    evaluation_id   BIGINT       NOT NULL COMMENT '=evaluation_result.id',
    submission_id   BIGINT       NOT NULL COMMENT '提交id（冗余，提交维度可查）',
    version_no      INT          NOT NULL COMMENT '版本号：1=AI初评，2+=复核/重评',
    source          VARCHAR(20)  NOT NULL COMMENT '字典：AI_INITIAL/TEACHER_REVIEW/RE_EVALUATE（TEACHER_REVIEW时teacher_comment必填≥10字→应用层30007）',
    total_score     DECIMAL(5,2) NOT NULL COMMENT '总分快照',
    metric_scores   JSON         NOT NULL COMMENT '各维度得分快照',
    ai_comment      TEXT         NULL COMMENT 'AI评语快照',
    teacher_comment TEXT         NULL COMMENT '复核理由',
    reviewer_id     BIGINT       NULL COMMENT '教师（AI版本为NULL）',
    trace_id        VARCHAR(64)  NULL COMMENT 'AI版本关联留痕对象key',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '版本生成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_eval_version (evaluation_id, version_no),
    KEY idx_submission (submission_id),
    CONSTRAINT fk_hist_evaluation FOREIGN KEY (evaluation_id) REFERENCES evaluation_result (id),
    CONSTRAINT fk_hist_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评价历史版本表（ADR-004，只读红线：无更新/删除接口，写请求→40301）';

-- ------------------------------------------------------------
-- 3. report_export_task 导出任务（幂等载体）§3.7
-- ------------------------------------------------------------
CREATE TABLE report_export_task (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id   VARCHAR(64)  NOT NULL COMMENT '幂等键（Header Idempotency-Key，Redis SETNX TTL 10min联动）',
    requester_id BIGINT       NOT NULL COMMENT '发起人',
    filter_json  JSON         NULL COMMENT '筛选条件',
    status       VARCHAR(20)  NOT NULL DEFAULT 'QUEUED' COMMENT '字典：QUEUED/GENERATING/DONE/FAILED（ENUM→VARCHAR翻译）',
    file_key     VARCHAR(128) NULL COMMENT '结果PDF的MinIO对象key（DONE时非空）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_id (request_id),
    CONSTRAINT fk_report_requester FOREIGN KEY (requester_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '报表导出任务表';

-- ------------------------------------------------------------
-- 4. prompt_template Prompt模板（版本化）§3.7
-- 全局唯一启用（enabled=1至多一行）由应用层事务保证（UC-38负例）
-- ------------------------------------------------------------
CREATE TABLE prompt_template (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    version_no INT         NOT NULL COMMENT '模板版本号（evaluation_history经version_no逻辑关联，非FK）',
    content    TEXT        NOT NULL COMMENT '系统Prompt模板（含指标/作业内容占位符）',
    enabled    TINYINT     NOT NULL DEFAULT 0 COMMENT '1=启用（全局唯一，应用层保证）；UC-38读取启用版',
    create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_version (version_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Prompt模板版本表';

-- ------------------------------------------------------------
-- 5. llm_config LLM运行参数（单行）§3.7
-- ------------------------------------------------------------
CREATE TABLE llm_config (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（单行）',
    model_name      VARCHAR(64)   NOT NULL COMMENT '模型名（本地Ollama 7B，ADR-001数据不出域）',
    temperature     DECIMAL(2,1)  NOT NULL DEFAULT 0.0 COMMENT '锁定0（复现性，仅展示不可改，NFR-03一致性前置）',
    timeout_seconds INT           NOT NULL DEFAULT 60 COMMENT '调用超时（NFR-01）',
    max_retry       INT           NOT NULL DEFAULT 3 COMMENT 'Schema校验失败重试上限（指数退避1s/2s/4s）',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT chk_llm_temperature CHECK (temperature = 0.0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'LLM运行参数表（单行）';

-- ------------------------------------------------------------
-- 6. golden_dataset 黄金数据集样本 §3.7（NFR-03一致性回归载体，UC-39）
-- ------------------------------------------------------------
CREATE TABLE golden_dataset (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    sample_file_key  VARCHAR(128)  NOT NULL COMMENT '样本文件MinIO热bucket对象key（golden/前缀）',
    teacher_total    DECIMAL(5,2)  NOT NULL COMMENT '教师标注总分',
    teacher_metrics  JSON          NOT NULL COMMENT '教师标注各指标分',
    active           TINYINT       NOT NULL DEFAULT 1 COMMENT '1=参与回归；0=停用不参与',
    last_consistency DECIMAL(4,3)  NULL COMMENT '最近回归一致率（单样本|AI-教师|≤总分×5%判一致；总体≥70%，NFR-03）',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '黄金数据集样本表';

-- ------------------------------------------------------------
-- 7. audit_log 审计日志（仅追加）§3.7
-- S-09 不可篡改操作手册（生产由DBA执行，迁移不自动REVOKE dev账号）：
--   REVOKE UPDATE, DELETE ON ai_evaluate.audit_log FROM 'aies_app'@'%';
--   并在DB账号权限层面限制；应用仅INSERT+SELECT（UC-40）
-- ------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    operator_id BIGINT       NOT NULL COMMENT '操作人',
    module      VARCHAR(16)  NOT NULL COMMENT '模块：auth/user/task/submission/evaluation/report/system',
    action      VARCHAR(32)  NOT NULL COMMENT '动作（如LOGIN/REVIEW/EXPORT）',
    target      VARCHAR(64)  NULL COMMENT '操作对象标识',
    detail      JSON         NULL COMMENT '明细快照（脱敏后，S-08）',
    ip          VARCHAR(45)  NULL COMMENT '客户端IP（兼容IPv6）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_operator_time (operator_id, create_time),
    CONSTRAINT fk_audit_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审计日志表（仅追加，禁止UPDATE/DELETE，S-09）';

-- ------------------------------------------------------------
-- 8. 种子（§六 V4规划：默认Prompt V1 + LLM默认参数）
-- ------------------------------------------------------------
INSERT INTO prompt_template (version_no, content, enabled) VALUES
    (1, '你是软件实训作业评阅助手。请依据以下评价指标对作业文本进行评分：{metrics}。作业提取文本：{parsed_text}。仅输出JSON：{"total_score":数值,"metric_scores":{"指标名":分数},"ai_comment":"评语"}。评分须与教师历史评分风格一致（一致性回归≥70%，NFR-03）。', 1);

INSERT INTO llm_config (model_name, temperature, timeout_seconds, max_retry) VALUES
    ('qwen2.5:7b', 0.0, 60, 3);
