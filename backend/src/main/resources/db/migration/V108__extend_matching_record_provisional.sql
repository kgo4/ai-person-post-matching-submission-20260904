-- V108: 扩展 matching_record 增加临时能力匹配字段
-- 支持强制匹配时的临时能力快照和风险标记持久化

ALTER TABLE matching_record
    ADD COLUMN used_provisional_abilities TINYINT(1) NULL DEFAULT 0 COMMENT '是否使用了临时能力：0-否，1-是' AFTER ai_scoring_next_retry_at,
    ADD COLUMN provisional_ability_count INT NULL DEFAULT 0 COMMENT '临时能力数量' AFTER used_provisional_abilities,
    ADD COLUMN provisional_snapshot_json MEDIUMTEXT NULL COMMENT '临时能力快照JSON' AFTER provisional_ability_count,
    ADD COLUMN provisional_risk_flags_json JSON NULL COMMENT '临时能力风险标记JSON' AFTER provisional_snapshot_json;

CREATE INDEX idx_matching_provisional ON matching_record (used_provisional_abilities);
