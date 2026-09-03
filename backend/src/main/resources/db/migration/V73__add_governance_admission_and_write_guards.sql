ALTER TABLE ai_harness_check_log
    ADD COLUMN legacy_support_score DECIMAL(5,2) DEFAULT NULL COMMENT '原支持分数',
    ADD COLUMN legacy_decision VARCHAR(14) DEFAULT NULL COMMENT '原决策',
    ADD COLUMN decision_rule VARCHAR(512) DEFAULT NULL COMMENT '决策合并规则',
    ADD COLUMN trace_id VARCHAR(64) DEFAULT NULL COMMENT '追踪ID';

ALTER TABLE ai_hallucination_check
    ADD COLUMN legacy_support_score DECIMAL(5,2) DEFAULT NULL COMMENT '原支持分数',
    ADD COLUMN harness_support_score DECIMAL(5,2) DEFAULT NULL COMMENT 'Harness支持分数',
    ADD COLUMN final_support_score DECIMAL(5,2) DEFAULT NULL COMMENT '最终支持分数',
    ADD COLUMN legacy_decision VARCHAR(14) DEFAULT NULL COMMENT '原决策',
    ADD COLUMN harness_decision VARCHAR(14) DEFAULT NULL COMMENT 'Harness决策',
    ADD COLUMN final_decision VARCHAR(14) DEFAULT NULL COMMENT '最终决策',
    ADD COLUMN decision_rule VARCHAR(512) DEFAULT NULL COMMENT '决策合并规则',
    ADD COLUMN harness_check_code VARCHAR(64) DEFAULT NULL COMMENT 'Harness检查编码',
    ADD COLUMN trace_id VARCHAR(64) DEFAULT NULL COMMENT '追踪ID';

ALTER TABLE prompt_invocation_log
    ADD COLUMN trace_id VARCHAR(64) DEFAULT NULL COMMENT '追踪ID';

ALTER TABLE emp_ability
    ADD COLUMN governance_admission_id BIGINT DEFAULT NULL COMMENT '治理准入记录ID';

ALTER TABLE post_ability_model
    ADD COLUMN governance_admission_id BIGINT DEFAULT NULL COMMENT '治理准入记录ID';

CREATE INDEX idx_harness_trace_id ON ai_harness_check_log(trace_id);
CREATE INDEX idx_hallucination_trace_id ON ai_hallucination_check(trace_id);
CREATE INDEX idx_prompt_trace_id ON prompt_invocation_log(trace_id);
