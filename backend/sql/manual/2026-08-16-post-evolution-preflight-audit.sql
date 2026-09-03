-- Preflight audit for legal untagged role-profile abilities. These rows are not invalid;
-- the report gives operators visibility before enabling evolution comparisons.
SELECT id, post_id, ability_name, min_required_level, weight, is_core, source_type, created_time
FROM post_ability_model
WHERE is_deleted = 0
  AND tag_id IS NULL
ORDER BY post_id, id;

-- Resolve duplicate active canonical identities before V137 is applied. Keep or merge a row
-- according to business evidence; the migration intentionally refuses to choose automatically.
SELECT post_id, tag_id, COUNT(*) AS active_count, GROUP_CONCAT(id ORDER BY id) AS model_ids
FROM post_ability_model
WHERE is_deleted = 0
  AND tag_id IS NOT NULL
GROUP BY post_id, tag_id
HAVING COUNT(*) > 1;
