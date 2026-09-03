-- Ability source unification for an existing MySQL 8 database.
--
-- Required before executing:
--   1. Take a database backup.
--   2. Run every preflight query below.
--   3. Stop if any collision query returns rows.
--   4. Execute the transaction only after the preflight result is clean.
--
-- The application accepts aliases during the transition, but new writes use
-- only canonical sources:
-- RESUME_PARSE, AI_TEST, AI_PROJECT, AI_INTERVIEW, LEARNING_PROJECT,
-- MANUAL, PERFORMANCE, PROFILE_FUSED.

DROP TEMPORARY TABLE IF EXISTS _ability_source_alias_map;
CREATE TEMPORARY TABLE _ability_source_alias_map (
    legacy_source VARCHAR(50) NOT NULL PRIMARY KEY,
    canonical_source VARCHAR(50) NOT NULL
);

INSERT INTO _ability_source_alias_map (legacy_source, canonical_source) VALUES
    ('PMS', 'AI_PROJECT'),
    ('PROJECT', 'AI_PROJECT'),
    ('PMS_ANALYSIS', 'AI_PROJECT'),
    ('PROJECT_SYSTEM', 'AI_PROJECT'),
    ('AI_ASSESSMENT', 'AI_TEST'),
    ('AI_VIDEO_INTERVIEW', 'AI_INTERVIEW'),
    ('VIDEO_INTERVIEW', 'AI_INTERVIEW'),
    ('LEARNING', 'LEARNING_PROJECT'),
    ('LEARNING_OUTCOME', 'LEARNING_PROJECT'),
    ('MANUAL_IMPORT', 'MANUAL');

-- Preflight: record the current distributions for the deployment record.
SELECT evaluation_source AS source_type, COUNT(*) AS row_count
FROM emp_ability
GROUP BY evaluation_source
ORDER BY source_type;

SELECT source_type, COUNT(*) AS row_count
FROM person_ability_claim
GROUP BY source_type
ORDER BY source_type;

SELECT source_type, COUNT(*) AS row_count
FROM dynamic_credibility_weight
GROUP BY source_type
ORDER BY source_type;

SELECT source_type, COUNT(*) AS row_count
FROM source_weight_config
GROUP BY source_type
ORDER BY source_type;

-- Preflight: this query MUST return no rows. emp_ability has a unique key on
-- (emp_id, tag_id, evaluation_source), so an alias and canonical value for the
-- same ability must be resolved manually before running the transaction.
SELECT e.emp_id,
       e.tag_id,
       COALESCE(m.canonical_source,
                CASE WHEN UPPER(TRIM(e.evaluation_source)) IN
                    ('RESUME_PARSE', 'AI_TEST', 'AI_PROJECT', 'AI_INTERVIEW',
                     'LEARNING_PROJECT', 'MANUAL', 'PERFORMANCE', 'PROFILE_FUSED')
                    THEN UPPER(TRIM(e.evaluation_source))
                    ELSE 'MANUAL'
                END) AS canonical_source,
       COUNT(*) AS row_count
FROM emp_ability e
LEFT JOIN _ability_source_alias_map m
       ON m.legacy_source = UPPER(TRIM(e.evaluation_source))
GROUP BY e.emp_id, e.tag_id, canonical_source
HAVING COUNT(*) > 1;

-- Preflight: this query MUST return no rows if dynamic_credibility_weight
-- enforces one row per source type in the target database.
SELECT COALESCE(m.canonical_source,
                CASE WHEN UPPER(TRIM(d.source_type)) IN
                    ('RESUME_PARSE', 'AI_TEST', 'AI_PROJECT', 'AI_INTERVIEW',
                     'LEARNING_PROJECT', 'MANUAL', 'PERFORMANCE', 'PROFILE_FUSED')
                    THEN UPPER(TRIM(d.source_type))
                    ELSE 'MANUAL'
                END) AS canonical_source,
       COUNT(*) AS row_count
FROM dynamic_credibility_weight d
LEFT JOIN _ability_source_alias_map m
       ON m.legacy_source = UPPER(TRIM(d.source_type))
GROUP BY canonical_source
HAVING COUNT(*) > 1;

-- Run only after both collision queries above return no rows.
START TRANSACTION;

UPDATE emp_ability e
LEFT JOIN _ability_source_alias_map m
       ON m.legacy_source = UPPER(TRIM(e.evaluation_source))
SET e.evaluation_source = COALESCE(m.canonical_source,
                                   CASE WHEN UPPER(TRIM(e.evaluation_source)) IN
                                       ('RESUME_PARSE', 'AI_TEST', 'AI_PROJECT', 'AI_INTERVIEW',
                                        'LEARNING_PROJECT', 'MANUAL', 'PERFORMANCE', 'PROFILE_FUSED')
                                       THEN UPPER(TRIM(e.evaluation_source))
                                       ELSE 'MANUAL'
                                   END);

