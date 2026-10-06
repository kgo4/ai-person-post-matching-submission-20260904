-- RETRYABLE 准入记录重试机制：retry_count + next_retry_time
-- 支持 GovernedAdmissionRetryScheduler 对解析器故障期间搁浅的准入进行指数退避重试
ALTER TABLE governance_admission
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT 'RETRYABLE 已重试次数',
    ADD COLUMN next_retry_time DATETIME DEFAULT NULL COMMENT '下次重试时间（RETRYABLE 状态使用）';

CREATE INDEX idx_ga_retryable ON governance_admission (apply_status, next_retry_time);
