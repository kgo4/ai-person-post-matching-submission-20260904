-- V69: AI评分状态值迁移 (PENDING_AI→PENDING, AI_FAILED→FAILED)
-- 在V68之后执行，确保新列已存在
UPDATE matching_record SET ai_scoring_status = 'PENDING'  WHERE ai_scoring_status = 'PENDING_AI';
UPDATE matching_record SET ai_scoring_status = 'FAILED'   WHERE ai_scoring_status = 'AI_FAILED';
UPDATE matching_record SET ai_scoring_status = 'SKIPPED'  WHERE ai_scoring_status IS NULL OR ai_scoring_status = '';