UPDATE person_ability_claim c
LEFT JOIN _ability_source_alias_map m
       ON m.legacy_source = UPPER(TRIM(c.source_type))
SET c.source_type = COALESCE(m.canonical_source,
                             CASE WHEN UPPER(TRIM(c.source_type)) IN
                                 ('RESUME_PARSE', 'AI_TEST', 'AI_PROJECT', 'AI_INTERVIEW',
                                  'LEARNING_PROJECT', 'MANUAL', 'PERFORMANCE', 'PROFILE_FUSED')
                                 THEN UPPER(TRIM(c.source_type))
                                 ELSE 'MANUAL'
                             END);

UPDATE dynamic_credibility_weight d
LEFT JOIN _ability_source_alias_map m
       ON m.legacy_source = UPPER(TRIM(d.source_type))
SET d.source_type = COALESCE(m.canonical_source,
                             CASE WHEN UPPER(TRIM(d.source_type)) IN
                                 ('RESUME_PARSE', 'AI_TEST', 'AI_PROJECT', 'AI_INTERVIEW',
                                  'LEARNING_PROJECT', 'MANUAL', 'PERFORMANCE', 'PROFILE_FUSED')
                                 THEN UPPER(TRIM(d.source_type))
                                 ELSE 'MANUAL'
                             END);

-- If a canonical configuration already exists, retain its configured weight.
-- Otherwise promote the newest legacy or unrecognized configuration for that
-- canonical source. Unrecognized values are deliberately treated as MANUAL.
INSERT INTO source_weight_config
    (source_type, source_label, weight, is_active, sort_order, remark, created_time, updated_time)
SELECT normalized.canonical_source,
       CASE normalized.canonical_source
           WHEN 'AI_PROJECT' THEN 'AI项目分析'
           WHEN 'AI_TEST' THEN 'AI测试'
           WHEN 'AI_INTERVIEW' THEN 'AI面试'
           WHEN 'LEARNING_PROJECT' THEN '学习项目'
           WHEN 'MANUAL' THEN '人工录入'
           ELSE normalized.canonical_source
       END,
       normalized.weight, normalized.is_active, normalized.sort_order, normalized.remark, NOW(), NOW()
FROM (
    SELECT c.*,
           COALESCE(m.canonical_source,
                    CASE WHEN UPPER(TRIM(c.source_type)) IN
                        ('RESUME_PARSE', 'AI_TEST', 'AI_PROJECT', 'AI_INTERVIEW',
                         'LEARNING_PROJECT', 'MANUAL', 'PERFORMANCE', 'PROFILE_FUSED')
                        THEN UPPER(TRIM(c.source_type))
                        ELSE 'MANUAL'
                    END) AS canonical_source
    FROM source_weight_config c
    LEFT JOIN _ability_source_alias_map m
           ON m.legacy_source = UPPER(TRIM(c.source_type))
) normalized
LEFT JOIN source_weight_config canonical
     ON canonical.source_type = normalized.canonical_source
WHERE canonical.id IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM source_weight_config newer
      LEFT JOIN _ability_source_alias_map newer_map
             ON newer_map.legacy_source = UPPER(TRIM(newer.source_type))
      WHERE COALESCE(newer_map.canonical_source,
                     CASE WHEN UPPER(TRIM(newer.source_type)) IN
                         ('RESUME_PARSE', 'AI_TEST', 'AI_PROJECT', 'AI_INTERVIEW',
                          'LEARNING_PROJECT', 'MANUAL', 'PERFORMANCE', 'PROFILE_FUSED')
                         THEN UPPER(TRIM(newer.source_type))
                         ELSE 'MANUAL'
                     END) = normalized.canonical_source
        AND (newer.updated_time > normalized.updated_time
             OR (newer.updated_time = normalized.updated_time AND newer.id > normalized.id))
  );

DELETE FROM source_weight_config
WHERE UPPER(TRIM(source_type)) NOT IN
    ('RESUME_PARSE', 'AI_TEST', 'AI_PROJECT', 'AI_INTERVIEW',
     'LEARNING_PROJECT', 'MANUAL', 'PERFORMANCE', 'PROFILE_FUSED');

-- Verify all source values before finalizing the transaction.
SELECT evaluation_source AS source_type, COUNT(*) AS row_count
FROM emp_ability
GROUP BY evaluation_source
ORDER BY source_type;

SELECT source_type, COUNT(*) AS row_count
FROM person_ability_claim
GROUP BY source_type
ORDER BY source_type;

SELECT source_type, COUNT(*) AS row_count
FROM dynamic_credibility_weight
GROUP BY source_type
ORDER BY source_type;

SELECT source_type, COUNT(*) AS row_count
FROM source_weight_config
GROUP BY source_type
ORDER BY source_type;

-- Commit only after the four verification queries contain canonical source
-- values only. Use ROLLBACK instead if the result is unexpected.
COMMIT;
