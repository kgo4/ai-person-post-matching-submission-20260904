-- 岗位趋势发现：由权威材料（政府红头文件 / 政策文件 / 市场职业报告等）驱动的候选岗位解析。
-- 设计文档：docs/post-trend-discovery-redesign.md
--
-- 与「岗位演化」的分工：
--   本表负责「一次材料解析 → 多个候选（新岗位 / 能力变更）」；
--   候选落地时复用演化的能力模型校验与 AbilityChangeEvent 事件，不重复实现治理链路。
--
-- 为什么不把能力变更候选挂到 post_evolution_change_item：
--   那张表外键挂在 post_evolution_task（单一 postId）。一次趋势解析会同时影响多个不同岗位，
--   强行复用会派生出 N 个演化任务 —— N 次并发 LLM 调用、N 条任务记录、审核入口散落多处。
--   候选统一收在趋势任务下，可保持单一审核面。
CREATE TABLE post_trend_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_code VARCHAR(64) NOT NULL COMMENT '可读任务编码 TREND_xxx',
    task_name VARCHAR(255) NULL COMMENT '任务名称',
    source_document_ids VARCHAR(2048) NULL COMMENT '参与解析的知识源文档ID列表JSON',
    source_categories VARCHAR(255) NULL COMMENT '参与解析的材料类别JSON：POLICY/REPORT/STANDARD',
    task_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/RUNNING/WAIT_CONFIRM/APPLIED/PARTIALLY_APPLIED/FAILED/CANCELLED',
    progress_status VARCHAR(64) NULL COMMENT 'RETRIEVING/EXTRACTING/MATCHING/HARNESS/COMPLETED/FAILED',
    progress_percent INT NOT NULL DEFAULT 0 COMMENT '执行进度 0-100',
    candidate_count INT NOT NULL DEFAULT 0 COMMENT '候选总数',
    new_post_count INT NOT NULL DEFAULT 0 COMMENT '新岗位候选数',
    change_count INT NOT NULL DEFAULT 0 COMMENT '能力变更候选数',
    similarity_threshold DECIMAL(4,3) NULL COMMENT '本次解析使用的分流阈值快照，便于事后复核误分',
    result_summary TEXT NULL COMMENT '解析统计JSON（序列化后的字符串）',
    diagnostics VARCHAR(2048) NULL COMMENT '面向使用者的解析诊断；无候选时必须说明原因',
    error_message VARCHAR(2048) NULL,
    operator_id BIGINT NULL COMMENT '触发人用户ID',
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_by BIGINT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trend_task_code (task_code),
    KEY idx_trend_task_status (task_status, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位趋势解析任务';

CREATE TABLE post_trend_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL COMMENT '所属趋势任务ID',
    candidate_type VARCHAR(32) NOT NULL
        COMMENT 'NEW_POST 新岗位候选 / ABILITY_CHANGE 既有岗位能力变更候选',
    post_name VARCHAR(255) NOT NULL COMMENT '解析出的岗位名称',
    post_description VARCHAR(2048) NULL COMMENT '岗位描述（AI 生成草案）',
    candidate_payload TEXT NULL COMMENT '能力清单、职责、业务场景、建议等级与权重，以及标签归位结果（JSON字符串）',
    matched_post_id BIGINT NULL COMMENT '最相似的既有岗位；ABILITY_CHANGE 必有',
    matched_post_name VARCHAR(255) NULL COMMENT '最相似岗位名称快照',
    similarity_score DECIMAL(5,4) NULL COMMENT '与 matched_post 的 COSINE 相似度 0-1；空表示未取得（检索不可用）',
    emphasis_score DECIMAL(5,2) NULL COMMENT '材料内出现强度（LLM 评估 + 提及次数交叉校验）',
    source_coverage INT NULL COMMENT '不同类别材料的印证数量',
    evidence_text VARCHAR(4096) NULL COMMENT '证据原文片段',
    source_refs TEXT NULL COMMENT '来源文档ID与分块定位（JSON字符串）',
    harness_decision VARCHAR(16) NULL COMMENT '治理判定：PASS/REVIEW/BLOCK',
    risk_level VARCHAR(16) NULL COMMENT 'LOW/MEDIUM/HIGH',
    confirm_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    created_post_id BIGINT NULL COMMENT '确认创建后回写的岗位ID',
    fingerprint VARCHAR(64) NULL COMMENT '去重指纹',
    review_comment VARCHAR(500) NULL COMMENT '审核意见',
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    created_by BIGINT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，避免两个审核人覆盖同一候选的确认结果',
    PRIMARY KEY (id),
    KEY idx_trend_cand_task (task_id, confirm_status, is_deleted),
    KEY idx_trend_cand_type (task_id, candidate_type),
    KEY idx_trend_cand_fingerprint (task_id, fingerprint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位趋势候选（新岗位 / 能力变更）';
