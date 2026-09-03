-- V112: 能力等级确认策略配置表
-- 等级确认中心的全部系数与规则配置化，生成版本快照供审计回放

CREATE TABLE ability_level_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    policy_version VARCHAR(50) NOT NULL COMMENT '策略版本号',
    policy_name VARCHAR(200) NOT NULL COMMENT '策略名称',
    config_json MEDIUMTEXT NOT NULL COMMENT '策略配置JSON（单来源上限/阈值/系数等）',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    effective_from DATETIME NULL COMMENT '生效时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    UNIQUE KEY uk_policy_version (policy_version),
    INDEX idx_policy_enabled (enabled, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='能力等级确认策略配置';
