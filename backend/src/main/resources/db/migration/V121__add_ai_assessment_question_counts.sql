ALTER TABLE system_ai_model_config
    ADD COLUMN test_question_count INT NOT NULL DEFAULT 5 COMMENT 'AI测试题目数量，由系统统一控制',
    ADD COLUMN interview_question_count INT NOT NULL DEFAULT 6 COMMENT 'AI面试题目数量，由系统统一控制';
