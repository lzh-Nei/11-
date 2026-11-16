-- ============================================================
-- SWE-AIES Flyway V14__report_permissions.sql（WP-2025-W11-C1 报表域权限点）
-- UC-25 报表域三权限点（C1 v1.0 §一 D-36 裁决版；沿用 sys_permission 增量种子口径）：
--   report:view:teacher  教师任教任务聚合
--   report:view:student  学生个人报表（/me 端点）
--   report:refresh       缓存强制重建（UC-30 对标）
-- 管理员全局聚合=M3+ 候选（留痕：C1 §一 + 03-API 回写注记），本期不建权限点。
-- ============================================================

INSERT INTO sys_permission (id, perm_code, perm_name, module) VALUES
    (23, 'report:view:teacher', '教师报表查看', 'report'),
    (24, 'report:view:student', '学生个人报表查看', 'report'),
    (25, 'report:refresh', '报表缓存刷新', 'report');

INSERT INTO sys_role_permission (role_id, perm_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'TEACHER' AND p.perm_code IN ('report:view:teacher', 'report:refresh');

INSERT INTO sys_role_permission (role_id, perm_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'STUDENT' AND p.perm_code = 'report:view:student';
