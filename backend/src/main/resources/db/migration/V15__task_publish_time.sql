-- 任务发布时间：发布动作发生时刻；草稿为空。
-- 历史已发布/已关闭任务以创建时间回填，避免列表出现空发布时间。
ALTER TABLE task
    ADD COLUMN publish_time DATETIME NULL COMMENT '发布时间（DRAFT 为空）' AFTER status;

UPDATE task
SET publish_time = create_time
WHERE status <> 'DRAFT'
  AND publish_time IS NULL;
