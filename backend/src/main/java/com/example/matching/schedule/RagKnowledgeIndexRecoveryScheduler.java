package com.example.matching.schedule;

import com.example.matching.entity.common.KnowledgeProjectionTask;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.service.knowledge.KnowledgeProjectionTaskService;
import com.example.matching.service.rag.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class RagKnowledgeIndexRecoveryScheduler {

    private static final int RECOVERY_BATCH_SIZE = 100;

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeProjectionTaskService projectionTaskService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    @Scheduled(
            fixedDelayString = "${rag.knowledge.index-recovery-delay-ms:60000}",
            initialDelayString = "${rag.knowledge.index-recovery-initial-delay-ms:60000}")
    public void recoverPendingIndexes() {
        runScheduled("rag_knowledge_index_recovery", this::recoverPendingIndexesInternal);
    }

    private void recoverPendingIndexesInternal() {
        try {
            for (RagKnowledgeDocument document : knowledgeDocumentService.listAllActiveDocuments(RECOVERY_BATCH_SIZE)) {
                if (document.getContentRevision() == null || document.getContentHash() == null) {
                    continue;
                }
                if (document.getIndexedRevision() == null
                        || document.getIndexedRevision() < document.getContentRevision()) {
                    projectionTaskService.enqueue(KnowledgeProjectionTask.Projection.MILVUS_RAG,
                            "RAG_DOCUMENT", document.getId(), document.getContentRevision(),
                            KnowledgeProjectionTask.Operation.UPSERT, document.getContentHash());
                }
            }
        } catch (Exception e) {
            log.error("RAG knowledge index recovery scan failed, pending indexes may never be built", e);
        }
    }

    private void runScheduled(String taskName, Runnable task) {
        if (taskRunner != null) {
            taskRunner.run(taskName, task);
            return;
        }
        try {
            task.run();
        } catch (Exception e) {
            log.error("RAG knowledge index recovery scan failed, pending indexes may never be built", e);
        }
    }
}
