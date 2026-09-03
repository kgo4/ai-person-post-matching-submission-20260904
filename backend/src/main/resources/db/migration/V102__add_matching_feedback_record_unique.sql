-- M5: 反馈数据集幂等 —— 每个 matching_record_id 只保留一条"当前有效校准样本"
-- 1) 清理历史重复数据：保留每个 matching_record_id 最新一条（id 最大），其余删除
--    （校准样本为派生数据，可从 matching_record 重新生成，故删除而非归档）
--    使用派生表封装规避 MySQL 1093（目标表出现在子查询中）
DELETE FROM matching_feedback_dataset
WHERE id NOT IN (
    SELECT keep_id FROM (
        SELECT MAX(id) AS keep_id
        FROM matching_feedback_dataset
        GROUP BY matching_record_id
    ) AS keep
);

-- 2) 唯一索引：数据库层面保证每个匹配记录至多一条校准样本
ALTER TABLE matching_feedback_dataset
    ADD UNIQUE KEY uk_matching_feedback_record (matching_record_id);
