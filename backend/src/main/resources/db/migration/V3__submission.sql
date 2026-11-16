-- ============================================================
-- SWE-AIES Flyway V3__submission.sql（WP-2025-W5-C1/T5）
-- submission 提交表（版本化）
-- 依据：03-数据库设计 V1.2 §3.4（字段清单）+ §五（索引）+ §7.1（字典）
-- ⚠️ 登记：①parse_status 按§7.1 V1.2字典为四态（含QUEUED），§3.4原文三态以§七为准
--          ②UNIQUE(task_id,student_id,version_no) 按T5选项A补入（03-02 §3.4未定义唯一键，
--            T5核验要求同版本冲突报错），待03-02 §3.4补录
-- ============================================================

CREATE TABLE submission (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id         BIGINT       NOT NULL COMMENT '任务id（超截止→40002，应用层校验）',
    student_id      BIGINT       NOT NULL COMMENT '学生id（仅本人，IDOR防线S-05）',
    file_key        VARCHAR(128) NOT NULL COMMENT 'MinIO热bucket对象key（UUID重命名，S-07）',
    file_name       VARCHAR(128) NOT NULL COMMENT '原始文件名（展示用）',
    file_size       BIGINT       NOT NULL COMMENT '文件字节：≤20MB上限由应用层校验（→30003）；DB只做正数CHECK（T5）',
    file_type       VARCHAR(8)   NOT NULL COMMENT '字典：docx/pdf 白名单（否则→30001）',
    version_no      INT          NOT NULL COMMENT '提交版本：同任务同人递增，重复上传覆盖展示、旧版保留',
    parse_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '解析状态机字典：PENDING/QUEUED/SUCCESS/FAILED（§7.1 V1.2四态）',
    parsed_text_key VARCHAR(128) NULL COMMENT '提取文本MinIO对象key（文本不入DB，W3决策）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（截止校验基准）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_student_version (task_id, student_id, version_no),
    KEY idx_task_student (task_id, student_id),
    CONSTRAINT chk_submission_file_size CHECK (file_size > 0),
    CONSTRAINT chk_submission_file_type CHECK (file_type IN ('docx', 'pdf')),
    CONSTRAINT fk_sub_task FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT fk_sub_student FOREIGN KEY (student_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '提交记录表（版本化）';
