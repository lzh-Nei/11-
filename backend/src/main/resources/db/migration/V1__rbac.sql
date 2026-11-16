-- ============================================================
-- SWE-AIES Flyway V1__rbac.sql
-- 五表RBAC + 内置三角色 + 权限点种子（02-04 RTM V1.2 去重清单，22个）+ 演示账号
-- 权威设计：03-数据库设计 V1.2 §3.1/3.2（字段清单），按§六翻译规则机械翻译
-- 翻译规则：ENUM→VARCHAR+应用层枚举校验；全部 ENGINE=InnoDB、utf8mb4
-- 注意：sys_user.class_id 的外键在 V2（class 建表后）补加，本脚本先建列
-- 旧 sql/schema.sql 已废弃删除，本目录为唯一建库事实源（SSOT）
-- ============================================================

-- ------------------------------------------------------------
-- 1. sys_user 用户主表
-- ------------------------------------------------------------
CREATE TABLE sys_user (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32) NOT NULL COMMENT '学号/工号，登录名',
    password    VARCHAR(72) NOT NULL COMMENT 'bcrypt哈希（强度10），不存明文',
    real_name   VARCHAR(32) NOT NULL COMMENT '姓名（PII，展示不脱敏，日志脱敏）',
    phone       VARCHAR(20) NULL COMMENT '手机号（PII，展示脱敏138****）',
    email       VARCHAR(64) NULL COMMENT '邮箱（UC-42/44前置，未绑定→30014）',
    class_id    BIGINT      NULL COMMENT '学生所属班级（外键V2补加）',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用/0禁用（禁用→40103）',
    first_login TINYINT     NOT NULL DEFAULT 1 COMMENT '首登强制改密标记 1是/0否',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户主表（RBAC五表之一）';

