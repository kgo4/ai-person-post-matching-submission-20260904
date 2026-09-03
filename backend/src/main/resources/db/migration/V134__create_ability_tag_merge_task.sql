CREATE TABLE ability_tag_merge_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_code VARCHAR(64) NOT NULL,
    threshold DECIMAL(4,3) NOT NULL,
    scheduled_time DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_time DATETIME NULL,
    completed_time DATETIME NULL,
    result_summary TEXT NULL,
    error_message VARCHAR(1000) NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_ability_tag_merge_task_code (task_code),
    KEY idx_ability_tag_merge_task_due (status, scheduled_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='能力标签定时归并任务';
