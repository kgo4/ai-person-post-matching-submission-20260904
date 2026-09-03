ALTER TABLE post_ability_model
    ADD COLUMN tech_stack VARCHAR(64) NULL COMMENT '技术栈，如Java、Spring、MySQL',
    ADD COLUMN skill_point_key VARCHAR(160) NULL COMMENT '岗位内技能点规范键';

CREATE UNIQUE INDEX uk_post_ability_skill_point_active
    ON post_ability_model (post_id, skill_point_key, is_deleted);
