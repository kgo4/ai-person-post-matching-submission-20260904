ALTER TABLE kg_graph_build_task
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0 COMMENT 'Retry attempts';
