-- Java AI应用开发工程师 -> learning_resource
-- Safe to execute repeatedly: existing active resources for the same ability
-- are skipped. All text comes from the UTF-8 ability_name column.
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

START TRANSACTION;

INSERT INTO learning_resource
    (resource_code, ability_name, tag_id, title, resource_type, difficulty_level,
     url, description, platform, platform_icon, duration, sort_order, status)
SELECT
    CONCAT('POST能力_', SUBSTRING(MD5(CONCAT('JAVA_AI_APP|', LOWER(TRIM(pam.ability_name)))), 1, 20)),
    TRIM(pam.ability_name),
    NULL,
    TRIM(pam.ability_name),
    CASE
        WHEN LOWER(pam.ability_name) REGEXP 'java|spring|python|pytorch|rag|embedding|milvus|redis|mysql|kafka|docker|kubernetes|agent|prompt|ocr'
            THEN 'COURSE'
        ELSE 'DOC'
    END,
    CASE WHEN COALESCE(pam.min_required_level, 1) >= 4 THEN 4 ELSE 3 END,
    'https://www.runoob.com/',
    TRIM(pam.ability_name),
    'OTHER',
    'book-open',
    '约8小时',
    0,
    1
FROM post_ability_model pam
JOIN post_post pp ON pp.id = pam.post_id
WHERE pp.post_name LIKE 'Java AI%'
  AND pam.ability_name IS NOT NULL
  AND TRIM(pam.ability_name) <> ''
  AND LOWER(TRIM(pam.ability_name)) NOT REGEXP '能力#[0-9]+|未命名|null'
  AND NOT EXISTS (
      SELECT 1
      FROM learning_resource lr
      WHERE LOWER(TRIM(lr.ability_name)) = LOWER(TRIM(pam.ability_name))
        AND lr.status = 1
  )
GROUP BY LOWER(TRIM(pam.ability_name)), TRIM(pam.ability_name), pam.min_required_level;

COMMIT;

-- Verification: number and names of resources now available for this post.
SELECT COUNT(*) AS inserted_or_existing_resources
FROM learning_resource lr
JOIN post_ability_model pam ON LOWER(TRIM(pam.ability_name)) = LOWER(TRIM(lr.ability_name))
JOIN post_post pp ON pp.id = pam.post_id
WHERE pp.post_name LIKE 'Java AI%'
  AND lr.status = 1;
