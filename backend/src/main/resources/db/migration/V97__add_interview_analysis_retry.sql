-- 面试后 AI 分析重试字段
-- 分析任务状态机：3=FINISHED(待分析) -> 4=ANALYZING -> 5=COMPLETED / 7=FAILED
-- 调度器扫描超时 ANALYZING 会话，未达上限则置回 3 重跑，达到上限置 7 终态。

ALTER TABLE emp_video_interview_session
    ADD COLUMN analysis_retry_count INT NOT NULL DEFAULT 0 COMMENT '面试后分析重试次数',
    ADD COLUMN analysis_failed_reason VARCHAR(1000) NULL COMMENT '面试后分析失败原因';

CREATE INDEX idx_evs_analyzing ON emp_video_interview_session (status, updated_time);
