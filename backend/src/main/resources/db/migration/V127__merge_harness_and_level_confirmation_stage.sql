-- New assessments use one atomic stage. Existing terminal history is retained;
-- only resumable legacy stage rows are renamed and remain runner-compatible.
UPDATE person_capability_stage_run
SET stage_type = 'AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION'
WHERE stage_type = 'LEVEL_CONFIRMATION'
  AND status IN ('PENDING', 'RUNNING');

UPDATE person_capability_workflow
SET current_stage = 'AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION'
WHERE current_stage = 'LEVEL_CONFIRMATION'
  AND status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED');
