-- 知识图谱影子表：非破坏性全量重建用
-- rebuildFullGraph 先投影到影子表，投影成功后再在事务内 DELETE 主表 + INSERT..SELECT 回填，
-- 任一步失败主表不受影响，杜绝"先删后建"的破坏性实现。
-- 注意：kg_graph_node/kg_graph_edge 由基线脚本创建（不在 Flyway 迁移中），
-- 影子表必须使用显式 DDL（不能 LIKE 基表），保证全新数据库可迁移。

CREATE TABLE IF NOT EXISTS kg_graph_node_new (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `node_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Stable node key, e.g. POST:1',
  `node_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'POST/POST_FAMILY/ABILITY/TECH_STACK/EMPLOYEE/EVIDENCE/RAG_DOCUMENT/LEARNING_RESOURCE/EVOLUTION_EVENT/EVALUATION_CASE',
  `ref_id` bigint NULL DEFAULT NULL COMMENT 'Source business id',
  `label` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Display label',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'Category or domain',
  `level_value` int NULL DEFAULT NULL COMMENT 'Ability or post level value',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `weight_value` decimal(10, 2) NULL DEFAULT NULL COMMENT 'Node weight for visualization',
  `metadata_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'Node metadata JSON',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_node_key`(`node_key`) USING BTREE,
  INDEX `idx_node_type`(`node_type`) USING BTREE,
  INDEX `idx_ref`(`node_type`, `ref_id`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Knowledge graph node shadow (rebuild staging)';

CREATE TABLE IF NOT EXISTS kg_graph_edge_new (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `edge_key` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Stable edge key',
  `source_node_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Source node key',
  `target_node_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Target node key',
  `edge_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'REQUIRES/HAS_ABILITY/BELONGS_TO/SUPPORTED_BY/DERIVED_FROM/RECOMMENDS/EVOLVED_TO/MATCHED_WITH/EVALUATED_BY',
  `weight_value` decimal(10, 2) NULL DEFAULT NULL COMMENT 'Edge weight',
  `confidence_score` decimal(5, 2) NULL DEFAULT NULL COMMENT 'Confidence 0-100',
  `metadata_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'Edge metadata JSON',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_edge_key`(`edge_key`) USING BTREE,
  INDEX `idx_source`(`source_node_key`) USING BTREE,
  INDEX `idx_target`(`target_node_key`) USING BTREE,
  INDEX `idx_edge_type`(`edge_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Knowledge graph edge shadow (rebuild staging)';
