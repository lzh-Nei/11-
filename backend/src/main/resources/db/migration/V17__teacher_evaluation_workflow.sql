-- 教师评价工作流：提交撤回、AI 模型痕迹、学生申诉改派。
ALTER TABLE submission
    ADD COLUMN canceled TINYINT NOT NULL DEFAULT 0 COMMENT '1=学生已取消该次提交' AFTER parsed_text_key,
    ADD COLUMN cancel_time DATETIME NULL COMMENT '取消时间' AFTER canceled;

ALTER TABLE evaluation_result
    ADD COLUMN ai_model VARCHAR(64) NULL COMMENT '最近一次 AI 评价模型标识' AFTER ai_comment;

ALTER TABLE evaluation_history
    ADD COLUMN ai_model VARCHAR(64) NULL COMMENT 'AI 版本模型标识（教师版本为空）' AFTER ai_comment;

CREATE TABLE evaluation_appeal (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    evaluation_id        BIGINT       NOT NULL COMMENT '评价id',
    submission_id        BIGINT       NOT NULL COMMENT '提交id（冗余）',
    student_id           BIGINT       NOT NULL COMMENT '申诉学生',
    reason               VARCHAR(500) NOT NULL COMMENT '申诉理由',
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED',
    assigned_teacher_id  BIGINT       NULL COMMENT '随机改派教师',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_time        DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_appeal_eval (evaluation_id),
    KEY idx_appeal_teacher_status (assigned_teacher_id, status),
    CONSTRAINT fk_appeal_eval FOREIGN KEY (evaluation_id) REFERENCES evaluation_result (id),
    CONSTRAINT fk_appeal_submission FOREIGN KEY (submission_id) REFERENCES submission (id),
    CONSTRAINT fk_appeal_student FOREIGN KEY (student_id) REFERENCES sys_user (id),
    CONSTRAINT fk_appeal_teacher FOREIGN KEY (assigned_teacher_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学生评价申诉与改派复评';
