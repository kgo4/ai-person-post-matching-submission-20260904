CREATE TABLE ability_tag_candidate_placement_proposal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    candidate_id BIGINT NOT NULL COMMENT 'ability_tag_candidate.id',
    proposal_version INT NOT NULL DEFAULT 1 COMMENT '编辑后递增，用于防止过期采纳',
    action VARCHAR(32) NOT NULL COMMENT 'MERGE_EXISTING or CREATE_L2',
    target_parent_domain_id BIGINT NULL COMMENT '建议或人工确认的 L1 能力域',
    target_tag_id BIGINT NULL COMMENT 'MERGE_EXISTING 的目标 L2 标签',
    confidence DECIMAL(5,4) NULL COMMENT 'Agent 建议置信度',
    rationale TEXT NULL COMMENT '建议依据',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, APPLIED, STALE, DISCARDED',
    final_tag_id BIGINT NULL COMMENT '实际挂载后的正式 L2 标签',
    applied_by BIGINT NULL,
    applied_time DATETIME NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_candidate_status (candidate_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候选能力标签的树形挂载建议';
