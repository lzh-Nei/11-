-- =====================================================================
-- V18 评价当前值更新时间回填（C19）
-- 背景：重评/复核路径历史上只更新业务字段，未刷新 evaluation_result.update_time，
--       导致当前值时间停留在首次 AI 评价时刻，与 evaluation_history 最新版本不一致。
-- 口径：以该评价最新历史版本的 create_time 回填，仅修正落后/为空的行，不改其他字段。
-- =====================================================================
UPDATE evaluation_result er
JOIN (
    SELECT evaluation_id, MAX(create_time) AS last_history_time
    FROM evaluation_history
    GROUP BY evaluation_id
) h ON h.evaluation_id = er.id
SET er.update_time = h.last_history_time
WHERE er.update_time IS NULL
   OR er.update_time < h.last_history_time;
