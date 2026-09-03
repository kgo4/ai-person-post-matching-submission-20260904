package com.example.matching.schedule;

import com.example.matching.service.common.DistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduledTaskRunnerLockTest {

    private DistributedLockService lockService;
    private SchedulerMetrics schedulerMetrics;
    private ScheduledTaskRunner runner;

    @BeforeEach
    void setUp() {
        lockService = mock(DistributedLockService.class);
        schedulerMetrics = mock(SchedulerMetrics.class);
        runner = new ScheduledTaskRunner(schedulerMetrics, lockService);
    }

    @Test
    void failedAcquisitionDoesNotRunBody() {
        when(lockService.tryAcquire(any())).thenReturn(null);

        AtomicBoolean ran = new AtomicBoolean(false);
        runner.run("test-task", () -> ran.set(true));

        assertThat(ran.get()).isFalse();
    }

    @Test
    void acquiredLockRunsBody() {
        DistributedLockService.LockHandle mockHandle = mock(DistributedLockService.LockHandle.class);
        when(lockService.tryAcquire(any())).thenReturn(mockHandle);

        AtomicBoolean ran = new AtomicBoolean(false);
        runner.run("test-task", () -> ran.set(true));

        assertThat(ran.get()).isTrue();
    }

    @Test
    void taskExceptionDoesNotPropagate() {
        DistributedLockService.LockHandle mockHandle = mock(DistributedLockService.LockHandle.class);
        when(lockService.tryAcquire(any())).thenReturn(mockHandle);

        AtomicBoolean ran = new AtomicBoolean(false);
        runner.run("test-task", () -> {
            ran.set(true);
            throw new RuntimeException("Task failed");
        });

        assertThat(ran.get()).isTrue();
    }
}
