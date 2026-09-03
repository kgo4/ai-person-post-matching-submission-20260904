-- 治理准入记录表：所有 AI 写入正式事实表之前必须在此取得 PASS 准入凭证
CREATE TABLE IF NOT EXISTS governance_admission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admission_code VARCHAR(32) NOT NULL COMMENT '准入编码 GAD_*',
    scenario VARCHAR(64) DEFAULT NULL,
    claim_type VARCHAR(64) DEFAULT NULL,
    claim_text TEXT,
    source_type VARCHAR(64) DEFAULT NULL,
    source_ref_id BIGINT DEFAULT NULL,
    evidence_text TEXT,
    source_refs_json TEXT,
    rag_chunk_ids_json TEXT,
    matched_tag_id BIGINT DEFAULT NULL,
    similar_tag_id BIGINT DEFAULT NULL,
    legacy_support_score DECIMAL(5,2) DEFAULT NULL,
    harness_support_score DECIMAL(5,2) DEFAULT NULL,
    final_support_score DECIMAL(5,2) DEFAULT NULL,
    legacy_decision VARCHAR(16) DEFAULT NULL,
    harness_decision VARCHAR(16) DEFAULT NULL,
    final_decision VARCHAR(16) NOT NULL COMMENT 'PASS/REVIEW/BLOCK/RETRY',
    decision_rule VARCHAR(256) DEFAULT NULL,
    harness_check_code VARCHAR(64) DEFAULT NULL,
    trace_id VARCHAR(64) DEFAULT NULL,
    risk_level VARCHAR(16) DEFAULT NULL,
    is_self_evidence TINYINT DEFAULT 0,
    reason_json TEXT,
    accepted_source_refs_json TEXT,
    invalid_source_refs_json TEXT,
    missing_evidence_json TEXT,
    business_target_type VARCHAR(64) DEFAULT NULL,
    business_target_id BIGINT DEFAULT NULL,
    apply_status VARCHAR(32) DEFAULT NULL,
    context_snapshot_id BIGINT DEFAULT NULL,
    context_hash VARCHAR(128) DEFAULT NULL,
    claim_payload_json TEXT,
    review_status VARCHAR(32) DEFAULT NULL,
    review_comment VARCHAR(512) DEFAULT NULL,
    reviewed_time DATETIME DEFAULT NULL,
    created_time DATETIME DEFAULT NULL,
    UNIQUE KEY uk_admission_code (admission_code),
    KEY idx_admission_trace (trace_id),
    KEY idx_admission_final (final_decision),
    KEY idx_admission_harness_code (harness_check_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='治理准入记录';

-- post_ability_model 增加显式 AI 来源标记列（trigger 依据该列判定，不依赖 remark 内容）
ALTER TABLE post_ability_model
    ADD COLUMN source_type VARCHAR(64) DEFAULT NULL COMMENT 'AI管道来源标记（如 JD_IMPORT/POST_EVOLUTION），非AI写入为 NULL/MANUAL';

-- 正式事实表外键：governance_admission_id 必须引用存在的准入记录
ALTER TABLE emp_ability
    ADD CONSTRAINT fk_emp_ability_gov_admission
        FOREIGN KEY (governance_admission_id) REFERENCES governance_admission(id);

ALTER TABLE post_ability_model
    ADD CONSTRAINT fk_post_ability_model_gov_admission
        FOREIGN KEY (governance_admission_id) REFERENCES governance_admission(id);

-- 数据库 trigger 防御层：AI 管道来源写入正式事实表时，必须存在对应 PASS 准入记录
-- emp_ability 依据 evaluation_source 判断；post_ability_model 依据 source_type 显式标记判断。

DELIMITER $$

CREATE TRIGGER trg_emp_ability_ai_guard_insert
BEFORE INSERT ON emp_ability
FOR EACH ROW
BEGIN
    IF NEW.evaluation_source IN ('RESUME_PARSE','AI_INTERVIEW','JD_IMPORT','PMS_IMPORT','AI_PROJECT','AI_ASSESSMENT','VIDEO_INTERVIEW','INTERVIEW_OBSERVATION','POST_EVOLUTION','AI_CANDIDATE','AI_GENERATED') THEN
        IF NEW.governance_admission_id IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AI-sourced emp_ability write requires governance_admission_id';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM governance_admission WHERE id = NEW.governance_admission_id AND final_decision = 'PASS') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'governance_admission_id must reference a PASS admission';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_emp_ability_ai_guard_update
BEFORE UPDATE ON emp_ability
FOR EACH ROW
BEGIN
    IF NEW.evaluation_source IN ('RESUME_PARSE','AI_INTERVIEW','JD_IMPORT','PMS_IMPORT','AI_PROJECT','AI_ASSESSMENT','VIDEO_INTERVIEW','INTERVIEW_OBSERVATION','POST_EVOLUTION','AI_CANDIDATE','AI_GENERATED') THEN
        IF NEW.governance_admission_id IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AI-sourced emp_ability write requires governance_admission_id';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM governance_admission WHERE id = NEW.governance_admission_id AND final_decision = 'PASS') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'governance_admission_id must reference a PASS admission';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_post_ability_model_ai_guard_insert
BEFORE INSERT ON post_ability_model
FOR EACH ROW
BEGIN
    IF NEW.source_type IN ('JD_IMPORT','POST_EVOLUTION','POST_ABILITY_CHANGE','AI_CANDIDATE','AI_GENERATED','PMS_IMPORT') THEN
        IF NEW.governance_admission_id IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AI-sourced post_ability_model write requires governance_admission_id';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM governance_admission WHERE id = NEW.governance_admission_id AND final_decision = 'PASS') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'governance_admission_id must reference a PASS admission';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_post_ability_model_ai_guard_update
BEFORE UPDATE ON post_ability_model
FOR EACH ROW
BEGIN
    IF NEW.source_type IN ('JD_IMPORT','POST_EVOLUTION','POST_ABILITY_CHANGE','AI_CANDIDATE','AI_GENERATED','PMS_IMPORT') THEN
        IF NEW.governance_admission_id IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AI-sourced post_ability_model write requires governance_admission_id';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM governance_admission WHERE id = NEW.governance_admission_id AND final_decision = 'PASS') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'governance_admission_id must reference a PASS admission';
        END IF;
    END IF;
END$$

DELIMITER ;
