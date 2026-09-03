ALTER TABLE interview_follow_up_question
    ADD COLUMN duration_seconds INT NULL COMMENT '服务端确定的追问答题时长（秒）' AFTER question_text;
