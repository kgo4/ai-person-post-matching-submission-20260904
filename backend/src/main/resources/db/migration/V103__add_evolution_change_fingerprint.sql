-- M4: 岗位演化变更项 fingerprint（同岗位+能力+变更类型+来源引用去重/冷却）
ALTER TABLE post_evolution_change_item
    ADD COLUMN fingerprint VARCHAR(64) NULL COMMENT '变更指纹：postId:tagId:changeType:sourceRef 的 MD5',
    ADD KEY idx_evolution_change_fingerprint (fingerprint, created_time);
