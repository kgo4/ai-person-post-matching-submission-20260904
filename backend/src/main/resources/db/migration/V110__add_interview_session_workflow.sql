-- V110: 面试会话工作流关联
-- 支持面试完成后推进能力评估工作流聚合审核

ALTER TABLE emp_video_interview_session
    ADD COLUMN workflow_id BIGINT NULL COMMENT '关联的能力评估工作流ID' AFTER post_id;

CREATE INDEX idx_video_session_workflow_id ON emp_video_interview_session (workflow_id);
