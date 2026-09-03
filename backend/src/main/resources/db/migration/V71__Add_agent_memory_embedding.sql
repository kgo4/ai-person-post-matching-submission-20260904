ALTER TABLE agent_memory
    ADD COLUMN embedding_vector JSON NULL COMMENT 'Agent memory semantic retrieval embedding vector';
