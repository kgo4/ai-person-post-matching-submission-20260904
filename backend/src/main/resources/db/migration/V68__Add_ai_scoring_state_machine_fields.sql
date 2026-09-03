-- V68: AI评分状态机字段
ALTER TABLE matching_record
    ADD COLUMN ai_scoring_attempt_count   INT       NULL COMMENT 'AI评分尝试次数',
    ADD COLUMN ai_scoring_last_attempt_at DATETIME  NULL COMMENT 'AI评分上次尝试时间',
    ADD COLUMN ai_scoring_next_retry_at   DATETIME  NULL COMMENT 'AI评分下次重试时间';

UPDATE matching_record SET ai_scoring_attempt_count = 0 WHERE ai_scoring_attempt_count IS NULL;
