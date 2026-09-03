-- 为 matching_task 增加 batch_no：建立「任务 ↔ 匹配记录批次」关联，支撑按任务删除连带记录
ALTER TABLE matching_task ADD COLUMN batch_no VARCHAR(64) NULL COMMENT '关联的匹配记录批次号（16位）' AFTER task_id;
CREATE INDEX idx_matching_task_batch_no ON matching_task (batch_no);
