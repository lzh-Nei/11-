-- V2：轻量通知中心（评价结果 / 教师复核 / 申诉受理 / 聊天消息）
-- 说明：V1__baseline.sql 已冻结为基线，后续结构变更一律新增迁移（禁止修改 V1）。
CREATE TABLE IF NOT EXISTS `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '接收人 sys_user.id',
  `type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'EVALUATION/REVIEW/APPEAL/CHAT/SYSTEM',
  `title` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知标题',
  `content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '正文摘要',
  `link` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端跳转路径',
  `read_flag` tinyint NOT NULL DEFAULT '0' COMMENT '0未读 1已读',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_notification_user` (`user_id`,`read_flag`,`id`),
  CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知';
