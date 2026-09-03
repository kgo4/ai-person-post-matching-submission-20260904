-- Backfill only names that can be recovered from an existing taxonomy tag.
-- Rows still missing both tag_id and ability_name have no trustworthy source name;
-- the API displays their model ID for manual correction instead of inventing one.
UPDATE post_ability_model pam
JOIN ability_tag atg ON atg.id = pam.tag_id
SET pam.ability_name = atg.tag_name
WHERE (pam.ability_name IS NULL OR TRIM(pam.ability_name) = '')
  AND pam.tag_id IS NOT NULL;
