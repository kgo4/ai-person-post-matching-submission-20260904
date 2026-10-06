-- V109: AI 测试工作流关联 + 验证覆盖关系表
-- 支持基于简历 Claim 与目标岗位的验证测试，记录题目与能力主张的覆盖关系

-- ============ emp_ai_test：增加工作流关联 ============
ALTER TABLE emp_ai_test
    ADD COLUMN workflow_id BIGINT NULL COMMENT '关联的能力评估工作流ID（工作流测试专用）' AFTER post_id;

CREATE INDEX idx_emp_ai_test_workflow_id ON emp_ai_test (workflow_id);

-- ============ ai_test_coverage：测试验证覆盖关系 ============
CREATE TABLE ai_test_coverage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workflow_id BIGINT NOT NULL COMMENT '关联的工作流ID',
    claim_group_id BIGINT NOT NULL COMMENT '关联的能力聚合组ID（简历Claim待验证假设）',
    test_id BIGINT NOT NULL COMMENT '关联的测试ID',
    question_id BIGINT NULL COMMENT '题目ID（题目JSON中的索引或ID）',
    target_competency VARCHAR(200) NOT NULL COMMENT '目标能力',
    target_level INT NOT NULL COMMENT '目标等级：1-5',
    coverage_type VARCHAR(30) NOT NULL COMMENT '验证类型：MULTIPLE_CHOICE/SITUATIONAL/PRACTICAL/REASONING',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    INDEX idx_test_coverage_workflow_id (workflow_id),
    INDEX idx_test_coverage_claim_group_id (claim_group_id),
    INDEX idx_test_coverage_test_id (test_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI测试验证覆盖关系';
