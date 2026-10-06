-- V166: 权威材料管理（试算模式 + 重复材料去重键）
--
-- 背景：岗位趋势发现页上传权威材料时暴露两个问题。
--   1) 同一份材料重复上传会重新建 RAG 文档、重复切片与向量化，既浪费算力又制造重复召回；
--   2) 有些场景只是「想看看这份材料能解析出什么岗位」，材料本身不必长期留在系统里。
--
-- 因此为知识源文档补齐两个字段：
--   dedup_key  —— 去重键（来源类型 + 资料类别 + 规范化内容哈希）。
--                刻意**不含**资料类别以外的维度：类别决定「跨来源印证」信号，
--                同一份内容以「政策文件」和「市场报告」两种类别上传必须各算一份，
--                否则同一个岗位永远不会被判为多来源印证。
--   ephemeral  —— 仅试算材料，解析任务结束后由系统自动清理。

ALTER TABLE knowledge_source_document
    ADD COLUMN dedup_key VARCHAR(160) NULL COMMENT '去重键：source_type + source_category + 规范化内容哈希',
    ADD COLUMN ephemeral TINYINT NOT NULL DEFAULT 0 COMMENT '仅试算材料：解析完成后自动清理，不长期保留';

CREATE INDEX idx_source_dedup_key ON knowledge_source_document (dedup_key);
CREATE INDEX idx_source_ephemeral ON knowledge_source_document (ephemeral, created_time);
