-- 市场JD版本快照：爬虫对同源岗位做「更新」前，先存下被替换的旧正文。
-- 主系统仍按原有批量解析流程消费 market_jd_data 的最新版本，本表只做可追溯，
-- 不参与 Agent 提取 / Harness 准入，也不影响正式岗位 JD 导入链路。
CREATE TABLE IF NOT EXISTS market_jd_data_version (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    market_jd_id      BIGINT       NOT NULL COMMENT '对应 market_jd_data.id',
    version_no        INT          NOT NULL COMMENT '版本号，从 1 开始递增',
    batch_no          VARCHAR(64)  NULL COMMENT '产生该版本的爬虫批次号',
    post_name         VARCHAR(191) NULL COMMENT '岗位名称（历史值）',
    company_name      VARCHAR(191) NULL COMMENT '公司名称（历史值）',
    city              VARCHAR(64)  NULL COMMENT '城市（历史值）',
    salary_range      VARCHAR(128) NULL COMMENT '薪资范围（历史值）',
    job_description   LONGTEXT     NULL COMMENT '岗位描述（历史值）',
    requirements      LONGTEXT     NULL COMMENT '任职要求（历史值）',
    source_url        VARCHAR(1000) NULL COMMENT '源站详情URL（历史值）',
    text_hash         VARCHAR(128) NULL COMMENT '该版本正文哈希',
    change_reason     VARCHAR(64)  NULL COMMENT '变更原因：CRAWLER_UPDATE 等',
    created_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_market_jd_version (market_jd_id, version_no),
    KEY idx_market_jd_version_created (created_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '市场JD历史版本快照';
