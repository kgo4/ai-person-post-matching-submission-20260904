ALTER TABLE ability_tag_merge_task
    ADD COLUMN created_by BIGINT NOT NULL DEFAULT 0 COMMENT '定时归并任务创建人' AFTER error_message,
    ADD KEY idx_ability_tag_merge_task_creator_status (created_by, status, completed_time);
