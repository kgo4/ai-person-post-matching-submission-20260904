-- 岗位演化变更项增加治理准入记录ID（写正式岗位能力模型时作为 PASS 凭证）
ALTER TABLE post_evolution_change_item
    ADD COLUMN governance_admission_id BIGINT DEFAULT NULL COMMENT '治理准入记录ID';

CREATE INDEX idx_change_item_admission ON post_evolution_change_item(governance_admission_id);
