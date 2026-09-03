package com.example.matching.infrastructure.persistence;

import com.example.matching.entity.common.KnowledgeProjectionTask;
import com.example.matching.port.knowledge.KnowledgeProjectionPort;
import com.example.matching.service.knowledge.KnowledgeProjectionTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Adapts the RAG domain's projection request to the durable outbox service. */
@Component
@RequiredArgsConstructor
public class KnowledgeProjectionPortAdapter implements KnowledgeProjectionPort {

    private final KnowledgeProjectionTaskService projectionTaskService;

    @Override
    public void enqueueMilvusRagDocument(long documentId, long contentRevision, String contentHash) {
        projectionTaskService.enqueue(
                KnowledgeProjectionTask.Projection.MILVUS_RAG,
                "RAG_DOCUMENT",
                documentId,
                contentRevision,
                KnowledgeProjectionTask.Operation.UPSERT,
                contentHash);
    }

    @Override
    public void enqueueNeo4jGraphSnapshot(long graphVersion) {
        projectionTaskService.enqueue(
                KnowledgeProjectionTask.Projection.NEO4J_GRAPH,
                "GRAPH_SNAPSHOT",
                graphVersion,
                0,
                KnowledgeProjectionTask.Operation.UPSERT,
                String.valueOf(graphVersion));
    }
}
