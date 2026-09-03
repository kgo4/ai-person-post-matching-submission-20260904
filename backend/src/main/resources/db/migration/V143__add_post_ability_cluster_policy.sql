ALTER TABLE system_ai_model_config
    ADD COLUMN post_ability_cluster_min_member_count INT NULL COMMENT '未归一岗位能力簇最少成员数',
    ADD COLUMN post_ability_cluster_min_post_count INT NULL COMMENT '未归一岗位能力簇最少覆盖岗位数',
    ADD COLUMN post_ability_cluster_join_similarity DECIMAL(5,4) NULL COMMENT '加入已有岗位能力簇的最小余弦相似度',
    ADD COLUMN post_ability_cluster_promotion_cohesion DECIMAL(5,4) NULL COMMENT '提升为待治理簇的最小平均内聚度';

UPDATE system_ai_model_config
SET post_ability_cluster_min_member_count = COALESCE(post_ability_cluster_min_member_count, 3),
    post_ability_cluster_min_post_count = COALESCE(post_ability_cluster_min_post_count, 2),
    post_ability_cluster_join_similarity = COALESCE(post_ability_cluster_join_similarity, 0.8200),
    post_ability_cluster_promotion_cohesion = COALESCE(post_ability_cluster_promotion_cohesion, 0.8000);
