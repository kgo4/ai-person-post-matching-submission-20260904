-- V151: layered RAG metadata.
-- MySQL remains authoritative for RAG documents; business master tables are untouched.
-- DDL is guarded because some environments may already contain the schema prepared manually.

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rag_knowledge_document' AND column_name = 'knowledge_layer') = 0, 'ALTER TABLE rag_knowledge_document ADD COLUMN knowledge_layer VARCHAR(16) NULL COMMENT ''FACT/EVIDENCE/DOMAIN/TREND''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rag_knowledge_document' AND column_name = 'business_scope') = 0, 'ALTER TABLE rag_knowledge_document ADD COLUMN business_scope VARCHAR(64) NULL COMMENT ''业务范围''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rag_knowledge_document' AND column_name = 'authority_level') = 0, 'ALTER TABLE rag_knowledge_document ADD COLUMN authority_level VARCHAR(16) NULL COMMENT ''HIGH/MEDIUM/LOW''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rag_knowledge_document' AND column_name = 'freshness_level') = 0, 'ALTER TABLE rag_knowledge_document ADD COLUMN freshness_level VARCHAR(16) NULL COMMENT ''CURRENT/AGING/EXPIRED/UNKNOWN''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rag_knowledge_document' AND column_name = 'access_scope') = 0, 'ALTER TABLE rag_knowledge_document ADD COLUMN access_scope VARCHAR(16) NULL COMMENT ''PUBLIC/INTERNAL/RESTRICTED''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rag_knowledge_document' AND column_name = 'source_origin') = 0, 'ALTER TABLE rag_knowledge_document ADD COLUMN source_origin VARCHAR(64) NULL COMMENT ''来源系统或导入渠道''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rag_query_log' AND column_name = 'allowed_layers') = 0, 'ALTER TABLE rag_query_log ADD COLUMN allowed_layers VARCHAR(128) NULL COMMENT ''场景允许层''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rag_query_log' AND column_name = 'hit_layers') = 0, 'ALTER TABLE rag_query_log ADD COLUMN hit_layers VARCHAR(128) NULL COMMENT ''实际命中层''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rag_query_log' AND column_name = 'rejected_source_types') = 0, 'ALTER TABLE rag_query_log ADD COLUMN rejected_source_types VARCHAR(512) NULL COMMENT ''被策略拒绝的来源类型''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rag_query_log' AND column_name = 'fallback_reason') = 0, 'ALTER TABLE rag_query_log ADD COLUMN fallback_reason VARCHAR(256) NULL COMMENT ''Provider 回退或过滤原因''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'rag_knowledge_document' AND index_name = 'idx_rag_doc_knowledge_layer') = 0, 'CREATE INDEX idx_rag_doc_knowledge_layer ON rag_knowledge_document (knowledge_layer, doc_status, is_deleted)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'rag_query_log' AND index_name = 'idx_rag_query_log_layers') = 0, 'CREATE INDEX idx_rag_query_log_layers ON rag_query_log (scenario, allowed_layers, hit_layers)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE rag_knowledge_document
SET knowledge_layer = CASE
    WHEN source_type IN ('POST_ABILITY_MODEL', 'ABILITY_TAG', 'JD_IMPORT', 'EMP_ABILITY', 'POST_PROTOTYPE', 'MATCHING_FACT', 'POST_FACT', 'MARKET_JD', 'RECRUITMENT_JD') THEN 'FACT'
    WHEN source_type IN ('CONTEST_EVIDENCE', 'EMP_EVIDENCE', 'INTERVIEW_EVIDENCE', 'ASSESSMENT_EVIDENCE', 'EVIDENCE', 'EVIDENCE_TRACE') THEN 'EVIDENCE'
    WHEN source_type IN ('POLICY_DOCUMENT', 'OFFICIAL_POLICY', 'OFFICIAL_DOCUMENT', 'OCCUPATION_STANDARD', 'INDUSTRY_WHITEPAPER', 'INDUSTRY_REPORT', 'MARKET_REPORT', 'ZHIHU_TREND', 'EXTERNAL_TREND', 'MARKET_TREND', 'TREND') THEN 'TREND'
    ELSE 'DOMAIN'
END
WHERE knowledge_layer IS NULL;

UPDATE rag_knowledge_document
SET business_scope = COALESCE(business_scope, knowledge_layer),
    authority_level = COALESCE(authority_level, CASE
        WHEN source_type LIKE '%OFFICIAL%' OR source_type LIKE '%POLICY%' OR source_type LIKE '%STANDARD%' THEN 'HIGH'
        WHEN source_type LIKE '%REPORT%' OR source_type LIKE '%WHITEPAPER%' THEN 'MEDIUM'
        ELSE 'LOW' END),
    freshness_level = COALESCE(freshness_level, 'UNKNOWN'),
    access_scope = COALESCE(access_scope, 'INTERNAL'),
    source_origin = COALESCE(source_origin, 'MIGRATION')
WHERE business_scope IS NULL OR authority_level IS NULL OR freshness_level IS NULL
   OR access_scope IS NULL OR source_origin IS NULL;
