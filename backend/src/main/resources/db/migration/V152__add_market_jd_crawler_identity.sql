-- Market crawler identity fields. The crawler is an external producer; these
-- fields make retries idempotent without changing the formal JD import path.
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'market_jd_data'
                 AND column_name = 'external_id') = 0,
              'ALTER TABLE market_jd_data ADD COLUMN external_id VARCHAR(191) NULL COMMENT ''源站岗位外部ID'' AFTER batch_no',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'market_jd_data'
                 AND column_name = 'source_url') = 0,
              'ALTER TABLE market_jd_data ADD COLUMN source_url VARCHAR(1000) NULL COMMENT ''源站岗位详情URL'' AFTER source_platform',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'market_jd_data'
                 AND index_name = 'uk_market_jd_source_external') = 0,
              'CREATE UNIQUE INDEX uk_market_jd_source_external ON market_jd_data (source_platform, external_id)',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
