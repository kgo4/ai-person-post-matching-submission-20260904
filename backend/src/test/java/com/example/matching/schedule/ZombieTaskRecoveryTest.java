package com.example.matching.schedule;

import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.entity.post.PostImportBatch;
import com.example.matching.listener.AiTestTaskPayload;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.post.PostImportBatchMapper;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.service.common.EventOutboxDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作包5：进程中断后的 PROCESSING 任务被扫描恢复
 */
@ExtendWith(MockitoExtension.class)
class ZombieTaskRecoveryTest {

    @Mock private EmpAiTestMapper empAiTestMapper;
    @Mock private PostImportBatchMapper importBatchMapper;
    @Mock private EventOutboxDispatcher outboxDispatcher;
    @Mock private CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;

    @InjectMocks private AiTestTaskRecoveryScheduler aiTestRecovery;
    @InjectMocks private ExcelImportZombieScanner excelScanner;

    @Test
    void aiTestGenerationZombieIsRecoveredAndRedelivered() {
        EmpAiTest zombie = new EmpAiTest();
        zombie.setId(5L);
        zombie.setRetryCount(1);
        when(empAiTestMapper.selectZombieGeneration(any())).thenReturn(List.of(zombie));
        when(empAiTestMapper.selectZombieEvaluation(any())).thenReturn(List.of());
        when(empAiTestMapper.recoverGeneration(eq(5L), anyString(), anyString())).thenReturn(1);

        aiTestRecovery.recoverZombieTasks();

        verify(empAiTestMapper).recoverGeneration(eq(5L), anyString(), anyString());
        verify(outboxDispatcher).enqueue(eq("AI_TEST"), anyString(), eq("ai.test.generate"),
                argThat(payload -> payload instanceof AiTestTaskPayload task
                        && "GENERATE".equals(task.getTaskType())));
    }

    @Test
    void aiTestZombieWithExhaustedRetriesIsMarkedFailedAndNotRedelivered() {
        EmpAiTest zombie = new EmpAiTest();
        zombie.setId(6L);
        zombie.setRetryCount(3);
        zombie.setWorkflowId(42L);
        when(empAiTestMapper.selectZombieGeneration(any())).thenReturn(List.of(zombie));
        when(empAiTestMapper.selectZombieEvaluation(any())).thenReturn(List.of());
        when(empAiTestMapper.failGeneration(eq(6L), anyString(), anyString())).thenReturn(1);

        aiTestRecovery.recoverZombieTasks();

        verify(empAiTestMapper).failGeneration(eq(6L), anyString(), anyString());
        verify(outboxDispatcher, never()).enqueue(anyString(), anyString(), anyString(), any());
        verify(lifecycleEventPublisher).publish(argThat(event ->
                event.workflowId().equals(42L)
                        && event.stageType().equals("AI_TEST_GENERATION")
                        && event.sourceRefId().equals(6L)));
    }

    @Test
    void excelZombieIsRecoveredAndRedelivered() {
        PostImportBatch zombie = new PostImportBatch();
        zombie.setId(11L);
        zombie.setImportStatus(1);
        zombie.setProcessingStartedAt(LocalDateTime.now().minusMinutes(30));
        zombie.setRetryCount(0);
        when(importBatchMapper.selectZombieBatches(any())).thenReturn(List.of(zombie));
        when(importBatchMapper.recoverZombie(eq(11L), anyString(), anyString())).thenReturn(1);

        excelScanner.scanZombieBatches();

        verify(importBatchMapper).recoverZombie(eq(11L), anyString(), anyString());
        verify(outboxDispatcher).enqueue(eq("EXCEL_IMPORT_ANALYZE"), anyString(),
                eq("excel.import.analyze.execute"), eq(11L));
    }

    @Test
    void excelZombieWithExhaustedRetriesIsMarkedFailedAndNotRedelivered() {
        PostImportBatch zombie = new PostImportBatch();
        zombie.setId(12L);
        zombie.setImportStatus(1);
        zombie.setRetryCount(3);
        when(importBatchMapper.selectZombieBatches(any())).thenReturn(List.of(zombie));
        when(importBatchMapper.failZombie(eq(12L), anyString(), anyString())).thenReturn(1);

        excelScanner.scanZombieBatches();

        verify(importBatchMapper).failZombie(eq(12L), anyString(), anyString());
        verify(outboxDispatcher, never()).enqueue(anyString(), anyString(), anyString(), any());
    }
}
