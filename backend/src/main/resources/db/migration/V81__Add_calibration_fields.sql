ALTER TABLE matching_feedback_dataset
    ADD COLUMN calibration_template_version VARCHAR(20) DEFAULT 'v1' COMMENT '校准模板版本，固定 v1',
    ADD COLUMN calibration_source VARCHAR(40) DEFAULT NULL COMMENT '校准来源：STRUCTURED_REVIEW / MANUAL_FEEDBACK',
    ADD COLUMN export_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许导出（替代 is_used_for_training 语义）',
    ADD INDEX idx_calibration_export (export_enabled, feedback_time);
