-- ============================================================
-- SWE-AIES Flyway V2__org_task.sql（WP-2025-W5-C1/T4）
-- class / task / task_metric 建表 + sys_user.class_id 外键回填 + 演示班级种子
-- 依据：03-数据库设计 V1.2 §3.3 + §8.1（ER）+ 本批已批增量（T4）
--   ①class.class_code VARCHAR(16) UNIQUE NOT NULL（UC-43班级码）
--   ②task_metric.weight 列级CHECK，跨行Σ=100由应用层事务校验→40001（03-02 §3.3勘误）
--   ③task.status 字典 DRAFT/PUBLISHED/CLOSED（ENUM→VARCHAR翻译规则）
-- ⚠️ 偏差登记：class 除class_code外字段03-02未定义，本脚本按最小必要假设
--   （class_name/teacher_id/create_time），待03-02补录后如不一致以V6+修正
-- ============================================================

-- ------------------------------------------------------------
-- 1. class 班级表（绑定教师）
-- ------------------------------------------------------------
CREATE TABLE class (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    class_name  VARCHAR(64)  NOT NULL COMMENT '班级名称（03-02待补录字段，最小假设）',
    class_code  VARCHAR(16)  NOT NULL COMMENT '班级码（UC-43注册绑定/分发）',
    teacher_id  BIGINT       NOT NULL COMMENT '绑定教师（ER §8.1：class→sys_user）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_class_code (class_code),
    CONSTRAINT fk_class_teacher FOREIGN KEY (teacher_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '班级表（绑定教师）';

-- ------------------------------------------------------------
-- 2. task 作业任务（§3.3字段清单 + T4增量③状态字典）
-- ------------------------------------------------------------
CREATE TABLE task (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title       VARCHAR(128) NOT NULL COMMENT '任务标题',
    description TEXT         NULL COMMENT '任务描述',
    teacher_id  BIGINT       NOT NULL COMMENT '发布教师（ER §8.1）',
    class_id    BIGINT       NOT NULL COMMENT '目标班级（ER §8.1）',
    deadline    DATETIME     NOT NULL COMMENT '截止时间（早于当前→30006，应用层校验）',
    status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '字典：DRAFT/PUBLISHED/CLOSED（发布后指标锁定，应用层拦截）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_teacher (teacher_id),
    KEY idx_class (class_id),
    KEY idx_deadline (deadline),
    CONSTRAINT fk_task_teacher FOREIGN KEY (teacher_id) REFERENCES sys_user (id),
    CONSTRAINT fk_task_class FOREIGN KEY (class_id) REFERENCES class (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '作业任务表';

-- ------------------------------------------------------------
-- 3. task_metric 评价指标（§3.3 + §7.1 + T4增量②CHECK）
-- 发布锁定：task.status=PUBLISHED后禁止增删改（应用层+审计留痕，UC-11）
-- ------------------------------------------------------------
CREATE TABLE task_metric (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id     BIGINT        NOT NULL COMMENT '所属任务',
    metric_name VARCHAR(64)   NOT NULL COMMENT '指标名（如正确性/完整性/规范性，2~64字符）',
    weight      DECIMAL(5,2)  NOT NULL COMMENT '指标权重：0<w≤100；单任务Σ=100应用层事务校验（≠100→40001），不做DB跨行约束（03-02 §3.3勘误）',
    sort_order  INT           NOT NULL DEFAULT 0 COMMENT '展示序',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_metric (task_id, metric_name),
    CONSTRAINT chk_metric_weight CHECK (weight > 0 AND weight <= 100),
    CONSTRAINT fk_metric_task FOREIGN KEY (task_id) REFERENCES task (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评价指标表';

-- ------------------------------------------------------------
-- 4. sys_user.class_id 外键回填（V1预留项，ER §8.1：class→sys_user 1:N）
-- ------------------------------------------------------------
ALTER TABLE sys_user
    ADD CONSTRAINT fk_user_class FOREIGN KEY (class_id) REFERENCES class (id);

-- ------------------------------------------------------------
-- 5. 种子：1个演示班级（绑定teacher账号id=2）+ student（id=3）class_id回填
--    class_code 写死演示值，供UC-43注册联调
-- ------------------------------------------------------------
INSERT INTO class (id, class_name, class_code, teacher_id) VALUES
    (1, '软件工程实训11班', 'AIES11-2025', 2);

UPDATE sys_user SET class_id = 1 WHERE id = 3;
