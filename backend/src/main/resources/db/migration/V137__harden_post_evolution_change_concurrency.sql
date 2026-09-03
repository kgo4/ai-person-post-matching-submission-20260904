-- Evolution review requires optimistic locking; active tagged model rows must be unique.
ALTER TABLE post_evolution_change_item
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version';

-- MySQL unique indexes allow multiple NULL values. This generated key preserves the legitimate
-- untagged profile rows while preventing duplicate active rows for one post/tag pair.
ALTER TABLE post_ability_model
    ADD COLUMN active_tag_identity VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE
                WHEN is_deleted = 0 AND tag_id IS NOT NULL THEN CONCAT(post_id, ':', tag_id)
                ELSE NULL
            END
        ) STORED,
    ADD UNIQUE KEY uk_post_ability_model_active_tag (active_tag_identity);
