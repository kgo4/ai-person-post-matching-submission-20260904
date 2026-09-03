-- Idempotently seed learning resources for the latest imported posts (28-32).
-- Ability names from post_ability_model are the authoritative source;
-- tag_id is intentionally left NULL because it is optional metadata.
INSERT INTO learning_resource
    (resource_code, ability_name, tag_id, title, resource_type, difficulty_level,
     url, description, platform, platform_icon, duration, sort_order, status)
SELECT
    CONCAT('POST能力_', SUBSTRING(MD5(TRIM(pam.ability_name)), 1, 20)),
    TRIM(pam.ability_name),
    NULL,
    CONCAT('学习路径：', TRIM(pam.ability_name)),
    CASE
        WHEN LOWER(pam.ability_name) REGEXP 'java|spring|kafka|redis|mysql|flink|mqtt|netty|websocket|influx|python'
            THEN 'COURSE'
        ELSE 'DOC'
    END,
    CASE WHEN pam.min_required_level >= 4 THEN 4 ELSE 3 END,
    'https://www.runoob.com/',
    CONCAT('面向岗位能力“', TRIM(pam.ability_name), '”的基础概念、工程实践与进阶练习。'),
    'OTHER',
    'book-open',
    '约8小时',
    0,
    1
FROM (
    SELECT LOWER(TRIM(ability_name)) AS ability_key,
           MIN(ability_name) AS ability_name,
           MAX(min_required_level) AS min_required_level
    FROM post_ability_model
    WHERE post_id BETWEEN 28 AND 32
    GROUP BY LOWER(TRIM(ability_name))
) pam
WHERE pam.ability_name IS NOT NULL
  AND TRIM(pam.ability_name) <> ''
  AND LOWER(TRIM(pam.ability_name)) NOT REGEXP '能力#[0-9]+|未命名|null'
  AND NOT EXISTS (
      SELECT 1 FROM learning_resource lr
      WHERE LOWER(TRIM(lr.ability_name)) = LOWER(TRIM(pam.ability_name))
        AND lr.status = 1
  )
;
