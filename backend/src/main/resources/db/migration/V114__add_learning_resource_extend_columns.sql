-- Add extended columns to learning_resource. Historical installations created
-- the table from hrms_db.sql without these columns while the entity and UI
-- already reference them. MySQL before 8.0.29 does not support
-- ADD COLUMN IF NOT EXISTS, so each addition is guarded through
-- information_schema and dynamic SQL instead.

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'learning_resource'
       AND column_name = 'platform') = 0,
    'ALTER TABLE learning_resource ADD COLUMN platform VARCHAR(32) DEFAULT NULL COMMENT ''MOOC/BILIBILI/YOUTUBE/GITHUB/CSDN/OTHER''',
    'SELECT 1');
PREPARE learning_resource_platform_stmt FROM @sql;
EXECUTE learning_resource_platform_stmt;
DEALLOCATE PREPARE learning_resource_platform_stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'learning_resource'
       AND column_name = 'platform_icon') = 0,
    'ALTER TABLE learning_resource ADD COLUMN platform_icon VARCHAR(128) DEFAULT NULL COMMENT ''Platform icon identifier''',
    'SELECT 1');
PREPARE learning_resource_platform_icon_stmt FROM @sql;
EXECUTE learning_resource_platform_icon_stmt;
DEALLOCATE PREPARE learning_resource_platform_icon_stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'learning_resource'
       AND column_name = 'cover_image_url') = 0,
    'ALTER TABLE learning_resource ADD COLUMN cover_image_url VARCHAR(512) DEFAULT NULL COMMENT ''Cover image URL''',
    'SELECT 1');
PREPARE learning_resource_cover_image_url_stmt FROM @sql;
EXECUTE learning_resource_cover_image_url_stmt;
DEALLOCATE PREPARE learning_resource_cover_image_url_stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'learning_resource'
       AND column_name = 'duration') = 0,
    'ALTER TABLE learning_resource ADD COLUMN duration VARCHAR(64) DEFAULT NULL COMMENT ''Estimated study duration, e.g. 约8小时''',
    'SELECT 1');
PREPARE learning_resource_duration_stmt FROM @sql;
EXECUTE learning_resource_duration_stmt;
DEALLOCATE PREPARE learning_resource_duration_stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'learning_resource'
       AND column_name = 'sort_order') = 0,
    'ALTER TABLE learning_resource ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT ''Sort weight, smaller first''',
    'SELECT 1');
PREPARE learning_resource_sort_order_stmt FROM @sql;
EXECUTE learning_resource_sort_order_stmt;
DEALLOCATE PREPARE learning_resource_sort_order_stmt;

-- Keep filter indexes aligned with the new queryable columns.
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'learning_resource'
       AND index_name = 'idx_platform') = 0,
    'ALTER TABLE learning_resource ADD INDEX idx_platform(platform)',
    'SELECT 1');
PREPARE learning_resource_idx_platform_stmt FROM @sql;
EXECUTE learning_resource_idx_platform_stmt;
DEALLOCATE PREPARE learning_resource_idx_platform_stmt;
