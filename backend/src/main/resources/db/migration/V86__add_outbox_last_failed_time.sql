-- DLQ 运营闭环：outbox FAILED 终态补充最后失败时间
-- 失败次数(attempt_count)、最后错误(error_message) 已存在，补齐 last_failed_time

ALTER TABLE event_outbox
    ADD COLUMN last_failed_time DATETIME NULL COMMENT '最后失败时间（FAILED 终态）';

ALTER TABLE matching_task_outbox
    ADD COLUMN last_failed_time DATETIME NULL COMMENT '最后失败时间（FAILED 终态）';
