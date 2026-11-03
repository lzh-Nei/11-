-- ============================================================
-- 大学生软件实训教学 AI 检查评价系统
-- 认证模块建表脚本（MySQL 8.0）
-- 执行方式：mysql --default-character-set=utf8mb4 -uroot -p < sql/schema.sql
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
