-- Retain auditable memory-hit history outside the hot operational table.
CREATE TABLE IF NOT EXISTS agent_memory_hit_log_archive LIKE agent_memory_hit_log;

ALTER TABLE agent_memory_hit_log_archive
    ADD COLUMN archived_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Archive completion time';

CREATE INDEX idx_amhl_archive_hit_time ON agent_memory_hit_log_archive (hit_time, id);
