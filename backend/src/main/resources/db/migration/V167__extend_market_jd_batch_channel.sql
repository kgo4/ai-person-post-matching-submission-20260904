-- =============================================================================
-- V167：市场 JD 批次「来源通道」标注 + 支持按批次删除
--
-- 背景（用户反馈）：
--   1) 批次列表只列爬虫推送的批次，**手动上传/Excel 导入的批次根本不出现**，
--      运维无法在一个地方看到「系统里到底有哪些批次、各自从哪来」；
--   2) 接收到的批次无法删除，测试数据与误推数据会永久留在池子里。
--
-- 做法：
--   · market_jd_data.ingest_channel   —— 每条 JD 的入库通道（分类的唯一依据）
--   · market_jd_crawler_batch_log.ingest_channel —— 批次日志同步标注，
--     并使该表从「爬虫批次日志」升级为**统一批次登记表**（手动导入也写一行，
--     这样「重新解析」「删除」对两类批次走同一条路径，不需要两套逻辑）。
--
-- 通道取值：
--   CRAWLER       爬虫推送
--   MANUAL_UPLOAD 人工上传（粘贴文本 / Excel）
--   POST_IMPORT   岗位导入连带的市场 JD
--
-- 回填口径：
--   列默认值设置为历史数据里占比最大的 MANUAL_UPLOAD，再用 batch_log 反标爬虫批次，
--   最后按批次号前缀标出岗位导入批次。三步都可重复执行（幂等）。
--
-- 幂等性：ADD COLUMN 前先判存在；UPDATE 只改特定值，重跑结果一致。
-- =============================================================================

-- 1) market_jd_data.ingest_channel -------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'market_jd_data'
      AND COLUMN_NAME = 'ingest_channel'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE market_jd_data ADD COLUMN ingest_channel VARCHAR(32) NOT NULL DEFAULT ''MANUAL_UPLOAD'' COMMENT ''入库通道：CRAWLER=爬虫推送 / MANUAL_UPLOAD=人工上传 / POST_IMPORT=岗位导入''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) market_jd_crawler_batch_log.ingest_channel ------------------------------
--    默认 CRAWLER：该表历史上只登记爬虫批次，默认值对历史行是正确的。
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'market_jd_crawler_batch_log'
      AND COLUMN_NAME = 'ingest_channel'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE market_jd_crawler_batch_log ADD COLUMN ingest_channel VARCHAR(32) NOT NULL DEFAULT ''CRAWLER'' COMMENT ''入库通道：CRAWLER / MANUAL_UPLOAD / POST_IMPORT''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 回填历史数据 -------------------------------------------------------------
-- 3.1 已登记在批次日志里的批次 = 爬虫推送
UPDATE market_jd_data d
    JOIN market_jd_crawler_batch_log l ON l.batch_no = d.batch_no
SET d.ingest_channel = 'CRAWLER'
WHERE d.ingest_channel <> 'CRAWLER';

-- 3.2 岗位导入批次：批次号前缀可辨（`_` 需转义，否则会匹配任意单字符）
UPDATE market_jd_data
SET ingest_channel = 'POST_IMPORT'
WHERE batch_no LIKE 'POST_IMPORT\_%'
  AND ingest_channel <> 'POST_IMPORT';

-- 3.3 补齐历史批次登记行 ------------------------------------------------------
-- 改造前只有爬虫推送会写 market_jd_crawler_batch_log，人工上传/岗位导入的批次
-- 在批次列表里根本不可见（也就无法分类、无法删除）。这里为「数据已存在但没有
-- 登记行」的批次补一行，使批次列表成为完整的批次台账。
--
-- 为什么放在 SQL 而不是 Java 合并查询：登记表是唯一入口后，「重新解析」「删除」
-- 只需要一条按 batch_no 的路径，不必维护「有日志 / 无日志」两套分支。
--
-- result_status='HISTORICAL'：这些批次没有真实的接收动作，不能谎称「接收成功」。
-- created_time 取该批次最早一条数据的创建时间，避免补登记行把顺序顶到列表最前。
--
-- 幂等：LEFT JOIN 的条件是「没有登记行」，重复执行不会产生重复行。
INSERT INTO market_jd_crawler_batch_log
    (batch_no, ingest_channel, source_platform, request_ip,
     item_count, imported, updated, duplicate, failed, http_status,
     result_status, error_sample, analysis_state, analysis_note, cost_millis, created_time)
SELECT d.batch_no,
       MAX(d.ingest_channel),
       COALESCE(MAX(d.source_platform), '-'),
       NULL,
       COUNT(*),
       COUNT(*),
       0,
       0,
       0,
       200,
       'HISTORICAL',
       NULL,
       'NOT_TRIGGERED',
       '历史批次：迁移时补登记，无原始接收记录',
       NULL,
       MIN(d.created_time)
FROM market_jd_data d
         LEFT JOIN market_jd_crawler_batch_log l ON l.batch_no = d.batch_no
WHERE l.id IS NULL
  AND d.batch_no IS NOT NULL
  AND d.batch_no <> ''
GROUP BY d.batch_no;

-- 4) 批次列表按 (batch_no, ingest_channel) 过滤/统计的支撑索引 ------------------
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'market_jd_data'
      AND INDEX_NAME = 'idx_market_jd_batch_channel'
);
SET @ddl := IF(@idx_exists = 0,
    'CREATE INDEX idx_market_jd_batch_channel ON market_jd_data (batch_no, ingest_channel)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
