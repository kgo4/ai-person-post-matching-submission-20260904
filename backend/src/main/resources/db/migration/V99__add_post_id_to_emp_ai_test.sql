-- Persist the source post for asynchronous post-comprehensive AI tests.
-- Post names are not unique, so title-based lookup can select multiple posts.
ALTER TABLE emp_ai_test
    ADD COLUMN post_id BIGINT NULL COMMENT '岗位综合能力测试对应岗位ID' AFTER emp_id;

CREATE INDEX idx_emp_ai_test_post_id ON emp_ai_test (post_id);
