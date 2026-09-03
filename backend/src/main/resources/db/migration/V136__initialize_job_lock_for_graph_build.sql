-- Initialize the database-backed lock used to serialize graph-build task creation.
-- The table is intentionally independent from Redis: a manual full rebuild must
-- remain triggerable even when the distributed cache is unavailable.

CREATE TABLE IF NOT EXISTS job_lock (
    lock_name VARCHAR(128) NOT NULL,
    locked_by VARCHAR(255) NULL,
    locked_at DATETIME NULL,
    expires_at DATETIME NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (lock_name),
    KEY idx_job_lock_expires_at (expires_at)
);

INSERT IGNORE INTO job_lock (lock_name, locked_by, locked_at, expires_at)
VALUES ('FULL_GRAPH_REBUILD', NULL, NULL, NULL);
