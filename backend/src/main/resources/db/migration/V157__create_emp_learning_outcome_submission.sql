-- 员工学习成果提交单（HR 匹配闭环 P4）。
-- 设计文档：docs/hr-matching-closed-loop-design.md 4.2 节。
--
-- 为什么新建表而不是复用 capability_closure_log：
--   capability_closure_log 是「已发生事件」的事件日志（eventType / businessKey /
--   closureStatus），且被 CapabilityClosureServiceImpl.findLog 用作幂等判定依据。
--   在其上叠加 review_status 会让「已发生的事件」带上「待审批」状态，污染事件流语义。
--   因此用独立单据承载审批态，复核通过后才回调既有 onLearningOutcomeConfirmed 回写链路。
--
-- 不建唯一键：tag_id / completed_resource_id 允许为 NULL，而 MySQL 唯一索引对 NULL 不生效，
-- 幂等改由应用层判定（同 emp_id + 同能力标识 + review_status=0 已存在则拒绝重复提交）。
CREATE TABLE emp_learning_outcome_submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    emp_id BIGINT NOT NULL COMMENT '员工档案ID',
    matching_record_id BIGINT NULL COMMENT '关联匹配记录（若有）',
    tag_id BIGINT NULL COMMENT '能力标签ID',
    ability_name VARCHAR(128) NULL COMMENT 'tagId 缺失时按名称匹配',
    completed_resource_id BIGINT NULL COMMENT '完成的学习资源ID',
    before_level TINYINT NULL COMMENT '学习前等级',
    confirmed_level TINYINT NOT NULL COMMENT '员工自评达成等级 1-5',
    note VARCHAR(512) NULL COMMENT '员工提交说明',
    ai_suggestion_id BIGINT NULL COMMENT 'AI 学习建议追溯ID',
    rag_chunk_ids VARCHAR(512) NULL COMMENT 'RAG 检索 chunkIds（JSON 数组）',
    ai_suggestion_version VARCHAR(64) NULL COMMENT 'AI 建议版本',
    review_status TINYINT NOT NULL DEFAULT 0 COMMENT '0待复核 1通过 2驳回',
    review_comment VARCHAR(512) NULL COMMENT '复核意见（驳回必填）',
    reviewed_by BIGINT NULL COMMENT '复核人用户ID',
    reviewed_time DATETIME NULL COMMENT '复核时间',
    closure_business_key VARCHAR(255) NULL COMMENT '复核通过后回写产生的闭环日志业务键',
    created_by BIGINT NULL COMMENT '提交人用户ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_los_emp_status (emp_id, review_status),
    KEY idx_los_status_created (review_status, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工学习成果提交单（HR 复核）';
