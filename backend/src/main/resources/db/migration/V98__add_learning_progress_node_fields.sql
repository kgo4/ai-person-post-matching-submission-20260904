-- 学习进度日志扩展：知识节点学习进度落库（修复 updateLearningProgress 空壳端点）

ALTER TABLE learning_progress_log
    ADD COLUMN node_id BIGINT NULL COMMENT '知识节点ID',
    ADD COLUMN progress_status VARCHAR(32) NULL COMMENT '进度状态: IN_PROGRESS/COMPLETED/ABANDONED';
