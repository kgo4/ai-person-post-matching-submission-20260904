package com.example.matching.service.knowledge;

import com.example.matching.entity.common.KnowledgeProjectionTask;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.service.rag.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Processes the MySQL-backed projection outbox; MySQL document revisions are authoritative. */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeProjectionWorker {

    private static final int BATCH_SIZE = 20;

    private final KnowledgeProjectionTaskService projectionTaskService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final com.example.matching.service.kg.build.Neo4jSnapshotSynchronizer neo4jSnapshotSynchronizer;

    @Scheduled(fixedDelayString = "${knowledge.projection.worker-delay-ms:5000}")
    public void projectMilvusRagDocuments() {
        for (KnowledgeProjectionTask task : projectionTaskService.claimNextBatch(
                KnowledgeProjectionTask.Projection.MILVUS_RAG, BATCH_SIZE)) {
            try {
                if (!"RAG_DOCUMENT".equals(task.getAggregateType())) {
                    projectionTaskService.markFailed(task.getId(), "Unsupported aggregate type: " + task.getAggregateType());
                    continue;
                }
                RagKnowledgeDocument document = knowledgeDocumentService.getDocumentById(task.getAggregateId());
                if (!task.getTargetRevision().equals(document.getContentRevision())) {
                    projectionTaskService.markSucceeded(task.getId());
                    continue;
                }
                knowledgeDocumentService.indexDocument(document.getId());
                projectionTaskService.markSucceeded(task.getId());
            } catch (Exception e) {
                log.warn("Knowledge projection failed: taskId={}, documentId={}", task.getId(), task.getAggregateId(), e);
                projectionTaskService.markFailed(task.getId(), e.getMessage());
            }
        }
    }

    /**
     * M20：Neo4j 展示图投影重试消费者（Outbox 记录、失败可重试、可查看状态）。
     * MySQL 权威图查询不受影响；Neo4j 仅用于展示/探索。
     */
    @Scheduled(fixedDelayString = "${knowledge.projection.neo4j-worker-delay-ms:30000}")
    public void projectNeo4jGraphSnapshot() {
        for (KnowledgeProjectionTask task : projectionTaskService.claimNextBatch(
                KnowledgeProjectionTask.Projection.NEO4J_GRAPH, BATCH_SIZE)) {
            try {
                if (!"GRAPH_SNAPSHOT".equals(task.getAggregateType())) {
                    projectionTaskService.markFailed(task.getId(), "Unsupported aggregate type: " + task.getAggregateType());
                    continue;
                }
                neo4jSnapshotSynchronizer.syncIfAvailable(task.getAggregateId());
                projectionTaskService.markSucceeded(task.getId());
            } catch (Exception e) {
                log.warn("Neo4j projection retry failed: taskId={}, graphVersion={}", task.getId(), task.getAggregateId(), e);
                projectionTaskService.markFailed(task.getId(), e.getMessage());
            }
        }
    }
}
