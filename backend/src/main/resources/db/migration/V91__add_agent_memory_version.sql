-- Agent 治理记忆版本化与修订追踪
-- 更新规则时创建新行并标记旧行 SUPERSEDED，保留不可变历史。

ALTER TABLE agent_memory
    ADD COLUMN revision INT NOT NULL DEFAULT 1 COMMENT '规则修订号（初始 1）',
    ADD COLUMN supersedes_memory_id BIGINT NULL COMMENT '被本修订取代的旧记忆 ID（无则 NULL）';

CREATE INDEX idx_agent_memory_status_revision ON agent_memory (status, revision);
