-- H2 兼容：人员能力评估工作流（测试用）
CREATE TABLE IF NOT EXISTS person_capability_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    emp_id BIGINT NOT NULL,
    resume_parse_id BIGINT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'RESUME_REQUIRED',
    current_stage VARCHAR(50) NULL,
    active_stage_run_id BIGINT NULL,
    workflow_version INT NOT NULL DEFAULT 1,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    failed_reason VARCHAR(1000) NULL,
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS person_capability_stage_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    stage_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    input_hash VARCHAR(64) NULL,
    input_snapshot_json CLOB NULL,
    output_snapshot_json CLOB NULL,
    source_ref_type VARCHAR(50) NULL,
    source_ref_id BIGINT NULL,
    task_id VARCHAR(100) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    failure_code VARCHAR(50) NULL,
    failure_message VARCHAR(1000) NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS capability_stage_lifecycle_event_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    workflow_id BIGINT NOT NULL,
    stage_run_id BIGINT NULL,
    stage_type VARCHAR(50) NULL,
    event_type VARCHAR(50) NOT NULL,
    source_ref_type VARCHAR(50) NULL,
    source_ref_id BIGINT NULL,
    workflow_status_before VARCHAR(50) NULL,
    workflow_status_after VARCHAR(50) NULL,
    stage_run_status_before VARCHAR(30) NULL,
    stage_run_status_after VARCHAR(30) NULL,
    handled_result VARCHAR(30) NOT NULL,
    remark VARCHAR(1000) NULL,
    occurred_at TIMESTAMP NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS person_ability_level_decision (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    claim_group_id BIGINT NULL,
    tag_id BIGINT NULL,
    ability_name VARCHAR(200) NULL,
    final_level INT NULL,
    decision_status VARCHAR(50) NULL,
    decision_reason_codes_json CLOB NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
