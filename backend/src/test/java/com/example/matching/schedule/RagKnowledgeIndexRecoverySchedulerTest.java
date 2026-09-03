package com.example.matching.schedule;

import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.service.knowledge.KnowledgeProjectionTaskService;
import com.example.matching.service.rag.KnowledgeDocumentService;
import com.example.matching.utils.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagKnowledgeIndexRecoverySchedulerTest {

    private KnowledgeDocumentService knowledgeDocumentService;
    private KnowledgeProjectionTaskService projectionTaskService;
    private RagKnowledgeIndexRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        knowledgeDocumentService = mock(KnowledgeDocumentService.class);
        projectionTaskService = mock(KnowledgeProjectionTaskService.class);
        scheduler = new RagKnowledgeIndexRecoveryScheduler(
                knowledgeDocumentService, projectionTaskService);
    }

    @AfterEach
    void tearDown() {
        SecurityUtils.clear();
    }

    @Test
    void executesRecoveryWhenDocumentsStale() {
        RagKnowledgeDocument stale = new RagKnowledgeDocument();
        stale.setId(1L);
        stale.setContentRevision(5L);
        stale.setContentHash("hash-5");
        stale.setIndexedRevision(3L);
        when(knowledgeDocumentService.listAllActiveDocuments(100)).thenReturn(List.of(stale));

        scheduler.recoverPendingIndexes();

        verify(knowledgeDocumentService).listAllActiveDocuments(100);
        verify(projectionTaskService).enqueue(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("RAG_DOCUMENT"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("hash-5"));
    }

    @Test
    void skipsDocumentsWithoutRevisionMetadata() {
        RagKnowledgeDocument incomplete = new RagKnowledgeDocument();
        incomplete.setId(2L);
        when(knowledgeDocumentService.listAllActiveDocuments(100)).thenReturn(List.of(incomplete));

        scheduler.recoverPendingIndexes();

        verify(knowledgeDocumentService).listAllActiveDocuments(100);
    }

    @Test
    void skipsDocumentsAlreadyIndexedAtLatestRevision() {
        RagKnowledgeDocument fresh = new RagKnowledgeDocument();
        fresh.setId(3L);
        fresh.setContentRevision(4L);
        fresh.setContentHash("hash-4");
        fresh.setIndexedRevision(4L);
        when(knowledgeDocumentService.listAllActiveDocuments(100)).thenReturn(List.of(fresh));

        scheduler.recoverPendingIndexes();

        verify(knowledgeDocumentService).listAllActiveDocuments(100);
    }
}
