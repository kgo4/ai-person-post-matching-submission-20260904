-- M-07: 保留并展示未匹配能力标签
-- AI 提取能力无法匹配已有 AbilityTag 时，持久化到本表供管理员查看、绑定或忽略。
CREATE TABLE IF NOT EXISTS post_model_unmatched_ability (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id              BIGINT       NOT NULL COMMENT '岗位模型版本ID(post_model_version.id)',
    ability_name            VARCHAR(128) NOT NULL COMMENT 'AI 提取的能力名称',
    normalized_ability_name VARCHAR(128) NULL COMMENT '归一化后的能力名称',
    reason                  VARCHAR(64)  NOT NULL COMMENT '未匹配原因: MATCHED_TAG_ID_NOT_FOUND/TAG_NAME_NOT_FOUND/TAG_DISABLED/TAG_NAME_AMBIGUOUS',
    min_required_level      INT          NULL COMMENT '建议最低要求等级 1-5',
    weight                  DECIMAL(10,2) NULL COMMENT '建议权重 0-100',
    is_required             TINYINT      NULL COMMENT '是否必需 0-否 1-是',
    is_core                 TINYINT      NULL COMMENT '是否核心 0-否 1-是',
    reasoning               VARCHAR(1024) NULL COMMENT 'AI 推理说明',
    status                  VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/TAG_BOUND/IGNORED',
    candidate_id            BIGINT       NULL COMMENT '已创建的标签候选ID(ability_tag_candidate.id)',
    bound_tag_id            BIGINT       NULL COMMENT '绑定后的正式标签ID(ability_tag.id)',
    created_by              BIGINT       NULL COMMENT '创建人ID',
    created_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_version_id (version_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '岗位模型未匹配能力标签（AI 提取但无法匹配已有标签的能力）';
