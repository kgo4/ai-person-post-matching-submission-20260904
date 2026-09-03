-- V107: 创建最终能力等级决策表
-- 最终能力等级确认中心的决策记录，保存完整策略快照

CREATE TABLE person_ability_level_decision (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workflow_id BIGINT NOT NULL COMMENT '关联的工作流ID',
    claim_group_id BIGINT NOT NULL COMMENT '关联的Claim Group ID',
    emp_id BIGINT NOT NULL COMMENT '员工ID',
    tag_id BIGINT NULL COMMENT '能力标签ID',
    decision_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_MANUAL_REVIEW' COMMENT '决策状态：AUTO_CONFIRMED/PENDING_MANUAL_REVIEW/HUMAN_CONFIRMED/REJECTED',
    final_level INT NULL COMMENT '最终等级：1-5',
    final_confidence INT NULL COMMENT '最终置信度：0-100',
    review_state VARCHAR(30) NULL COMMENT '审核状态：AUTO/PENDING/APPROVED/REJECTED',
    policy_version VARCHAR(50) NULL COMMENT '策略版本号',
    policy_snapshot_json MEDIUMTEXT NULL COMMENT '策略快照JSON',
    source_breakdown_json JSON NULL COMMENT '来源分解JSON',
    effective_weight_breakdown_json JSON NULL COMMENT '有效权重分解JSON',
    conflict_signals_json JSON NULL COMMENT '冲突信号JSON',
    decision_reason_codes_json JSON NULL COMMENT '决策原因码JSON',
    reviewed_by BIGINT NULL COMMENT '审核人ID',
    reviewed_time DATETIME NULL COMMENT '审核时间',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    INDEX idx_level_decision_workflow_id (workflow_id),
    INDEX idx_level_decision_emp_id (emp_id),
    INDEX idx_level_decision_tag_id (tag_id),
    INDEX idx_level_decision_claim_group_id (claim_group_id),
    INDEX idx_level_decision_status (decision_status),
    -- 同一 Claim Group 只允许一个决策记录
    UNIQUE KEY uk_decision_claim_group (claim_group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员能力等级决策';