-- ------------------------------------------------------------
-- 2. sys_role 角色表（内置三角色）
-- ------------------------------------------------------------
CREATE TABLE sys_role (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_code   VARCHAR(20) NOT NULL COMMENT '角色编码：STUDENT/TEACHER/ADMIN',
    role_name   VARCHAR(32) NOT NULL COMMENT '角色名称',
    built_in    TINYINT     NOT NULL DEFAULT 1 COMMENT '内置角色=1不可删（UC-31负例）',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色表（内置三角色）';

-- ------------------------------------------------------------
-- 3. sys_permission 权限点表
-- ------------------------------------------------------------
CREATE TABLE sys_permission (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    perm_code   VARCHAR(64) NOT NULL COMMENT '权限点编码（与02-04 RTM一一对应，S-06）',
    perm_name   VARCHAR(64) NOT NULL COMMENT '权限点名称',
    module      VARCHAR(16) NOT NULL COMMENT '所属模块：auth/user/task/submission/evaluation/report/system',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (perm_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '权限点表';

-- ------------------------------------------------------------
-- 4. sys_user_role 用户-角色关联（用户默认单角色，应用层约束）
-- ------------------------------------------------------------
CREATE TABLE sys_user_role (
    id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户id',
    role_id BIGINT NOT NULL COMMENT '角色id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户-角色关联表';

-- ------------------------------------------------------------
-- 5. sys_role_permission 角色-权限关联（变更后Redis广播刷新权限缓存，UC-32）
-- ------------------------------------------------------------
CREATE TABLE sys_role_permission (
    id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_id BIGINT NOT NULL COMMENT '角色id',
    perm_id BIGINT NOT NULL COMMENT '权限点id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, perm_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_rp_perm FOREIGN KEY (perm_id) REFERENCES sys_permission (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色-权限关联表';

-- ============================================================
-- 种子数据 A：内置三角色
-- ============================================================
INSERT INTO sys_role (id, role_code, role_name, built_in) VALUES
    (1, 'STUDENT', '学生', 1),
    (2, 'TEACHER', '教师', 1),
    (3, 'ADMIN',   '管理员', 1);

-- ============================================================
-- 种子数据 B：权限点（02-04 RTM V1.2 去重清单，共22个）
-- ============================================================
INSERT INTO sys_permission (id, perm_code, perm_name, module) VALUES
    (1,  'auth:logout',            '退出登录',           'auth'),
    (2,  'user:me',                '查看本人信息',       'user'),
    (3,  'user:manage',            '用户管理',           'user'),
    (4,  'task:create',            '创建任务',           'task'),
    (5,  'task:publish',           '发布任务',           'task'),
    (6,  'task:edit',              '编辑任务',           'task'),
    (7,  'task:view',              '查看任务',           'task'),
    (8,  'submission:upload',      '上传提交',           'submission'),
    (9,  'submission:view',        '查看提交',           'submission'),
    (10, 'evaluation:view',        '查看评价',           'evaluation'),
    (11, 'evaluation:review',      '复核评价',           'evaluation'),
    (12, 'evaluation:re-evaluate', '触发重评',           'evaluation'),
    (13, 'evaluation:history:view','查看评价历史版本',   'evaluation'),
    (14, 'report:view',            '查看报表',           'report'),
    (15, 'report:export',          '导出报表',           'report'),
    (16, 'role:manage',            '角色管理',           'system'),
    (17, 'system:config',          '系统参数配置',       'system'),
    (18, 'class:manage',           '班级管理',           'system'),
    (19, 'audit:view',             '审计日志查询',       'system'),
    (20, 'llm:config',             'LLM参数配置',        'system'),
    (21, 'prompt:manage',          'Prompt模板管理',     'system'),
    (22, 'golden:manage',          '黄金数据集管理',     'system');

-- ============================================================
-- 种子数据 C：角色-权限映射
-- ADMIN：全部22个；TEACHER：教学链路14个；STUDENT：本人链路7个
-- （D-16：auth:logout 回归基线分配全部三角色——UC-02本为全角色用例；勾稽22/14/7=43）
-- ============================================================
-- ADMIN：全量
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.role_code = 'ADMIN';

-- TEACHER：登出 + 任务管理 + 评价链路 + 报表 + 班级
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
    ON p.perm_code IN ('auth:logout', 'user:me', 'task:create', 'task:publish', 'task:edit', 'task:view',
                       'submission:view', 'evaluation:view', 'evaluation:review',
                       'evaluation:re-evaluate', 'evaluation:history:view',
                       'report:view', 'report:export', 'class:manage')
WHERE r.role_code = 'TEACHER';

-- STUDENT：登出 + 看任务/传作业/看评价/看本人报表
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
    ON p.perm_code IN ('auth:logout', 'user:me', 'task:view', 'submission:upload',
                       'submission:view', 'evaluation:view', 'report:view')
WHERE r.role_code = 'STUDENT';

-- ============================================================
-- 种子数据 D：课程演示账号（密码与旧 schema.sql 保持一致）
--   admin   / Admin@123    教师演示可再加
--   teacher / Teacher@123
--   student / Student@123
-- 演示账号 first_login=0 免首登改密；答辩前提醒：正式环境删除或改密
-- ============================================================
INSERT INTO sys_user (id, username, password, real_name, phone, status, first_login) VALUES
    (1, 'admin',   '$2a$10$viV0l4j3XyjvcpcBWAgo2uWpJ/ENyOFHs0iduxMS3Uee1mVtpGzAO', '系统管理员', '13800000000', 1, 0),
    (2, 'teacher', '$2a$10$ycU8w5mc43zzP1DZI7odo.OdBAc6alsW.uZ7TmeKysNN9HR1EuXX.', '示例教师',   '13800000001', 1, 0),
    (3, 'student', '$2a$10$AVvzw4tbtfKuzlDnWmi/F.Nwa7E6IVALGh6/Qe2oNDYZPDupC4zyW', '示例学生',   '13800000002', 1, 0);

INSERT INTO sys_user_role (user_id, role_id) VALUES
    (1, 3),
    (2, 2),
    (3, 1);
