package com.example.matching.port.knowledge;

/**
 * Boundary for requesting durable RAG document projection.
 */
public interface KnowledgeProjectionPort {

    void enqueueMilvusRagDocument(long documentId, long contentRevision, String contentHash);

    /**
     * M20：请求 Neo4j 展示图投影重试任务（Outbox 记录、可查看状态）。
     *
     * @param graphVersion 图版本（重试任务溯源）
     */
    void enqueueNeo4jGraphSnapshot(long graphVersion);
}
