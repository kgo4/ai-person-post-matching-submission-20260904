package com.example.matching.service.rag;

import com.example.matching.entity.rag.RagKnowledgeChunk;

import java.util.List;

/**
 * RAG向量存储接口
 * <p>
 * 实现层：{@link com.example.matching.service.rag.impl.MilvusRagVectorStore}（Milvus HNSW，主用）
 * 降级：{@link com.example.matching.service.rag.impl.MysqlRagVectorStore}（MySQL 全量加载，兼容）
 */
public interface RagVectorStore {

    /**
     * 检索最相关的分块
     *
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @param sourceTypes 可选的来源类型过滤
     * @return 按相似度降序排列的分块及分数
     */
    List<ScoredChunk> search(List<Float> queryVector, int topK, List<String> sourceTypes);

    /**
     * 将一个文档分块的向量写入向量库（新索引或更新）
     *
     * @param chunk      知识分块记录
     * @param sourceType 所属文档的 sourceType
     * @param vector     1536 维向量
     */
    void insert(RagKnowledgeChunk chunk, String sourceType, List<Float> vector);

    /**
     * 删除指定文档在向量库中的所有分块
     */
    void deleteByDocumentId(Long documentId);

    /**
     * 删除指定分块
     */
    void deleteByChunkId(Long chunkId);

    /**
     * 带分数的分块结果
     */
    record ScoredChunk(RagKnowledgeChunk chunk, float score) {
    }
}
