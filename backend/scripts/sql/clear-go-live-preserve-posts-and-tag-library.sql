-- Go-live business-data reset for MySQL 8.
--
-- Retained data (the go-live baseline):
--   * Positions and their approved ability models:
--     post_post, post_ability_model, post_hard_condition_rule
--   * Tag library and its taxonomy:
--     ability_tag, ability_tag_alias, ability_tag_relation,
--     ability_tag_domain_rel, skill_taxonomy_map
--   * System configuration required to keep the deployed Agent behavior:
--     system_ai_model_config, source_weight_config, ability_level_policy,
--     dynamic_credibility_weight, sys_extend_field
--   * Flyway schema history.
--
-- Everything else is business/runtime data and is deleted.
--
-- Preconditions:
--   1. Stop backend instances, scheduler workers, consumers and import jobs.
--   2. Take a MySQL backup. Clear Redis, RabbitMQ, Milvus and Neo4j only after
--      the MySQL transaction succeeds.
--   3. Run with a MySQL account that can delete from all application tables.
--
-- This script deliberately removes all users. The application creates its
-- bootstrap administrator on the next startup when no user exists.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- The supplied baseline dump can predate later Flyway migrations. Make the
-- cleanup forward-compatible: known runtime tables that do not exist yet are
-- skipped, while existing tables are still cleared.
DROP PROCEDURE IF EXISTS clear_table_if_exists;
DELIMITER //
CREATE PROCEDURE clear_table_if_exists(IN p_table_name VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
    ) THEN
        SET @clear_sql = CONCAT('DELETE FROM `', REPLACE(p_table_name, '`', '``'), '`');
        PREPARE clear_statement FROM @clear_sql;
        EXECUTE clear_statement;
        DEALLOCATE PREPARE clear_statement;
    END IF;
END//
DELIMITER ;

