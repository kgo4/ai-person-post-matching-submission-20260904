-- ============================================================
-- V70: Agent 治理记忆闭环 — 字段扩展、状态统一、幂等去重
-- ============================================================

-- 1. agent_memory 新增字段
ALTER TABLE agent_memory
    ADD COLUMN rule_key VARCHAR(128) NULL COMMENT '规则唯一键：SHA-256(scope+memoryType+condition+action)，幂等去重',
    ADD COLUMN rule_payload_json JSON NULL COMMENT '结构化规则载荷JSON（程序执行依据）',
    ADD COLUMN rule_strength VARCHAR(16) NULL DEFAULT 'GUIDANCE' COMMENT '规则强度：HARD-程序强制执行，GUIDANCE-Prompt建议';

-- 2. agent_memory 状态统一（兼容旧值）
-- 将旧 INACTIVE → DISABLED，让新旧状态统一
UPDATE agent_memory SET status = 'DISABLED' WHERE status = 'INACTIVE';
-- 将不是明确标准状态的历史 records 设为 DRAFT
UPDATE agent_memory SET status = 'DRAFT' WHERE status NOT IN ('DRAFT', 'ACTIVE', 'DISABLED', 'SUPERSEDED', 'EXPIRED');

-- 3. rule_key 唯一索引（仅对非NULL值）
CREATE UNIQUE INDEX uk_agent_memory_rule_key ON agent_memory (rule_key);

-- 4. agent_memory_hit_log 新增字段
ALTER TABLE agent_memory_hit_log
    ADD COLUMN outcome VARCHAR(32) NULL COMMENT '执行结果：RETRIEVED_NOT_APPLIED, APPLIED_BY_AGENT, APPLIED_BY_CODE, CONFLICT_SUPERSEDED, REJECTED_BY_VALIDATION';

-- 5. 索引优化
CREATE INDEX idx_am_status_scope ON agent_memory (status, applicable_scope);
CREATE INDEX idx_am_rule_key ON agent_memory (rule_key);
CREATE INDEX idx_amhl_memory_id ON agent_memory_hit_log (memory_id);
CREATE INDEX idx_amhl_agent_hit_time ON agent_memory_hit_log (agent_name, hit_time);
