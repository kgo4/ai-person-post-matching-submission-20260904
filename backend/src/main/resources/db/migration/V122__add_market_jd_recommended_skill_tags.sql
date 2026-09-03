ALTER TABLE market_jd_data
    ADD COLUMN recommended_skill_tags JSON NULL COMMENT 'High-confidence vector recommendation drafts; excluded from formal models' AFTER skill_tags;
