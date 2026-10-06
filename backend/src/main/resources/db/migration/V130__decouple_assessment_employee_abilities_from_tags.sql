-- 人员评估能力与系统标签库解耦。
-- 人员最终 Harness 可写入无标准标签关联的正式能力事实。

ALTER TABLE emp_ability
    MODIFY COLUMN tag_id BIGINT NULL COMMENT '可选的系统能力标签ID',
    ADD COLUMN ability_name VARCHAR(200) NULL COMMENT '正式人员能力名称' AFTER tag_id,
    ADD COLUMN assessment_ability_id BIGINT NULL COMMENT '评估内能力身份' AFTER ability_name,
    ADD COLUMN workflow_id BIGINT NULL COMMENT '能力评估工作流ID' AFTER assessment_ability_id,
    ADD COLUMN evidence_summary_ref VARCHAR(255) NULL COMMENT '统一证据引用' AFTER workflow_id,
    ADD COLUMN harness_decision_id BIGINT NULL COMMENT '最终Harness等级决策ID' AFTER evidence_summary_ref,
    ADD UNIQUE KEY uk_emp_assessment_ability (emp_id, workflow_id, assessment_ability_id);

ALTER TABLE person_ability_profile
    MODIFY COLUMN tag_id BIGINT NULL COMMENT '可选的系统能力标签ID',
    ADD COLUMN assessment_ability_id BIGINT NULL COMMENT '评估内能力身份' AFTER ability_name,
    ADD COLUMN workflow_id BIGINT NULL COMMENT '能力评估工作流ID' AFTER assessment_ability_id,
    ADD UNIQUE KEY uk_profile_assessment_ability (emp_id, workflow_id, assessment_ability_id);
