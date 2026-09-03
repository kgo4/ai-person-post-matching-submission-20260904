-- ============================================================
-- dev 环境手动迁移（application-dev.yml flyway.enabled=false，
-- 与 V116__add_matching_task_batch_no.sql 保持一致）
-- 说明：matching_task 增加 batch_no，建立「任务 ↔ 匹配记录批次」关联，
--      支撑「删除任务连带删除匹配记录」（任务页删除功能）。
-- 执行：mysql -uroot -proot hrms_db < 本文件
-- ============================================================
ALTER TABLE matching_task ADD COLUMN batch_no VARCHAR(64) NULL COMMENT '关联的匹配记录批次号（16位）' AFTER task_id;
CREATE INDEX idx_matching_task_batch_no ON matching_task (batch_no);
