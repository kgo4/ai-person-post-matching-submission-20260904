-- =============================================================================
-- V160：全方位评估报告 —— 扩展 assessment_report 承载「AI 综合洞察」
--
-- 背景：docs/comprehensive-assessment-report-design.md
--   报告 = 既有四部分（简历证据 / AI 测试 / AI 面试 / 最终等级）+ AI 综合洞察。
--   四部分数据**早已落库**于本表（resume_/test_/interview_/aggregate_/level_summary_json），
--   本次只补「AI 洞察文字」的承载列。
--
-- 关键口径（务必遵守，否则会破坏「不重复造评分体系」的约束）：
--   · 本迁移**只加列、不加分数**：新增列全部是文字/元数据，绝无 score/level 类数值列；
--   · 既有数值列（overall_score / post_match_score / *_summary_json）一律不动、不改语义；
--   · 列名用 insight_* 而非 ai_*，表明「这是文字而非分数」；
--   · source_fingerprint 用于幂等：输入事实未变化时不重复调用 LLM。
-- =============================================================================

ALTER TABLE assessment_report
    -- AI 洞察文字（结构性保证不含分数：生成侧的输出契约里没有分数字段）
    ADD COLUMN section_insights_json MEDIUMTEXT     NULL COMMENT '各部分解读JSON：section/insight',
    ADD COLUMN strengths_json        MEDIUMTEXT     NULL COMMENT '优势项JSON（文字数组）',
    ADD COLUMN weaknesses_json       MEDIUMTEXT     NULL COMMENT '短板项JSON（文字数组）',
    ADD COLUMN risk_signals_json     MEDIUMTEXT     NULL COMMENT '风险信号JSON（文字数组）',
    ADD COLUMN suggestions_json      MEDIUMTEXT     NULL COMMENT '改进建议JSON（文字数组）',
    ADD COLUMN ai_conclusion         TEXT           NULL COMMENT '综合结论文字（AI 或模板生成；与面试侧 conclusion 区分）',
    -- 洞察元数据
    ADD COLUMN insight_source        VARCHAR(16)    NOT NULL DEFAULT 'TEMPLATE' COMMENT '洞察文字来源：AI/TEMPLATE',
    ADD COLUMN insight_model         VARCHAR(64)    NULL COMMENT '生成洞察文字的模型标识',
    ADD COLUMN insight_confidence    INT            NULL COMMENT '洞察文字可信度0-100（语义为“文字可信度”，非分数可信度）',
    ADD COLUMN insight_generated_at  DATETIME       NULL COMMENT '洞察生成时间',
    ADD COLUMN source_fingerprint    VARCHAR(64)    NULL COMMENT '输入事实指纹，用于幂等与“是否需要重算”判定';

-- 报告页按员工倒序取最近一次洞察，走覆盖索引
CREATE INDEX idx_report_emp_insight ON assessment_report (emp_id, insight_generated_at);
