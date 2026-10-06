ALTER TABLE learning_assessment_item
    ADD COLUMN answer_text TEXT NULL COMMENT '用户提交答案' AFTER source,
    ADD COLUMN score INT NULL COMMENT '评分，0-100' AFTER answer_text,
    ADD COLUMN assessment_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PASSED/NOT_PASSED' AFTER score,
    ADD COLUMN scoring_feedback VARCHAR(1000) NULL COMMENT '评分反馈' AFTER assessment_status,
    ADD COLUMN answered_time DATETIME NULL AFTER scoring_feedback,
    ADD COLUMN scored_time DATETIME NULL AFTER answered_time,
    ADD INDEX idx_lai_step_status (step_id, assessment_status);
