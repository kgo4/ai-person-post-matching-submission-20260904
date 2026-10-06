-- V89: Knowledge Projection Consistency
-- Makes MySQL the authoritative source; Milvus/Neo4j are versioned projections

CREATE TABLE knowledge_projection_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    projection VARCHAR(32) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    target_revision BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 10,
    next_retry_time DATETIME NULL,
    lease_until DATETIME NULL,
    error_message VARCHAR(2000) NULL,
    completed_time DATETIME NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_projection_revision (projection, aggregate_type, aggregate_id, target_revision),
    KEY idx_projection_pending (projection, status, next_retry_time, id)
);

ALTER TABLE rag_knowledge_document
    ADD COLUMN content_revision BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN content_hash CHAR(64) NULL,
    ADD COLUMN indexed_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN indexing_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN indexing_error VARCHAR(2000) NULL;

ALTER TABLE rag_knowledge_chunk
    ADD COLUMN document_revision BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN is_current TINYINT NOT NULL DEFAULT 0,
    ADD KEY idx_chunk_document_revision (document_id, document_revision, is_current);

ALTER TABLE knowledge_source_document
    ADD COLUMN rag_document_id BIGINT NULL,
    ADD COLUMN storage_path VARCHAR(500) NULL,
    ADD COLUMN index_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD UNIQUE KEY uk_source_rag_document (rag_document_id);

UPDATE rag_knowledge_document
SET content_hash = SHA2(COALESCE(content, ''), 256)
WHERE content_hash IS NULL;

UPDATE rag_knowledge_document
SET indexing_status = 'INDEXED'
WHERE last_indexed_time IS NOT NULL AND indexing_status = 'PENDING';
