-- ============================================================
-- 【已废弃 / DEPRECATED】请勿用于初始化当前环境！
--
-- 数据库结构与种子数据的唯一权威来源是 Flyway 迁移：
--   backend/src/main/resources/db/migration/（从 V1__rbac.sql 起）
-- 应用启动时会自动执行迁移，无需手工建表。
--
-- 本文件是早期手工建表脚本，存在以下不一致，仅作历史参考保留：
--   1) 用户表结构为 nickname/role，当前为 real_name/class_id/first_login 等；
--   2) 演示账号哈希对应旧密码 Admin@123 / Teacher@123 / Student@123，
--      当前 dev 库密码为 Passw0rd（见 docs/认证授权接口文档.md）；
--   3) 手工执行它会创建与 Flyway 不兼容的表，导致后续迁移失败。
-- ============================================================
-- 大学生软件实训教学 AI 检查评价系统
-- 认证模块建表脚本（MySQL 8.0，历史版本）
-- ============================================================

-- 显式声明脚本按 UTF-8 发送，避免 Windows 下 mysql 客户端默认 GBK 导致中文昵称乱码
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS ai_evaluate
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ai_evaluate;

-- ------------------------------------------------------------
-- 用户表
-- password 保存 BCrypt 密文（$2a$10$...，长度 60），不保存明文
-- role: admin / teacher / student
-- status: 1 正常，0 禁用
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)     NOT NULL                COMMENT '登录账号',
    password    VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 密码密文',
    phone       VARCHAR(20)     NOT NULL                COMMENT '手机号',
    nickname    VARCHAR(50)     DEFAULT NULL            COMMENT '昵称',
    avatar      VARCHAR(255)    DEFAULT NULL            COMMENT '头像地址',
    role        VARCHAR(20)     NOT NULL DEFAULT 'student' COMMENT '角色：admin/teacher/student',
    status      TINYINT         NOT NULL DEFAULT 1      COMMENT '1 正常，0 禁用',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统用户表';

-- ------------------------------------------------------------
-- 课程演示账号（生产环境请删除或首次登录后修改密码）
-- admin   / Admin@123   / 13800000000 / admin
-- teacher / Teacher@123 / 13800000001 / teacher
-- student / Student@123 / 13800000002 / student
--
-- 使用 INSERT IGNORE，脚本可重复执行，不会覆盖已存在的账号。
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_user (username, password, phone, nickname, role, status)
VALUES ('admin',
        '$2a$10$viV0l4j3XyjvcpcBWAgo2uWpJ/ENyOFHs0iduxMS3Uee1mVtpGzAO',
        '13800000000', '系统管理员', 'admin', 1),
       ('teacher',
        '$2a$10$ycU8w5mc43zzP1DZI7odo.OdBAc6alsW.uZ7TmeKysNN9HR1EuXX.',
        '13800000001', '示例教师', 'teacher', 1),
       ('student',
        '$2a$10$AVvzw4tbtfKuzlDnWmi/F.Nwa7E6IVALGh6/Qe2oNDYZPDupC4zyW',
        '13800000002', '示例学生', 'student', 1);
