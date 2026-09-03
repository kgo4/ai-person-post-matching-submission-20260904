-- V113: 能力评估生命周期事件日志表（幂等去重 + 审计）
-- 协调器处理每条事件时先插入本表（event_id 唯一）：
--   冲突 => 已处理过，直接跳过（幂等）；
--   成功 => 记录状态变化前后值，作为状态链路审计日志。
CREATE TABLE capability_stage_lifecycle_event_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '事件唯一ID（幂等键）',
    workflow_id BIGINT NOT NULL COMMENT '工作流ID',
    stage_run_id BIGINT NULL COMMENT '阶段运行ID',
    stage_type VARCHAR(50) NULL COMMENT '阶段类型',
    event_type VARCHAR(50) NOT NULL COMMENT '生命周期事件类型',
    source_ref_type VARCHAR(50) NULL COMMENT '来源引用类型',
    source_ref_id BIGINT NULL COMMENT '来源引用ID',
    workflow_status_before VARCHAR(50) NULL COMMENT '处理前工作流状态',
    workflow_status_after VARCHAR(50) NULL COMMENT '处理后工作流状态',
    stage_run_status_before VARCHAR(30) NULL COMMENT '处理前阶段运行状态',
    stage_run_status_after VARCHAR(30) NULL COMMENT '处理后阶段运行状态',
    handled_result VARCHAR(30) NOT NULL COMMENT '处理结果：HANDLED/SKIPPED_DUPLICATE/SKIPPED_ILLEGAL/STAGE_RUN_NOT_FOUND/FAILED',
    remark VARCHAR(1000) NULL COMMENT '备注（失败原因/跳过原因）',
    occurred_at DATETIME NULL COMMENT '事件发生时间',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',

    UNIQUE KEY uk_lifecycle_event_id (event_id),
    INDEX idx_lifecycle_wf_event (workflow_id, event_type),
    INDEX idx_lifecycle_stage_run (stage_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='能力评估生命周期事件日志（幂等去重+审计）';