-- Required by full graph rebuilds. Older imported schemas may not have reached
-- V136 yet, so establish this harmless infrastructure table before the reset.
CREATE TABLE IF NOT EXISTS job_lock (
    lock_name VARCHAR(100) NOT NULL,
    locked_by VARCHAR(100) NULL,
    locked_at DATETIME NULL,
    expires_at DATETIME NULL,
    PRIMARY KEY (lock_name),
    KEY idx_job_lock_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

START TRANSACTION;

-- Personnel assessment, resumes, AI tests, interviews and evidence.
CALL clear_table_if_exists('assessment_agent_artifact');
CALL clear_table_if_exists('assessment_evidence_ledger');
CALL clear_table_if_exists('assessment_blueprint');
CALL clear_table_if_exists('assessment_scope_snapshot');
CALL clear_table_if_exists('assessment_report');
CALL clear_table_if_exists('capability_stage_lifecycle_event_log');
CALL clear_table_if_exists('ability_harness_batch_item');
CALL clear_table_if_exists('ability_harness_batch');
CALL clear_table_if_exists('person_ability_level_decision');
CALL clear_table_if_exists('person_ability_claim_group');
CALL clear_table_if_exists('person_ability_governance_event');
CALL clear_table_if_exists('person_ability_claim');
CALL clear_table_if_exists('person_ability_profile');
CALL clear_table_if_exists('emp_video_interview_evidence');
CALL clear_table_if_exists('emp_video_interview_ability');
CALL clear_table_if_exists('interview_ability_observation');
CALL clear_table_if_exists('interview_follow_up_question');
CALL clear_table_if_exists('emp_video_interview_question');
CALL clear_table_if_exists('emp_video_interview_session');
CALL clear_table_if_exists('ai_test_coverage');
CALL clear_table_if_exists('emp_ai_test');
CALL clear_table_if_exists('person_capability_stage_run');
CALL clear_table_if_exists('person_capability_workflow');
CALL clear_table_if_exists('emp_ability');
CALL clear_table_if_exists('emp_resume_parse');
CALL clear_table_if_exists('emp_employee');

-- Matching, feedback and asynchronous matching execution.
CALL clear_table_if_exists('matching_rematch_validation');
CALL clear_table_if_exists('matching_feedback_dimension');
CALL clear_table_if_exists('matching_feedback_dataset');
CALL clear_table_if_exists('matching_approval_flow');
CALL clear_table_if_exists('matching_black_white_list');
CALL clear_table_if_exists('matching_task_outbox');
CALL clear_table_if_exists('matching_task');
CALL clear_table_if_exists('matching_record');

-- Position runtime/history, imported JDs, market discovery and evolution.
-- The position master and its active ability model are intentionally retained.
CALL clear_table_if_exists('post_model_unmatched_ability');
CALL clear_table_if_exists('post_model_version_item');
CALL clear_table_if_exists('post_model_version');
CALL clear_table_if_exists('post_model_quality');
CALL clear_table_if_exists('post_data_cleaning_record');
CALL clear_table_if_exists('post_import_item');
CALL clear_table_if_exists('post_import_batch');
CALL clear_table_if_exists('jd_import_task');
CALL clear_table_if_exists('post_prototype_tag');
CALL clear_table_if_exists('post_prototype');
CALL clear_table_if_exists('template_ability_model');
CALL clear_table_if_exists('post_model_template');
CALL clear_table_if_exists('post_evolution_evidence');
CALL clear_table_if_exists('post_evolution_change_item');
CALL clear_table_if_exists('post_evolution_task');
CALL clear_table_if_exists('post_evolution_schedule_config');
CALL clear_table_if_exists('market_jd_data');

-- Tag governance runtime. The tag master, aliases, relations and mappings stay.
CALL clear_table_if_exists('ability_tag_merge_task');
CALL clear_table_if_exists('ability_tag_usage_stat');
CALL clear_table_if_exists('ability_tag_candidate');

-- Governance, knowledge assets, RAG and Agent memory.
CALL clear_table_if_exists('governance_admission');
CALL clear_table_if_exists('ai_harness_check_log');
CALL clear_table_if_exists('ai_hallucination_check');
CALL clear_table_if_exists('prompt_invocation_log');
CALL clear_table_if_exists('rag_query_log');
CALL clear_table_if_exists('rag_knowledge_chunk');
CALL clear_table_if_exists('rag_knowledge_document');
CALL clear_table_if_exists('knowledge_source_chunk');
CALL clear_table_if_exists('knowledge_source_document');
CALL clear_table_if_exists('knowledge_projection_task');
CALL clear_table_if_exists('knowledge_node');
CALL clear_table_if_exists('knowledge_domain');
CALL clear_table_if_exists('agent_memory_hit_log_archive');
CALL clear_table_if_exists('agent_memory_hit_log');
CALL clear_table_if_exists('agent_memory');

-- Learning, PMS, contest, graph projection and other runtime data.
CALL clear_table_if_exists('pms_analysis_task');
CALL clear_table_if_exists('pms_user_mapping');
CALL clear_table_if_exists('ai_learning_suggestion_log');
CALL clear_table_if_exists('learning_progress_log');
CALL clear_table_if_exists('learning_project_submission');
CALL clear_table_if_exists('learning_project_task');
CALL clear_table_if_exists('learning_quiz_record');
CALL clear_table_if_exists('learning_quiz');
CALL clear_table_if_exists('learning_assessment_item');
CALL clear_table_if_exists('learning_path_step');
CALL clear_table_if_exists('learning_path_plan');
CALL clear_table_if_exists('learning_mastery_log');
CALL clear_table_if_exists('learning_resource');
CALL clear_table_if_exists('contest_report_evidence_ref');
CALL clear_table_if_exists('contest_evidence_item');
CALL clear_table_if_exists('contest_report_task');
CALL clear_table_if_exists('kg_post_ability_snapshot');
CALL clear_table_if_exists('kg_relation_candidate');
CALL clear_table_if_exists('kg_graph_edge_new');
CALL clear_table_if_exists('kg_graph_node_new');
CALL clear_table_if_exists('kg_graph_edge');
CALL clear_table_if_exists('kg_graph_node');
CALL clear_table_if_exists('kg_graph_change_set');
CALL clear_table_if_exists('kg_graph_build_task');
CALL clear_table_if_exists('kg_graph_snapshot');
CALL clear_table_if_exists('capability_closure_log');
CALL clear_table_if_exists('vector_sync_task');
CALL clear_table_if_exists('event_outbox');
CALL clear_table_if_exists('token_blacklist');
CALL clear_table_if_exists('sys_operation_log');

-- Accounts and roles are runtime business data. Bootstrap admin is recreated.
CALL clear_table_if_exists('sys_user_role');
CALL clear_table_if_exists('sys_user');
CALL clear_table_if_exists('sys_role');

-- Clear any stale graph-build owner while retaining the required lock seed.
INSERT INTO job_lock (lock_name, locked_by, locked_at, expires_at)
VALUES ('FULL_GRAPH_REBUILD', NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    locked_by = NULL,
    locked_at = NULL,
    expires_at = NULL;

COMMIT;
DROP PROCEDURE IF EXISTS clear_table_if_exists;
SET FOREIGN_KEY_CHECKS = 1;

-- Post-reset verification: retained baseline must remain; runtime data is empty.
SELECT
    (SELECT COUNT(*) FROM post_post) AS retained_post_count,
    (SELECT COUNT(*) FROM post_ability_model) AS retained_post_ability_model_count,
    (SELECT COUNT(*) FROM ability_tag) AS retained_ability_tag_count,
    (SELECT COUNT(*) FROM emp_employee) AS employee_count,
    (SELECT COUNT(*) FROM sys_user) AS user_count,
    (SELECT COUNT(*) FROM matching_record) AS matching_record_count,
    (SELECT COUNT(*) FROM rag_knowledge_document) AS knowledge_document_count,
    (SELECT COUNT(*) FROM kg_graph_node) AS mysql_graph_node_count,
    (SELECT COUNT(*) FROM job_lock WHERE lock_name = 'FULL_GRAPH_REBUILD') AS graph_lock_seed_count;

-- Companion cleanup after this SQL has committed successfully:
--   Redis:     docker compose exec redis redis-cli -a "$REDIS_PASSWORD" FLUSHDB
--   RabbitMQ:  purge application queues from the RabbitMQ management UI or CLI.
--   Milvus:    drop/recreate the configured ${MILVUS_COLLECTION:-person_post_vector}
--              only after stopping the backend; it will be rebuilt from new data.
--   Neo4j:     only if an external Neo4j graph sync is configured:
--              MATCH (n) DETACH DELETE n;
--   Uploads:   remove obsolete files from the matching-uploads-data volume after backup.
