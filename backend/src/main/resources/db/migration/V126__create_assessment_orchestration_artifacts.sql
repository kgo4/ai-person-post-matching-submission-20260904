CREATE TABLE assessment_scope_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    scope_hash VARCHAR(64) NOT NULL,
    taxonomy_version VARCHAR(64) NULL,
    snapshot_json MEDIUMTEXT NOT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_assessment_scope_workflow (workflow_id),
    UNIQUE KEY uk_assessment_scope_hash (scope_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='冻结的能力评估范围';

CREATE TABLE assessment_blueprint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    scope_hash VARCHAR(64) NOT NULL,
    blueprint_json MEDIUMTEXT NOT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_assessment_blueprint_workflow (workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='能力评估测试蓝图';

CREATE TABLE assessment_evidence_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    assessment_ability_id BIGINT NOT NULL,
    canonical_tag_id BIGINT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_ref_id BIGINT NULL,
    question_id BIGINT NULL,
    evidence_text MEDIUMTEXT NOT NULL,
    score DECIMAL(10,2) NULL,
    observed_level INT NULL,
    confidence_score DECIMAL(10,2) NULL,
    evidence_status VARCHAR(30) NOT NULL,
    source_refs_json TEXT NULL,
    scope_hash VARCHAR(64) NOT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_assessment_ledger_workflow (workflow_id, assessment_ability_id),
    INDEX idx_assessment_ledger_scope (scope_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='能力评估统一证据账本';

CREATE TABLE assessment_agent_artifact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    stage_run_id BIGINT NULL,
    artifact_type VARCHAR(50) NOT NULL,
    content_json MEDIUMTEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_assessment_artifact_hash (workflow_id, artifact_type, content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 大对象产物引用';
