-- 爬虫对接契约落地（对齐 MAIN_SYSTEM_INTEGRATION.md，2026-09-04）
--
-- 目的：
--   1) market_jd_data 补齐上游 §4.3 要求的字段：content_hash / first_seen_time /
--      last_updated_time / content_category，并补退化唯一键的查询索引；
--   2) 新增「推送批次处理日志」表，满足上游 §7.5「记录请求 IP、批次号、来源和处理结果」；
--   3) 新增「采集命令队列 + 本地爬虫心跳」表，落实上游 §9「服务器创建命令、本地轮询」，
--      取代原先「主系统反向访问本地 8081」的方向。
--
-- 全部语句均按 information_schema 幂等判空后执行，可重复运行。
-- 与 sql/extend_market_jd_crawler_contract.sql 内容等价，供线上手工执行。

-- ===== 1. market_jd_data 字段扩展 =====

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'market_jd_data'
                 AND column_name = 'content_hash') = 0,
              'ALTER TABLE market_jd_data ADD COLUMN content_hash VARCHAR(64) NULL COMMENT ''SHA-256(jobDescription + "\n" + requirements)，主系统生成''',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'market_jd_data'
                 AND column_name = 'first_seen_time') = 0,
              'ALTER TABLE market_jd_data ADD COLUMN first_seen_time DATETIME NULL COMMENT ''首次接收时间''',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'market_jd_data'
                 AND column_name = 'last_updated_time') = 0,
              'ALTER TABLE market_jd_data ADD COLUMN last_updated_time DATETIME NULL COMMENT ''正文最近变更时间''',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'market_jd_data'
                 AND column_name = 'content_category') = 0,
              'ALTER TABLE market_jd_data ADD COLUMN content_category VARCHAR(32) NOT NULL DEFAULT ''RECRUITMENT_JD'' COMMENT ''内容分类，爬虫 JD 固定 RECRUITMENT_JD''',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 存量行补 first_seen_time：以既有 created_time 作为首次接收时间（只补空值，可重复执行）
UPDATE market_jd_data SET first_seen_time = created_time WHERE first_seen_time IS NULL;

-- ===== 2. market_jd_data 查询索引 =====

-- 退化唯一键（externalId 缺失时的 source_platform + job_title + company_name + city）只建普通索引：
-- 历史数据可能存在同四列重复行，建唯一索引会让迁移直接失败；唯一性由服务层判定。
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'market_jd_data'
                 AND index_name = 'idx_market_jd_fallback_identity') = 0,
              'CREATE INDEX idx_market_jd_fallback_identity ON market_jd_data (source_platform, post_name, company_name, city)',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'market_jd_data'
                 AND index_name = 'idx_market_jd_content_hash') = 0,
              'CREATE INDEX idx_market_jd_content_hash ON market_jd_data (source_platform, content_hash)',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===== 3. 爬虫推送批次处理日志 =====

CREATE TABLE IF NOT EXISTS market_jd_crawler_batch_log (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    batch_no        VARCHAR(191)  NOT NULL COMMENT '爬虫推送批次号',
    source_platform VARCHAR(64)   NOT NULL COMMENT '来源平台：jd/remoteok/themuse/arbeitnow',
    request_ip      VARCHAR(64)   NULL COMMENT '调用方 IP（上游 §7.5）',
    item_count      INT           NOT NULL DEFAULT 0 COMMENT '请求 items 条数',
    imported        INT           NOT NULL DEFAULT 0 COMMENT '新增条数',
    updated         INT           NOT NULL DEFAULT 0 COMMENT '更新条数',
    duplicate       INT           NOT NULL DEFAULT 0 COMMENT '重复条数',
    failed          INT           NOT NULL DEFAULT 0 COMMENT '失败条数',
    http_status     INT           NOT NULL DEFAULT 200 COMMENT '返回的 HTTP 状态码',
    result_status   VARCHAR(32)   NOT NULL DEFAULT 'OK' COMMENT 'OK/PARTIAL_FAILED/REJECTED/UNAUTHORIZED',
    error_sample    VARCHAR(2000) NULL COMMENT '前若干条失败原因摘要（绝不含 token）',
    analysis_state  VARCHAR(32)   NOT NULL DEFAULT 'NOT_TRIGGERED' COMMENT 'NOT_TRIGGERED/QUEUED/RUNNING/SUCCEEDED/FAILED/SKIPPED',
    analysis_note   VARCHAR(500)  NULL COMMENT '解析状态说明（失败原因或跳过理由）',
    cost_millis     INT           NULL COMMENT '处理耗时（毫秒）',
    created_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_crawler_batch_log_batch (batch_no),
    KEY idx_crawler_batch_log_created (created_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '爬虫推送批次处理日志';

-- ===== 4. 采集命令队列（上游 §9） =====

CREATE TABLE IF NOT EXISTS crawler_command (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    command_id      VARCHAR(64)   NOT NULL COMMENT '命令业务ID',
    agent_id        VARCHAR(64)   NULL COMMENT '指定执行者；NULL 表示任意 agent 可领取',
    command_type    VARCHAR(32)   NOT NULL DEFAULT 'COLLECT' COMMENT '命令类型，当前仅 COLLECT',
    sources         VARCHAR(500)  NULL COMMENT '来源平台，JSON 数组',
    keywords        VARCHAR(500)  NULL COMMENT '关键词，JSON 数组',
    cities          VARCHAR(500)  NULL COMMENT '城市，JSON 数组',
    max_items       INT           NULL COMMENT '单次抓取上限',
    status          VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DISPATCHED/RUNNING/SUCCEEDED/FAILED/EXPIRED/CANCELLED',
    payload         VARCHAR(2000) NULL COMMENT '下发给爬虫的完整命令体，JSON',
    result_json     VARCHAR(2000) NULL COMMENT '爬虫回传的执行结果，JSON',
    error_message   VARCHAR(500)  NULL COMMENT '失败原因',
    requested_by    BIGINT        NULL COMMENT '发起人 sys_user.id',
    created_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    dispatched_time DATETIME      NULL COMMENT '被 agent 领取时间',
    finished_time   DATETIME      NULL COMMENT '执行结束时间',
    expire_time     DATETIME      NULL COMMENT '过期时间，超时未领取转 EXPIRED',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crawler_command_id (command_id),
    KEY idx_crawler_command_status (status, created_time),
    KEY idx_crawler_command_agent (agent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '主系统下发给本地爬虫的采集命令';

-- ===== 5. 本地爬虫心跳台账 =====

CREATE TABLE IF NOT EXISTS crawler_agent (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    agent_id            VARCHAR(64)  NOT NULL COMMENT '本地爬虫实例标识',
    agent_name          VARCHAR(128) NULL COMMENT '实例名称',
    host_info           VARCHAR(255) NULL COMMENT '主机/版本等描述信息',
    agent_version       VARCHAR(64)  NULL COMMENT '爬虫版本号',
    last_heartbeat_time DATETIME     NULL COMMENT '最近一次心跳时间',
    last_command_id     VARCHAR(64)  NULL COMMENT '最近领取的命令ID',
    created_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time        DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crawler_agent_id (agent_id),
    KEY idx_crawler_agent_heartbeat (last_heartbeat_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '本地爬虫心跳台账';
