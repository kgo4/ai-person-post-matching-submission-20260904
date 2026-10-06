-- V104: 人员能力评估工作流 + 阶段运行表
-- 支持按阶段推进的候选人评估主流程

-- ============ person_capability_workflow：工作流主表 ============
CREATE TABLE person_capability_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    emp_id BIGINT NOT NULL COMMENT '员工ID',
    resume_parse_id BIGINT NULL COMMENT '关联的简历解析记录ID',
    status VARCHAR(50) NOT NULL DEFAULT 'RESUME_REQUIRED' COMMENT '工作流状态',
    current_stage VARCHAR(50) NULL COMMENT '当前阶段',
    active_stage_run_id BIGINT NULL COMMENT '当前活跃的阶段运行ID',
    workflow_version INT NOT NULL DEFAULT 1 COMMENT '工作流版本号',
    started_at DATETIME NULL COMMENT '流程开始时间',
    completed_at DATETIME NULL COMMENT '流程完成时间',
    failed_reason VARCHAR(1000) NULL COMMENT '失败原因',
    created_by BIGINT NULL COMMENT '创建人ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    INDEX idx_workflow_emp_id (emp_id),
    INDEX idx_workflow_status (status),
    INDEX idx_workflow_current_stage (current_stage),
    -- 普通索引：同一员工单活跃流程由代码 getActiveWorkflow（排除终态）保证。
    -- 注意：不能是 UNIQUE (emp_id, status)——终态 COMPLETED 也纳入唯一会导致员工第二次
    -- 完成评估时 UPDATE 为 (emp_id, COMPLETED) 与历史记录冲突（Duplicate entry）。
    KEY uk_emp_active_workflow (emp_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员能力评估工作流';

-- ============ person_capability_stage_run：阶段运行表 ============
CREATE TABLE person_capability_stage_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workflow_id BIGINT NOT NULL COMMENT '关联的工作流ID',
    stage_type VARCHAR(50) NOT NULL COMMENT '阶段类型：RESUME_PARSE/RESUME_CLAIM_EXTRACTION/AI_TEST_GENERATION/AI_TEST_EVALUATION/AI_INTERVIEW/AGGREGATE_HARNESS/LEVEL_CONFIRMATION/PMS_INCREMENTAL',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '阶段状态：PENDING/RUNNING/SUCCEEDED/FAILED_RETRYABLE/FAILED_FINAL/SKIPPED/CANCELLED',
    input_hash VARCHAR(64) NULL COMMENT '输入哈希（幂等键）',
    input_snapshot_json MEDIUMTEXT NULL COMMENT '输入快照JSON',
    output_snapshot_json MEDIUMTEXT NULL COMMENT '输出快照JSON',
    source_ref_type VARCHAR(50) NULL COMMENT '来源引用类型',
    source_ref_id BIGINT NULL COMMENT '来源引用ID',
    task_id VARCHAR(100) NULL COMMENT '异步任务ID',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '尝试次数',
    started_at DATETIME NULL COMMENT '开始时间',
    completed_at DATETIME NULL COMMENT '完成时间',
    failure_code VARCHAR(50) NULL COMMENT '失败代码',
    failure_message VARCHAR(1000) NULL COMMENT '失败信息',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    INDEX idx_stage_run_workflow_id (workflow_id),
    INDEX idx_stage_run_status (status),
    INDEX idx_stage_run_type_status (stage_type, status),
    -- 幂等键：同一工作流+阶段+输入哈希唯一
    UNIQUE KEY uk_workflow_stage_input (workflow_id, stage_type, input_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员能力评估阶段运行';
