-- RAG 查询日志扩展：归一化分数、提供商模式、降级标记、上下文哈希
ALTER TABLE rag_query_log
    ADD COLUMN query_id VARCHAR(36) NULL COMMENT '查询唯一标识(ULID)',
    ADD COLUMN provider_mode VARCHAR(32) NULL COMMENT '提供商模式: mysql/volcengine/hybrid',
    ADD COLUMN is_degraded TINYINT(1) DEFAULT 0 COMMENT '是否使用了降级',
    ADD COLUMN requested_top_k INT NULL COMMENT '请求的topK',
    ADD COLUMN normalized_scores VARCHAR(1024) NULL COMMENT '归一化分数，逗号分隔',
    ADD COLUMN context_hash VARCHAR(64) NULL COMMENT '上下文文本的SHA-256 truncated hash',
    ADD COLUMN context_token_estimate INT NULL COMMENT '上下文估算token数';

CREATE INDEX idx_rag_query_log_query_id ON rag_query_log (query_id);
CREATE INDEX idx_rag_query_log_provider ON rag_query_log (provider_mode, scenario);
