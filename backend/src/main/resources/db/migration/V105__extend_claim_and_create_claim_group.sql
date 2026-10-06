-- V105: 扩展 person_ability_claim + 创建 person_ability_claim_group
-- 支持多来源证据按能力聚合分组

-- ============ 扩展 person_ability_claim：增加工作流关联字段 ============
ALTER TABLE person_ability_claim
    ADD COLUMN workflow_id BIGINT NULL COMMENT '关联的工作流ID' AFTER is_deleted,
    ADD COLUMN stage_run_id BIGINT NULL COMMENT '关联的阶段运行ID' AFTER workflow_id,
    ADD COLUMN claim_group_id BIGINT NULL COMMENT '关联的Claim Group ID' AFTER stage_run_id,
    ADD COLUMN evidence_status VARCHAR(30) NULL DEFAULT 'COLLECTED' COMMENT '证据状态：COLLECTED/READY_FOR_AGGREGATE_HARNESS/CONFIRMED/PENDING_MANUAL_REVIEW/BLOCKED' AFTER claim_group_id,
    ADD COLUMN eligibility VARCHAR(30) NULL DEFAULT 'DISPLAY_ONLY' COMMENT '可用性：DISPLAY_ONLY/CONFIRMED/MATCH_SNAPSHOT_ONLY' AFTER evidence_status;

CREATE INDEX idx_claim_workflow_id ON person_ability_claim (workflow_id);
CREATE INDEX idx_claim_stage_run_id ON person_ability_claim (stage_run_id);
CREATE INDEX idx_claim_group_id ON person_ability_claim (claim_group_id);
CREATE INDEX idx_claim_evidence_status ON person_ability_claim (evidence_status);

-- ============ person_ability_claim_group：能力主张聚合组 ============
CREATE TABLE person_ability_claim_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workflow_id BIGINT NOT NULL COMMENT '关联的工作流ID',
    emp_id BIGINT NOT NULL COMMENT '员工ID（冗余便于查询）',
    canonical_tag_id BIGINT NULL COMMENT '规范标签ID',
    normalized_ability_name VARCHAR(200) NOT NULL COMMENT '标准化能力名称',
    tag_resolution_status VARCHAR(30) NOT NULL DEFAULT 'UNRESOLVED' COMMENT '标签解析状态：RESOLVED/TAG_CANDIDATE_PENDING/UNRESOLVED',
    status VARCHAR(30) NOT NULL DEFAULT 'COLLECTED' COMMENT '组状态：COLLECTED/READY_FOR_AGGREGATE_HARNESS/CONFIRMED/PENDING_MANUAL_REVIEW/BLOCKED',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    INDEX idx_claim_group_workflow_id (workflow_id),
    INDEX idx_claim_group_emp_id (emp_id),
    INDEX idx_claim_group_tag_id (canonical_tag_id),
    INDEX idx_claim_group_status (status),
    INDEX idx_claim_group_tag_resolution (tag_resolution_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员能力主张聚合组';
