-- Repair schema drift in historical installations. MySQL before 8.0.29 does
-- not support ADD COLUMN IF NOT EXISTS, so each addition is guarded through
-- information_schema and dynamic SQL instead.

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'matching_record'
       AND column_name = 'evidence_credibility_score') = 0,
    'ALTER TABLE matching_record ADD COLUMN evidence_credibility_score DECIMAL(5,2) DEFAULT NULL COMMENT ''Evidence credibility score, 0.00-100.00''',
    'SELECT 1');
PREPARE matching_record_schema_stmt FROM @sql;
EXECUTE matching_record_schema_stmt;
DEALLOCATE PREPARE matching_record_schema_stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'matching_record'
       AND column_name = 'evidence_coverage_score') = 0,
    'ALTER TABLE matching_record ADD COLUMN evidence_coverage_score DECIMAL(5,2) DEFAULT NULL COMMENT ''Evidence coverage score, 0.00-100.00''',
    'SELECT 1');
PREPARE matching_record_schema_stmt FROM @sql;
EXECUTE matching_record_schema_stmt;
DEALLOCATE PREPARE matching_record_schema_stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'matching_record'
       AND column_name = 'ai_scoring_status') = 0,
    'ALTER TABLE matching_record ADD COLUMN ai_scoring_status VARCHAR(20) NOT NULL DEFAULT ''SKIPPED'' COMMENT ''AI scoring status''',
    'SELECT 1');
PREPARE matching_record_schema_stmt FROM @sql;
EXECUTE matching_record_schema_stmt;
DEALLOCATE PREPARE matching_record_schema_stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'matching_record'
       AND column_name = 'ai_scoring_fail_reason') = 0,
    'ALTER TABLE matching_record ADD COLUMN ai_scoring_fail_reason VARCHAR(1000) DEFAULT NULL COMMENT ''AI scoring failure reason''',
    'SELECT 1');
PREPARE matching_record_schema_stmt FROM @sql;
EXECUTE matching_record_schema_stmt;
DEALLOCATE PREPARE matching_record_schema_stmt;
