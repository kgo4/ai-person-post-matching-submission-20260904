-- 人岗匹配演示数据（MySQL 8）
-- 只写入 emp_ability，岗位能力表和人员正式能力表仍是业务权威来源。
-- 执行前先把 @emp_id / @post_id 改成实际员工和岗位 ID。

SET @emp_id = 74;
SET @post_id = 1;

-- 1) 核对目标员工与岗位能力
SELECT id, real_name, emp_code
FROM emp_employee
WHERE id = @emp_id AND is_deleted = 0;

SELECT id, post_id, ability_name, min_required_level, weight, is_required, is_core
FROM post_ability_model
WHERE post_id = @post_id AND is_deleted = 0
ORDER BY is_core DESC, is_required DESC, weight DESC, id;

-- 2) 按岗位能力表批量补齐人员演示能力。
-- ability_name 是主身份；tag_id 仅作为可选辅助关联；重复执行不会重复插入。
INSERT INTO emp_ability (
    emp_id, tag_id, ability_name, mastery_level, ability_level,
    evaluation_source, source_weight, evaluation_date, remark,
    is_deleted, created_time, updated_time, version
)
SELECT
    @emp_id,
    NULL,
    TRIM(pam.ability_name),
    GREATEST(COALESCE(pam.min_required_level, 3), 3),
    GREATEST(COALESCE(pam.min_required_level, 3), 3),
    'MANUAL',
    1.00,
    CURDATE(),
    '人岗匹配演示数据',
    0,
    NOW(),
    NOW(),
    0
FROM post_ability_model pam
WHERE pam.post_id = @post_id
  AND pam.is_deleted = 0
  AND pam.ability_name IS NOT NULL
  AND TRIM(pam.ability_name) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM emp_ability ea
      WHERE ea.emp_id = @emp_id
        AND ea.is_deleted = 0
        AND LOWER(REPLACE(TRIM(COALESCE(ea.ability_name, '')), ' ', '')) =
            LOWER(REPLACE(TRIM(pam.ability_name), ' ', ''))
  );

-- 3) 如果岗位能力表为空，可用下面的固定演示能力补数据。
INSERT INTO emp_ability (
    emp_id, tag_id, ability_name, mastery_level, ability_level,
    evaluation_source, source_weight, evaluation_date, remark,
    is_deleted, created_time, updated_time, version
)
SELECT @emp_id, NULL, demo.ability_name, demo.mastery_level, demo.mastery_level,
       'MANUAL', 1.00, CURDATE(), '人岗匹配演示数据', 0, NOW(), NOW(), 0
FROM (
    SELECT 'Java' AS ability_name, 4 AS mastery_level
    UNION ALL SELECT 'Spring Boot', 4
    UNION ALL SELECT 'MySQL', 3
    UNION ALL SELECT 'Redis', 3
    UNION ALL SELECT 'Docker', 3
) demo
WHERE @emp_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM emp_ability ea
      WHERE ea.emp_id = @emp_id
        AND ea.is_deleted = 0
        AND LOWER(REPLACE(TRIM(COALESCE(ea.ability_name, '')), ' ', '')) =
            LOWER(REPLACE(TRIM(demo.ability_name), ' ', ''))
  );

-- 4) 验证最终人员能力（能力名称优先，标签为空也应正常显示和匹配）
SELECT id, emp_id, ability_name, tag_id, mastery_level,
       evaluation_source, source_weight, is_deleted
FROM emp_ability
WHERE emp_id = @emp_id AND is_deleted = 0
ORDER BY ability_name;
