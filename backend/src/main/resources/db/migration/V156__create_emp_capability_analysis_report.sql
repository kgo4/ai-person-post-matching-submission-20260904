-- 员工全面能力分析报告快照（HR 匹配闭环 P2）。
-- 设计文档：docs/hr-matching-closed-loop-design.md 3.2 节。
-- HR 完成某员工全部待审 harness 能力项后自动聚合生成；重审出新版本，不覆盖旧版本。
-- 四区 JSON 与 person_ability_level_decision 的 decisionStatus 对应：
--   auto_passed_json      <- AUTO_CONFIRMED（harness 自动通过）
--   manual_confirmed_json <- HUMAN_CONFIRMED（人工确认通过，含审核人/时间/理由）
--   manual_rejected_json  <- REJECTED（人工拒绝，含理由）
--   final_levels_json     <- 全部能力项的最终等级
CREATE TABLE emp_capability_analysis_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    emp_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    auto_passed_json JSON NULL COMMENT 'harness 自动通过项：能力名/等级/证据链',
    manual_confirmed_json JSON NULL COMMENT '人工确认通过项：能力名/等级/审核人/审核理由',
    manual_rejected_json JSON NULL COMMENT '人工拒绝项：能力名/拒绝理由',
    final_levels_json JSON NULL COMMENT '各能力项最终等级汇总',
    summary VARCHAR(1024) NULL COMMENT '整体结论摘要',
    source_fingerprint VARCHAR(128) NOT NULL COMMENT '审核数据指纹，幂等防重',
    created_by BIGINT NULL COMMENT '触发生成的审核人（最后一条决策的 reviewedBy）',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_emp_report_version (emp_id, version_no),
    UNIQUE KEY uk_emp_report_fingerprint (emp_id, source_fingerprint),
    KEY idx_emp_report_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工全面能力分析报告快照';
