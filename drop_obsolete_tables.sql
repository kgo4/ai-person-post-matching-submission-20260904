-- ============================================================================
-- 废弃表删除脚本
-- 数据库: hrms_db (MySQL 8.0)
-- 生成日期: 2026-08-09
-- 依据: .reasonix/attachments/clipboard-20260809-030057.035549-000001.sql 全库导出
--       与 backend 代码交叉比对（2026-08-09 工作区代码）
-- ============================================================================
--
-- 【判定结论】
-- 以下 5 张表在最新全库导出中存在，但：
--   1) backend/frontend 全部代码零引用（无实体类、无 Mapper、无原生 SQL）；
--   2) Flyway 迁移脚本（db/migration/V*.sql）从未创建/引用它们；
--   3) 导出中无 AUTO_INCREMENT 数据（空表）；
--   4) 无其他表外键引用它们。
-- 属于历史遗留/被新表替代的废弃表，可安全删除。
--
-- 废弃原因明细：
--   mcp_ability_change_record   早期 MCP 能力监测实验遗留，功能已被 market_jd_data
--                               + post_evolution_* 取代，代码从未落地。
--   mcp_ability_monitor_log     同上，能力监测日志遗留表。
--   mcp_emerging_job_candidate  同上，新兴岗位候选遗留表。
--   post_skill_trend_snapshot   岗位技能趋势快照遗留表（演化模块早期设计），
--                               代码中无任何写入/读取。
--   knowledge_source_chunk      旧版知识来源切片表，已被 rag_knowledge_chunk
--                               （V89/V92 迁移 + RagKnowledgeChunk 实体）取代；
--                               其残留 entity/mapper 为死代码（Mapper 零引用）。
--
-- 【已确认不可删的表（供核对）】
--   kg_graph_edge_new / kg_graph_node_new   —— 知识图谱重建影子表，动态表名路由活跃使用
--   agent_memory_hit_log_archive            —— AgentMemoryHitLogArchiver 归档服务活跃使用
--   job_lock                                —— GraphBuildTaskServiceImpl 分布式锁使用
--   knowledge_source_document               —— 仍被 AiContextSourceRef / EvolutionSource 桥接引用
--
-- 【执行方式】
--   mysql -uroot -p hrms_db < drop_obsolete_tables.sql
--   或分步执行（推荐先 RENAME 备份，观察确认无误后再 DROP）。
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 第一步（可选，推荐）：重命名为备份表，保留数据以便回滚
-- ----------------------------------------------------------------------------
RENAME TABLE
    `mcp_ability_change_record`   TO `_obsolete_mcp_ability_change_record`,
    `mcp_ability_monitor_log`     TO `_obsolete_mcp_ability_monitor_log`,
    `mcp_emerging_job_candidate`  TO `_obsolete_mcp_emerging_job_candidate`,
    `post_skill_trend_snapshot`   TO `_obsolete_post_skill_trend_snapshot`,
    `knowledge_source_chunk`      TO `_obsolete_knowledge_source_chunk`;

-- ----------------------------------------------------------------------------
-- 第二步：确认无误后，正式删除（取消下方注释执行）
-- ----------------------------------------------------------------------------
-- DROP TABLE IF EXISTS
--     `_obsolete_mcp_ability_change_record`,
--     `_obsolete_mcp_ability_monitor_log`,
--     `_obsolete_mcp_emerging_job_candidate`,
--     `_obsolete_post_skill_trend_snapshot`,
--     `_obsolete_knowledge_source_chunk`;

-- ----------------------------------------------------------------------------
-- 或者：直接物理删除（已确认均为空表且零引用，可一步到位）
-- ----------------------------------------------------------------------------
-- DROP TABLE IF EXISTS
--     `mcp_ability_change_record`,
--     `mcp_ability_monitor_log`,
--     `mcp_emerging_job_candidate`,
--     `post_skill_trend_snapshot`,
--     `knowledge_source_chunk`;

SET FOREIGN_KEY_CHECKS = 1;
