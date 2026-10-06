-- Persist the identity used by one assessment independently of the global tag catalog.
ALTER TABLE person_ability_claim_group
    ADD COLUMN assessment_ability_id BIGINT NULL COMMENT '本次评估内稳定能力ID' AFTER id,
    ADD COLUMN taxonomy_version VARCHAR(64) NULL COMMENT '能力标签树版本' AFTER canonical_tag_id,
    ADD COLUMN parent_tag_id BIGINT NULL COMMENT '审核时确认的父标签' AFTER taxonomy_version,
    ADD COLUMN taxonomy_path VARCHAR(500) NULL COMMENT '审核时确认的标签路径' AFTER parent_tag_id,
    ADD COLUMN assessable TINYINT NULL COMMENT '是否可作为评估能力' AFTER taxonomy_path,
    ADD COLUMN scope_hash VARCHAR(64) NULL COMMENT '冻结的评估范围哈希' AFTER assessable;

UPDATE person_ability_claim_group
SET assessment_ability_id = id
WHERE assessment_ability_id IS NULL;

CREATE UNIQUE INDEX uk_claim_group_assessment_ability
    ON person_ability_claim_group (workflow_id, assessment_ability_id);
CREATE INDEX idx_claim_group_scope_hash
    ON person_ability_claim_group (workflow_id, scope_hash);

ALTER TABLE person_ability_claim
    ADD COLUMN scope_hash VARCHAR(64) NULL COMMENT '证据所属冻结评估范围哈希' AFTER claim_group_id;
CREATE INDEX idx_claim_scope_hash ON person_ability_claim (workflow_id, scope_hash);
