package com.example.matching.service.matching;

import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.schedule.SchedulerMetrics;
import com.example.matching.service.common.DistributedLockService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiScoringRecoverySchedulerTest {

    @Test
    void recordsMetricWhenRecoveryRunFails() {
        DistributedLockService lockService = mock(DistributedLockService.class);
        SchedulerMetrics metrics = mock(SchedulerMetrics.class);
        when(lockService.tryAcquire("ai-scoring-recovery"))
                .thenThrow(new IllegalStateException("redis unavailable"));

        AiScoringRecoveryScheduler scheduler = new AiScoringRecoveryScheduler(
                mock(MatchingRecordMapper.class),
                mock(MatchingAiScoringStateMachine.class),
                mock(MatchingAiScoringRetryService.class),
                lockService,
                metrics);

        scheduler.recoverStalledRecords();

        verify(metrics).recordFailure("ai_scoring_recovery");
    }
}
