-- =====================================================================
-- V19 AI当前值清理陈旧复核人（C21交互自查）
-- 背景：重新 AI 评价后 result.status=AI_DONE，但 reviewer_id/review_time
--       仍保留上一次教师复核痕迹，页面会误显示“复核教师”，与当前 AI 版本不符。
-- 口径：仅清理当前状态为 AI_DONE 的行；历史版本 evaluation_history 完整保留。
-- =====================================================================
UPDATE evaluation_result
SET reviewer_id = NULL,
    review_time = NULL
WHERE status = 'AI_DONE'
  AND (reviewer_id IS NOT NULL OR review_time IS NOT NULL);
