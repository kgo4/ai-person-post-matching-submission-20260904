-- RAG 文档溯源与去重键
-- canonical_* 列用于保守去重与来源分组；embedding_* 记录向量模型溯源。

ALTER TABLE rag_knowledge_document
    ADD COLUMN canonical_content_hash VARCHAR(64) NULL COMMENT '规范化内容哈希（仅用于比较）',
    ADD COLUMN canonical_source_group VARCHAR(32) NULL COMMENT '规范化来源分组: POST_REQUIREMENT/EVIDENCE/LEARNING',
    ADD COLUMN embedding_model VARCHAR(128) NULL COMMENT '生成嵌入的模型名',
    ADD COLUMN embedding_dimension INT NULL COMMENT '嵌入维度';

ALTER TABLE rag_knowledge_chunk
    ADD COLUMN chunk_profile VARCHAR(32) NULL COMMENT '分块配置: JD/EVIDENCE/LEARNING/GENERAL';

CREATE INDEX idx_rag_doc_canonical_group_hash ON rag_knowledge_document (canonical_source_group, canonical_content_hash, is_deleted);
