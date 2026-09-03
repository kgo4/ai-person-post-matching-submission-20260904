-- V120: 创建评估流程综合报告表
-- 一次评估（workflow）一份报告；主体面试结束后生成，聚合审核/等级确认完成后回填。

CREATE TABLE assessment_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workflow_id BIGINT NOT NULL COMMENT '关联评估工作流ID',
    emp_id BIGINT NOT NULL COMMENT '员工ID',
    post_id BIGINT NULL COMMENT '目标岗位ID',
    session_id BIGINT NULL COMMENT '面试会话ID',
    status VARCHAR(32) NOT NULL DEFAULT 'READY' COMMENT '报告状态：READY/FAILED',
    overall_score INT NULL COMMENT '综合评分0-100',
    post_match_score INT NULL COMMENT '岗位匹配度0-100',
    resume_summary_json MEDIUMTEXT NULL COMMENT '简历证据摘要JSON',
    test_summary_json MEDIUMTEXT NULL COMMENT 'AI测试结果JSON',
    interview_summary_json MEDIUMTEXT NULL COMMENT '面试观察+雷达+优势/劣势/建议JSON',
    aggregate_summary_json MEDIUMTEXT NULL COMMENT '聚合Harness审核结论JSON',
    level_summary_json MEDIUMTEXT NULL COMMENT '等级确认结论JSON',
    conclusion TEXT NULL COMMENT '综合结论',
    recommendation TEXT NULL COMMENT '建议',
    generated_at DATETIME NULL COMMENT '主体生成时间',
    completed_at DATETIME NULL COMMENT '最终定稿时间（回填完成）',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    UNIQUE KEY uk_report_workflow (workflow_id),
    INDEX idx_report_emp_id (emp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评估流程综合报告';
