-- 匹配任务消费端重试字段
-- 消费失败时将任务置回 PENDING 并递增 retry_count，由 outbox 重投驱动重试；
-- 达到上限后置 FAILED 终态。next_retry_time 控制调度重扫节奏。

ALTER TABLE matching_task
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '消费端重试次数',
    ADD COLUMN next_retry_time DATETIME NULL COMMENT '下次重试时间（重试期任务置回 PENDING 后由重投扫描使用）';

CREATE INDEX idx_matching_task_retry ON matching_task (status, next_retry_time);
