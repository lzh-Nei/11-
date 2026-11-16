-- ============================================================
-- SWE-AIES Flyway V6__trace_compensation.sql（WP-2025-W8-M1 T2，W8-D4硬边界）
-- 留痕补偿表：LLD §4.3步骤6"失败→补偿日志表（重试线程补写）"的完整设计落地。
-- 【偏差登记】规约原文无该表结构（03-数据库设计 §3.x未列），按03-02建表风格自设计；
-- 承接W7-C2偏差"MVP以ERROR日志+traceId承载"的W8补建义务（04-05 §六注记同步）。
-- 红线对齐：R2留痕不阻塞主链路——本表为TraceRecorder冷桶写失败的补偿入口，
-- 定时任务（TraceCompensationService @Scheduled）重试补写至冷桶trace/{traceId}.json。
-- ============================================================

CREATE TABLE trace_compensation (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    trace_id        VARCHAR(64)  NOT NULL COMMENT '评价traceId（与冷桶对象key一一对应）',
    object_key      VARCHAR(128) NOT NULL COMMENT '冷桶对象key（trace/{traceId}.json）',
    payload         JSON         NOT NULL COMMENT '留痕JSON原文（补写用：LLM原始响应+耗时+模型版本+校验结果全量）',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '字典：PENDING/DONE（补写成功置DONE）',
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT '补写重试次数（≥10保留PENDING人工兜底）',
    last_error      VARCHAR(512) NULL COMMENT '最近一次补写失败原因（截断512）',
    next_retry_time DATETIME     NOT NULL COMMENT '下次重试时间（指数退避2^(n-1)分钟，上限60分钟）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_comp_trace (trace_id),
    KEY idx_comp_status_next (status, next_retry_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '留痕写冷桶补偿表（V6，定时任务重试补写）';
