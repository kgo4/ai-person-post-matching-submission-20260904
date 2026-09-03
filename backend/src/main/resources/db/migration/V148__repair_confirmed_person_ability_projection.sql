-- Repair historical Harness-approved abilities that were overwritten when
-- untagged abilities were incorrectly treated as one (emp_id, tag_id) record.
-- ability_name is the formal identity; tag_id remains optional enrichment.

INSERT INTO governance_admission (
    admission_code, scenario, claim_type, claim_text, source_type, source_ref_id,
    matched_tag_id, final_decision, decision_rule, risk_level,
    business_target_type, apply_status, claim_payload_json, created_time
)
SELECT
    CONCAT('GAD', g.workflow_id, '-', g.id),
    'PERSON_ABILITY_LEVEL_CONFIRMATION',
    'EMP_ABILITY',
    g.normalized_ability_name,
    'PROFILE_FUSED',
    g.id,
    g.canonical_tag_id,
    'PASS',
    'LEVEL_CONFIRMATION_PROJECTION_REPAIR',
    'LOW',
    'EMP_ABILITY',
    'APPLIED',
    CONCAT('{"workflowId":', g.workflow_id, ',"decisionId":', d.id, ',"repair":true}'),
    NOW()
FROM person_ability_level_decision d
JOIN person_ability_claim_group g ON g.id = d.claim_group_id
WHERE d.decision_status IN ('AUTO_CONFIRMED', 'HUMAN_CONFIRMED')
  AND g.normalized_ability_name IS NOT NULL
  AND TRIM(g.normalized_ability_name) <> ''
ON DUPLICATE KEY UPDATE admission_code = VALUES(admission_code);

INSERT INTO emp_ability (
    emp_id, tag_id, ability_name, assessment_ability_id, workflow_id,
    evidence_summary_ref, harness_decision_id, mastery_level, ability_level,
    evaluation_source, source_weight, evaluation_date, remark,
    governance_admission_id, is_deleted, created_time, updated_time, version
)
SELECT
    g.emp_id,
    NULL,
    g.normalized_ability_name,
    g.assessment_ability_id,
    g.workflow_id,
    CONCAT('workflow:', g.workflow_id, ':ability:', g.assessment_ability_id),
    d.id,
    d.final_level,
    d.final_level,
    'PROFILE_FUSED',
    1.00,
    CURDATE(),
    '能力评估最终审核已通过',
    admission.id,
    0,
    NOW(),
    NOW(),
    0
FROM person_ability_level_decision d
JOIN person_ability_claim_group g ON g.id = d.claim_group_id
JOIN governance_admission admission ON admission.admission_code = CONCAT('GAD', g.workflow_id, '-', g.id)
WHERE d.decision_status IN ('AUTO_CONFIRMED', 'HUMAN_CONFIRMED')
  AND g.normalized_ability_name IS NOT NULL
  AND TRIM(g.normalized_ability_name) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM emp_ability ea
      WHERE ea.emp_id = g.emp_id
        AND ea.is_deleted = 0
        AND LOWER(REPLACE(TRIM(COALESCE(ea.ability_name, '')), ' ', '')) =
            LOWER(REPLACE(TRIM(g.normalized_ability_name), ' ', ''))
  );

INSERT INTO person_ability_profile (
    emp_id, tag_id, ability_name, assessment_ability_id, workflow_id,
    final_level, confidence_score, source_breakdown_json, evidence_count,
    last_evidence_time, review_status, review_state, is_deleted,
    created_time, updated_time, version
)
SELECT
    g.emp_id,
    NULL,
    g.normalized_ability_name,
    g.assessment_ability_id,
    g.workflow_id,
    d.final_level,
    COALESCE(d.final_confidence, 60),
    d.source_breakdown_json,
    (SELECT COUNT(*) FROM person_ability_claim claim WHERE claim.claim_group_id = g.id),
    NOW(),
    'AUTO',
    CASE WHEN d.review_state = 'AUTO' THEN 'AUTO' ELSE 'APPROVED' END,
    0,
    NOW(),
    NOW(),
    0
FROM person_ability_level_decision d
JOIN person_ability_claim_group g ON g.id = d.claim_group_id
WHERE d.decision_status IN ('AUTO_CONFIRMED', 'HUMAN_CONFIRMED')
  AND g.normalized_ability_name IS NOT NULL
  AND TRIM(g.normalized_ability_name) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM person_ability_profile profile
      WHERE profile.emp_id = g.emp_id
        AND profile.is_deleted = 0
        AND LOWER(REPLACE(TRIM(COALESCE(profile.ability_name, '')), ' ', '')) =
            LOWER(REPLACE(TRIM(g.normalized_ability_name), ' ', ''))
  );
