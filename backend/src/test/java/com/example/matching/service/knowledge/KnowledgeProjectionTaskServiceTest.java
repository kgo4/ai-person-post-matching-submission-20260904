package com.example.matching.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.matching.entity.common.KnowledgeProjectionTask;
import com.example.matching.mapper.common.KnowledgeProjectionTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class KnowledgeProjectionTaskServiceTest {

    private KnowledgeProjectionTaskMapper mapper;
    private KnowledgeProjectionTaskService service;

    @BeforeEach
    void setUp() {
        mapper = mock(KnowledgeProjectionTaskMapper.class);
        service = new KnowledgeProjectionTaskService(mapper);
    }

    @Test
    void enqueueInsertsPendingTask() {
        service.enqueue(KnowledgeProjectionTask.Projection.MILVUS_RAG,
                "RAG_DOCUMENT", 42L, 3L,
                KnowledgeProjectionTask.Operation.UPSERT, "abc123");

        ArgumentCaptor<KnowledgeProjectionTask> captor = ArgumentCaptor.forClass(KnowledgeProjectionTask.class);
        verify(mapper).insert(captor.capture());
        KnowledgeProjectionTask t = captor.getValue();
        assertThat(t.getProjection()).isEqualTo("MILVUS_RAG");
        assertThat(t.getAggregateType()).isEqualTo("RAG_DOCUMENT");
        assertThat(t.getAggregateId()).isEqualTo(42L);
        assertThat(t.getTargetRevision()).isEqualTo(3L);
        assertThat(t.getOperation()).isEqualTo("UPSERT");
        assertThat(t.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void enqueueDuplicateCompletedRevisionIsRevivedForCompensation() {
        doThrow(new org.springframework.dao.DuplicateKeyException("dup"))
                .when(mapper).insert(any(KnowledgeProjectionTask.class));

        service.enqueue(KnowledgeProjectionTask.Projection.MILVUS_RAG,
                "RAG_DOCUMENT", 42L, 3L,
                KnowledgeProjectionTask.Operation.UPSERT, "abc123");

        verify(mapper).insert(any(KnowledgeProjectionTask.class));
        verify(mapper).update(isNull(), any());
    }

    @Test
    void enqueueBothRevisionsCreatesSeparateTasks() {
        service.enqueue(KnowledgeProjectionTask.Projection.MILVUS_RAG,
                "RAG_DOCUMENT", 42L, 3L,
                KnowledgeProjectionTask.Operation.UPSERT, "hash3");
        service.enqueue(KnowledgeProjectionTask.Projection.MILVUS_RAG,
                "RAG_DOCUMENT", 42L, 4L,
                KnowledgeProjectionTask.Operation.UPSERT, "hash4");

        verify(mapper, times(2)).insert(any(KnowledgeProjectionTask.class));
    }

    @Test
    void claimNextBatchReturnsEmptyWhenNoPending() {
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        List<KnowledgeProjectionTask> result = service.claimNextBatch(
                KnowledgeProjectionTask.Projection.MILVUS_RAG, 10);

        assertThat(result).isEmpty();
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void claimNextBatchSetsProcessingAndLease() {
        KnowledgeProjectionTask t = new KnowledgeProjectionTask();
        t.setId(1L);
        t.setTargetRevision(3L);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(t));
        when(mapper.update(isNull(), any())).thenReturn(1);

        List<KnowledgeProjectionTask> result = service.claimNextBatch(
                KnowledgeProjectionTask.Projection.MILVUS_RAG, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("PROCESSING");
        assertThat(result.get(0).getLeaseUntil()).isNotNull();
        verify(mapper).update(isNull(), any());
    }

    @Test
    void claimNextBatchDoesNotReturnRowsLostToAnotherWorker() {
        KnowledgeProjectionTask t = new KnowledgeProjectionTask();
        t.setId(1L);
        t.setTargetRevision(3L);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(t));
        when(mapper.update(isNull(), any())).thenReturn(0);

        List<KnowledgeProjectionTask> result = service.claimNextBatch(
                KnowledgeProjectionTask.Projection.MILVUS_RAG, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void markSucceededSetsCompleted() {
        service.markSucceeded(1L);
        verify(mapper).update(isNull(), any());
    }

    @Test
    void markFailedWithRetriesLeftReturnsToPending() {
        KnowledgeProjectionTask t = new KnowledgeProjectionTask();
        t.setId(1L);
        t.setAttemptCount(1);
        t.setMaxAttempts(10);
        when(mapper.selectById(1L)).thenReturn(t);

        service.markFailed(1L, "error");
        verify(mapper).update(isNull(), any());
    }

    @Test
    void markFailedAtMaxAttemptsGoesToFailed() {
        KnowledgeProjectionTask t = new KnowledgeProjectionTask();
        t.setId(1L);
        t.setAttemptCount(9);
        t.setMaxAttempts(10);
        when(mapper.selectById(1L)).thenReturn(t);

        service.markFailed(1L, "error");
        verify(mapper).update(isNull(), any());
    }
}
