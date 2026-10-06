CREATE TABLE ability_tag_candidate_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    candidate_id BIGINT NOT NULL COMMENT '语义簇代表 ability_tag_candidate.id',
    post_id BIGINT NOT NULL COMMENT '岗位能力事实所属岗位',
    ability_name VARCHAR(255) NOT NULL COMMENT '原始、已证据验证的能力表达',
    source_type VARCHAR(64) NULL,
    source_ref_id BIGINT NULL,
    evidence_text TEXT NULL,
    similarity_score DECIMAL(6,5) NOT NULL COMMENT '成员到簇中心的余弦相似度',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_candidate_post_ability (candidate_id, post_id, ability_name),
    KEY idx_candidate_id (candidate_id),
    KEY idx_post_id (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位未归一能力的语义候选簇成员';
