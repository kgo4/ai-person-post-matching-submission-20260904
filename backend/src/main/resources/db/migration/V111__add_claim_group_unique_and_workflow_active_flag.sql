-- V111: 能力聚合组唯一约束 + 活跃工作流唯一约束（生成列方案）
-- 1. 同一工作流内同名能力仅允许一个聚合组（防止重复审核与数据污染）
-- 2. 每员工仅允许一个非终态工作流（MySQL 不支持条件唯一索引，用生成列实现）

-- ============ 1. 聚合组唯一约束 ============
-- 清理历史重复组（保留最早创建的，可重复执行且无副作用）
CREATE TEMPORARY TABLE tmp_claim_group_dedup_map AS
SELECT duplicate_group.id AS duplicate_group_id, retained_group.id AS retained_group_id
FROM person_ability_claim_group duplicate_group
JOIN (
    SELECT workflow_id, normalized_ability_name, MIN(id) AS retained_group_id
    FROM person_ability_claim_group
    GROUP BY workflow_id, normalized_ability_name
) retained ON retained.workflow_id = duplicate_group.workflow_id
        AND retained.normalized_ability_name = duplicate_group.normalized_ability_name
JOIN person_ability_claim_group retained_group ON retained_group.id = retained.retained_group_id
WHERE duplicate_group.id <> retained_group.id;

UPDATE person_ability_claim claim_record
JOIN tmp_claim_group_dedup_map mapping ON mapping.duplicate_group_id = claim_record.claim_group_id
SET claim_record.claim_group_id = mapping.retained_group_id;

DELETE duplicate_decision FROM person_ability_level_decision duplicate_decision
JOIN tmp_claim_group_dedup_map mapping ON mapping.duplicate_group_id = duplicate_decision.claim_group_id
JOIN person_ability_level_decision retained_decision ON retained_decision.claim_group_id = mapping.retained_group_id;

UPDATE person_ability_level_decision decision_record
JOIN tmp_claim_group_dedup_map mapping ON mapping.duplicate_group_id = decision_record.claim_group_id
SET decision_record.claim_group_id = mapping.retained_group_id;

DELETE duplicate_item FROM ability_harness_batch_item duplicate_item
JOIN tmp_claim_group_dedup_map mapping ON mapping.duplicate_group_id = duplicate_item.claim_group_id
JOIN ability_harness_batch_item retained_item
  ON retained_item.batch_id = duplicate_item.batch_id
 AND retained_item.claim_group_id = mapping.retained_group_id;

UPDATE ability_harness_batch_item batch_item
JOIN tmp_claim_group_dedup_map mapping ON mapping.duplicate_group_id = batch_item.claim_group_id
SET batch_item.claim_group_id = mapping.retained_group_id;

DELETE duplicate_group FROM person_ability_claim_group duplicate_group
JOIN tmp_claim_group_dedup_map mapping ON mapping.duplicate_group_id = duplicate_group.id;

DROP TEMPORARY TABLE tmp_claim_group_dedup_map;

ALTER TABLE person_ability_claim_group
    ADD UNIQUE KEY uk_claim_group_workflow_ability (workflow_id, normalized_ability_name);

-- ============ 2. 活跃工作流唯一约束 ============
-- 终态（COMPLETED/FAILED/CANCELLED）时 active_flag = NULL（NULL 不参与唯一约束，允许多条历史）
-- 非终态时 active_flag = 1（配合 emp_id 唯一，保证每员工仅一个活跃流程）
ALTER TABLE person_capability_workflow
    ADD COLUMN active_flag TINYINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('COMPLETED', 'FAILED', 'CANCELLED') THEN NULL ELSE 1 END
    ) STORED COMMENT '活跃标记：非终态=1，终态=NULL（配合唯一索引限制每员工仅一个活跃流程）';

ALTER TABLE person_capability_workflow
    ADD UNIQUE KEY uk_emp_active_flag (emp_id, active_flag);
