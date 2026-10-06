package com.example.matching.schedule;

import com.example.matching.service.ability.AgentMemoryService;
import com.example.matching.service.common.DistributedLockService;
import com.example.matching.utils.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentMemoryExpirationSchedulerTest {

    private AgentMemoryService agentMemoryService;
    private DistributedLockService distributedLockService;
    private SchedulerMetrics schedulerMetrics;
    private AgentMemoryExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        agentMemoryService = mock(AgentMemoryService.class);
        distributedLockService = mock(DistributedLockService.class);
        schedulerMetrics = mock(SchedulerMetrics.class);
        scheduler = new AgentMemoryExpirationScheduler(agentMemoryService, schedulerMetrics);
    }

    @AfterEach
    void tearDown() {
        SecurityUtils.clear();
    }

    @Test
    void runsExpirationAndReleasesContext() {
        // 当前实现：无 taskRunner 时直接执行（分布式锁由 ScheduledTaskRunner 统一承担）
        when(agentMemoryService.expireDueMemories()).thenReturn(3);

        scheduler.expireDueMemories();

        verify(agentMemoryService).expireDueMemories();
    }

    @Test
    void failureRecordsMetric() {
        when(agentMemoryService.expireDueMemories()).thenThrow(new IllegalStateException("boom"));

        scheduler.expireDueMemories();

        verify(schedulerMetrics).recordFailure("agent_memory_expiration");
    }
}
