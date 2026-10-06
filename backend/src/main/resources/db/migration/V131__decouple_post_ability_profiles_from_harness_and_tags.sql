-- Role capability profiles are source-evidence validated before persistence.
-- They do not require a taxonomy tag or a per-ability Harness admission.

ALTER TABLE post_ability_model
    MODIFY COLUMN tag_id BIGINT NULL COMMENT 'Optional canonical taxonomy tag ID';

ALTER TABLE post_ability_model
    ADD COLUMN ability_name VARCHAR(255) NULL COMMENT 'Source-validated role ability name';

DROP TRIGGER IF EXISTS trg_post_ability_model_ai_guard_insert;
DROP TRIGGER IF EXISTS trg_post_ability_model_ai_guard_update;
