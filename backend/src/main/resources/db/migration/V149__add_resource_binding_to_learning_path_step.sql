-- =========================================================================
-- V149: learning_path_step 增加资源关联字段 + 学习资源数据规范化
-- 目的：学习路径步骤绑定真实学习资源（abilityName 主关联，tagId 辅助），
--       无资源时保留能力差距但不阻断；同时清理学习资源库异常数据。
-- =========================================================================

-- 1. learning_path_step 增加资源关联字段
ALTER TABLE learning_path_step
    ADD COLUMN resource_id BIGINT NULL COMMENT '推荐学习资源ID（来自 learning_resource）' AFTER source_refs_json,
    ADD COLUMN resource_title VARCHAR(256) NULL COMMENT '推荐资源标题' AFTER resource_id,
    ADD COLUMN resource_url VARCHAR(512) NULL COMMENT '推荐资源链接' AFTER resource_title,
    ADD COLUMN resource_type VARCHAR(32) NULL COMMENT '推荐资源类型：COURSE/DOC/PRACTICE/PROJECT/BOOK/VIDEO' AFTER resource_url,
    ADD COLUMN resource_count INT NOT NULL DEFAULT 0 COMMENT '该能力匹配到的启用资源总数' AFTER resource_type;

CREATE INDEX idx_lps_resource_id ON learning_path_step (resource_id);

-- 2. 学习资源数据规范化（仅清理明显异常数据，不删除正常多资源）

-- 2.1 清理空能力名称 / 占位能力名
DELETE FROM learning_resource
WHERE ability_name IS NULL
   OR TRIM(ability_name) = ''
   OR ability_name LIKE '能力#%'
   OR ability_name IN ('未命名能力', 'unknown', 'null', 'N/A', 'n/a');

-- 2.2 清理乱码标题（含 Unicode 替换符 U+FFFD（�）的标题）
DELETE FROM learning_resource
WHERE title IS NOT NULL
  AND title LIKE '%�%';

-- 2.3 标题为空时用能力名称兜底
UPDATE learning_resource
SET title = ability_name
WHERE (title IS NULL OR TRIM(title) = '') AND ability_name IS NOT NULL;

-- 2.4 同 abilityName + 标题 + 链接完全重复的记录只保留最小 id
DELETE r1 FROM learning_resource r1
INNER JOIN learning_resource r2
  ON r1.ability_name = r2.ability_name
 AND COALESCE(r1.title, '') = COALESCE(r2.title, '')
 AND COALESCE(r1.url, '') = COALESCE(r2.url, '')
 AND r1.id > r2.id;

-- 2.5 状态为空统一置为启用
UPDATE learning_resource SET status = 1 WHERE status IS NULL;

-- 2.6 空描述置空，避免占位文本污染展示
UPDATE learning_resource SET description = NULL
WHERE description IS NOT NULL AND CHAR_LENGTH(TRIM(description)) < 2;
