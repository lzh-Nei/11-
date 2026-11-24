-- V3：后端闭环修复（系统参数、异步导出、申诉处理）
-- 说明：V1__baseline.sql 已冻结为基线，后续结构变更一律新增迁移（禁止修改 V1）。
CREATE TABLE IF NOT EXISTS `sys_config` (
  `config_key` varchar(64) NOT NULL COMMENT '配置键',
  `config_value` varchar(512) NOT NULL COMMENT '配置值（字符串存储，应用侧校验类型）',
  `description` varchar(128) DEFAULT NULL COMMENT '说明',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行期可调参数';

INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
  ('upload.maxFileMb', '20', '单文件上传上限(MB)，1-20'),
  ('report.cacheTtlMinutes', '10', '报表缓存TTL(分钟)，1-60')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);

ALTER TABLE `report_export_task`
  ADD COLUMN `file_name` varchar(160) DEFAULT NULL COMMENT '导出文件名' AFTER `file_key`,
  ADD COLUMN `error_msg` varchar(500) DEFAULT NULL COMMENT '失败原因' AFTER `file_name`,
  ADD COLUMN `finish_time` datetime DEFAULT NULL COMMENT '完成时间' AFTER `error_msg`;

ALTER TABLE `evaluation_appeal`
  ADD COLUMN `reject_reason` varchar(500) DEFAULT NULL COMMENT '驳回理由' AFTER `resolved_time`,
  ADD COLUMN `handled_time` datetime DEFAULT NULL COMMENT '处理时间' AFTER `reject_reason`;
