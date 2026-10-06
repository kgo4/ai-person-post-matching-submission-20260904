-- 回填历史人工反馈为可导出的校准数据：
-- 旧 is_used_for_training=1 的反馈标记为 export_enabled=1，来源标记为 LEGACY_FEEDBACK；
-- 其他记录保持 export_enabled=0（默认不允许导出）。
UPDATE matching_feedback_dataset
SET export_enabled = 1,
    calibration_source = 'LEGACY_FEEDBACK',
    calibration_template_version = 'v1'
WHERE is_used_for_training = 1
  AND ai_match_score IS NOT NULL
  AND final_match_score IS NOT NULL;
