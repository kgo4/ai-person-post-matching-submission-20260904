package com.example.matching.service.rag.impl;

/**
 * RAG 向量投影降级信号。
 * <p>
 * Milvus 不可用/调用失败时抛出：数据已落 MySQL 权威表（rag_knowledge_chunk），
 * 由调用方（知识文档索引）将文档标记为 DEGRADED 且不更新 indexedRevision，
 * 待 Milvus 恢复后由补偿调度器（RagKnowledgeIndexRecoveryScheduler）重放投影。
 */
public class RagVectorStoreFallbackException extends RuntimeException {

    public RagVectorStoreFallbackException(String message) {
        super(message);
    }

    public RagVectorStoreFallbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
