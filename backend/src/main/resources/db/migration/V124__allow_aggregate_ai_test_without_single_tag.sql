-- Workflow tests verify a set of resume-derived tags. They do not have a
-- single primary tag; per-question bindings retain the canonical tag mapping.
ALTER TABLE emp_ai_test
    MODIFY COLUMN ability_tag_id BIGINT NULL COMMENT 'Single-tag test ability ID; NULL for aggregate workflow tests';
