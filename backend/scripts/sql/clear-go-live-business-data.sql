-- Full go-live data reset for MySQL 8.
--
-- This script deletes every application data record, including users, posts,
-- tags, configuration, AI model configuration, knowledge assets, and runtime
-- queues. It deliberately preserves only database schema and flyway history.
--
-- Preconditions:
--   1. Stop backend instances, scheduler workers, RabbitMQ consumers, and import jobs.
--   2. Back up MySQL, Redis, RabbitMQ, Milvus/Zilliz, Neo4j, and uploaded files first.
--   3. Do not run this against a database containing data that must be retained.
--
-- After execution:
--   - The next backend startup creates admin / ADMIN automatically.
--   - Configure production environment variables before starting the backend.
--   - Rebuild vector collections and the knowledge graph from newly imported data.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Assessment, resume, AI test, interview, evidence, and personnel data.
DELETE FROM assessment_agent_artifact;
DELETE FROM assessment_evidence_ledger;
DELETE FROM assessment_blueprint;
DELETE FROM assessment_scope_snapshot;
DELETE FROM assessment_report;
DELETE FROM capability_stage_lifecycle_event_log;
DELETE FROM ability_harness_batch_item;
DELETE FROM ability_harness_batch;
DELETE FROM person_ability_level_decision;
DELETE FROM person_ability_claim_group;
DELETE FROM person_ability_governance_event;
DELETE FROM person_ability_claim;
DELETE FROM person_ability_profile;
DELETE FROM emp_video_interview_evidence;
DELETE FROM emp_video_interview_ability;
DELETE FROM interview_ability_observation;
DELETE FROM interview_follow_up_question;
DELETE FROM emp_video_interview_question;
DELETE FROM emp_video_interview_session;
DELETE FROM ai_test_coverage;
DELETE FROM emp_ai_test;
DELETE FROM person_capability_stage_run;
DELETE FROM person_capability_workflow;
DELETE FROM emp_ability;
DELETE FROM emp_resume_parse;
DELETE FROM emp_employee;

-- Matching, feedback, approval, and asynchronous matching data.
DELETE FROM matching_rematch_validation;
DELETE FROM matching_feedback_dimension;
DELETE FROM matching_feedback_dataset;
DELETE FROM matching_approval_flow;
DELETE FROM matching_black_white_list;
DELETE FROM matching_task_outbox;
DELETE FROM matching_task;
DELETE FROM matching_record;

-- Position, import, market discovery, and evolution data.
DELETE FROM post_model_unmatched_ability;
DELETE FROM post_model_version_item;
DELETE FROM post_model_version;
DELETE FROM post_model_quality;
DELETE FROM post_ability_model;
DELETE FROM post_hard_condition_rule;
DELETE FROM post_data_cleaning_record;
DELETE FROM post_import_item;
DELETE FROM post_import_batch;
DELETE FROM jd_import_task;
DELETE FROM post_prototype_tag;
DELETE FROM post_prototype;
DELETE FROM template_ability_model;
DELETE FROM post_model_template;
DELETE FROM post_evolution_evidence;
DELETE FROM post_evolution_change_item;
DELETE FROM post_evolution_task;
DELETE FROM post_evolution_schedule_config;
DELETE FROM market_jd_data;
DELETE FROM post_post;

-- Tag taxonomy, governance, and system-defined skill mappings.
DELETE FROM ability_tag_merge_task;
DELETE FROM ability_tag_usage_stat;
DELETE FROM ability_tag_domain_rel;
DELETE FROM ability_tag_relation;
DELETE FROM ability_tag_alias;
DELETE FROM ability_tag_candidate;
DELETE FROM skill_taxonomy_map;
DELETE FROM ability_tag;

-- Governance, RAG, knowledge assets, and Agent memory.
DELETE FROM governance_admission;
DELETE FROM ai_harness_check_log;
DELETE FROM ai_hallucination_check;
DELETE FROM prompt_invocation_log;
DELETE FROM rag_query_log;
DELETE FROM rag_knowledge_chunk;
DELETE FROM rag_knowledge_document;
DELETE FROM knowledge_source_chunk;
DELETE FROM knowledge_source_document;
DELETE FROM knowledge_projection_task;
DELETE FROM knowledge_node;
DELETE FROM knowledge_domain;
DELETE FROM agent_memory_hit_log_archive;
DELETE FROM agent_memory_hit_log;
DELETE FROM agent_memory;

-- Learning, PMS, contest, graph projection, and common runtime data.
DELETE FROM pms_analysis_task;
DELETE FROM pms_user_mapping;
DELETE FROM ai_learning_suggestion_log;
DELETE FROM learning_progress_log;
DELETE FROM learning_project_submission;
DELETE FROM learning_project_task;
DELETE FROM learning_quiz_record;
DELETE FROM learning_quiz;
DELETE FROM learning_assessment_item;
DELETE FROM learning_path_step;
DELETE FROM learning_path_plan;
DELETE FROM learning_mastery_log;
DELETE FROM learning_resource;
DELETE FROM contest_report_evidence_ref;
DELETE FROM contest_evidence_item;
DELETE FROM contest_report_task;
DELETE FROM kg_post_ability_snapshot;
DELETE FROM kg_relation_candidate;
DELETE FROM kg_graph_edge_new;
DELETE FROM kg_graph_node_new;
DELETE FROM kg_graph_edge;
DELETE FROM kg_graph_node;
DELETE FROM kg_graph_change_set;
DELETE FROM kg_graph_build_task;
DELETE FROM kg_graph_snapshot;
DELETE FROM capability_closure_log;
DELETE FROM vector_sync_task;
DELETE FROM event_outbox;
DELETE FROM job_lock;
DELETE FROM token_blacklist;
DELETE FROM sys_operation_log;

-- System users and all persisted application configuration.
DELETE FROM sys_user_role;
DELETE FROM sys_user;
DELETE FROM sys_role;
DELETE FROM system_ai_model_config;
DELETE FROM source_weight_config;
DELETE FROM ability_level_policy;
DELETE FROM dynamic_credibility_weight;
DELETE FROM sys_extend_field;

SET FOREIGN_KEY_CHECKS = 1;

-- Required seed for the database-backed full graph build lock.
INSERT INTO job_lock (lock_name, locked_by, locked_at, expires_at)
VALUES ('FULL_GRAPH_REBUILD', NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    locked_by = NULL,
    locked_at = NULL,
    expires_at = NULL;

-- Verification: every listed application-data table must be empty after reset.
SELECT
    (SELECT COUNT(*) FROM sys_user) AS sys_user_count,
    (SELECT COUNT(*) FROM post_post) AS post_count,
    (SELECT COUNT(*) FROM post_ability_model) AS post_ability_model_count,
    (SELECT COUNT(*) FROM ability_tag) AS ability_tag_count,
    (SELECT COUNT(*) FROM emp_employee) AS employee_count,
    (SELECT COUNT(*) FROM matching_record) AS matching_record_count,
    (SELECT COUNT(*) FROM rag_knowledge_document) AS knowledge_document_count,
    (SELECT COUNT(*) FROM person_capability_workflow) AS assessment_workflow_count,
    (SELECT COUNT(*) FROM flyway_schema_history) AS flyway_history_count,
    (SELECT COUNT(*) FROM job_lock WHERE lock_name = 'FULL_GRAPH_REBUILD') AS graph_lock_seed_count;
