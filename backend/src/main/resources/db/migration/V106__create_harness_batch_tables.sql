-- V106: 创建聚合 Harness 审核批次表
-- 面试完成后按能力聚合证据，执行一次批量 Harness 审核

-- ============ ability_harness_batch：聚合审核批次 ============
CREATE TABLE ability_harness_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workflow_id BIGINT NOT NULL COMMENT '关联的工作流ID',
    batch_type VARCHAR(50) NOT NULL COMMENT '批次类型',
    model_config_snapshot MEDIUMTEXT NULL COMMENT '模型配置快照JSON',
    request_hash VARCHAR(64) NULL COMMENT '请求哈希',
    request_snapshot_json MEDIUMTEXT NULL COMMENT '请求快照JSON',
    response_snapshot_json MEDIUMTEXT NULL COMMENT '响应快照JSON',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '批次状态',
    started_at DATETIME NULL COMMENT '开始时间',
    completed_at DATETIME NULL COMMENT '完成时间',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    INDEX idx_harness_batch_workflow_id (workflow_id),
    INDEX idx_harness_batch_status (status),
    UNIQUE KEY uk_harness_batch_request (workflow_id, batch_type, request_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聚合Harness审核批次';

-- ============ ability_harness_batch_item：审核批次项 ============
CREATE TABLE ability_harness_batch_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    batch_id BIGINT NOT NULL COMMENT '关联的批次ID',
    claim_group_id BIGINT NOT NULL COMMENT '关联的Claim Group ID',
    decision VARCHAR(20) NOT NULL COMMENT '决策：PASS/REVIEW/BLOCK',
    ability_supported TINYINT(1) NULL COMMENT '能力是否得到支持',
    supported_level_ceiling INT NULL COMMENT '支持的等级上限：1-5',
    risk_level VARCHAR(20) NULL COMMENT '风险等级',
    reason_codes_json JSON NULL COMMENT '原因码JSON数组',
    evidence_refs_json JSON NULL COMMENT '证据引用JSON数组',
    harness_log_id BIGINT NULL COMMENT '关联的AiHarnessCheckLog ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    INDEX idx_harness_item_batch_id (batch_id),
    INDEX idx_harness_item_claim_group_id (claim_group_id),
    INDEX idx_harness_item_decision (decision),
    UNIQUE KEY uk_harness_item_batch_group (batch_id, claim_group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聚合Harness审核批次项';
