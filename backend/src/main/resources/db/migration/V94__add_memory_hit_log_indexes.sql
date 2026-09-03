-- V93: Add performance indexes for agent_memory_hit_log archive queries
CREATE INDEX idx_hit_log_time_id ON agent_memory_hit_log (hit_time, id);
CREATE INDEX idx_hit_log_memory_time ON agent_memory_hit_log (memory_id, hit_time);
