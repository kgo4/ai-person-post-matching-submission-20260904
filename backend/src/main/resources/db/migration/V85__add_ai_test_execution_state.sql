-- AI 测试与 Excel 导入：任务执行状态机（幂等抢占 + 僵尸恢复）
-- 所有异步任务遵循 PENDING -> PROCESSING -> SUCCEEDED | FAILED
-- 数据库是任务最终事实来源，RabbitMQ 仅负责触发

-- ============ emp_ai_test：题目生成 / 评分状态机 ============
ALTER TABLE emp_ai_test
    ADD COLUMN generation_state VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '题目生成状态: PENDING/PROCESSING/SUCCEEDED/FAILED',
    ADD COLUMN evaluation_state VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '评分状态: PENDING/PROCESSING/SUCCEEDED/FAILED',
    ADD COLUMN processing_started_at DATETIME NULL COMMENT '本次处理开始时间（僵尸恢复依据）',
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    ADD COLUMN last_error_type VARCHAR(50) NULL COMMENT '最近一次错误类型，如 AI_OUTPUT_INVALID',
    ADD COLUMN last_error_message VARCHAR(500) NULL COMMENT '最近一次错误信息';

CREATE INDEX idx_emp_ai_test_gen_state ON emp_ai_test (generation_state, processing_started_at);
CREATE INDEX idx_emp_ai_test_eval_state ON emp_ai_test (evaluation_state, processing_started_at);

-- ============ post_import_batch：Excel 导入批次处理状态机 ============
ALTER TABLE post_import_batch
    ADD COLUMN processing_started_at DATETIME NULL COMMENT '本次处理开始时间（僵尸恢复依据）',
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    ADD COLUMN last_error_type VARCHAR(50) NULL COMMENT '最近一次错误类型，如 AI_OUTPUT_INVALID',
    ADD COLUMN last_error_message VARCHAR(500) NULL COMMENT '最近一次错误信息';

CREATE INDEX idx_post_import_batch_state ON post_import_batch (import_status, processing_started_at);
