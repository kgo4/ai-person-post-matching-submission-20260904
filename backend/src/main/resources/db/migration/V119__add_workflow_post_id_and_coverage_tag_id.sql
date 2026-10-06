-- V119: 岗位绑定到工作流 + 测试覆盖关系增加能力标签ID
-- 支持评估流程岗位单一真相源（测试选岗后锁定、面试复用）+ 测试题→能力组的确定性映射

-- ============ person_capability_workflow：绑定目标岗位 ============
ALTER TABLE person_capability_workflow
    ADD COLUMN post_id BIGINT NULL COMMENT '绑定的目标岗位ID（测试选岗后锁定，面试复用）' AFTER resume_parse_id;

CREATE INDEX idx_workflow_post_id ON person_capability_workflow (post_id);

-- ============ ai_test_coverage：增加能力标签ID ============
ALTER TABLE ai_test_coverage
    ADD COLUMN tag_id BIGINT NULL COMMENT '题目映射的能力标签ID（归并每能力等级时按题归属能力）' AFTER claim_group_id;

CREATE INDEX idx_test_coverage_tag_id ON ai_test_coverage (tag_id);
