package com.example.matching.schedule;

import com.example.matching.entity.system.SysOperationLog;
import com.example.matching.service.common.DistributedLockService;
import com.example.matching.service.system.SysOperationLogService;
import com.example.matching.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作包3：统一调度任务执行器
 */
class ScheduledTaskRunnerTest {

    private SchedulerMetrics schedulerMetrics;
    private SysOperationLogService sysOperationLogService;
    private DistributedLockService lockService;
    private ScheduledTaskRunner runner;

    @BeforeEach
    void setUp() {
        schedulerMetrics = mock(SchedulerMetrics.class);
        sysOperationLogService = mock(SysOperationLogService.class);
        lockService = mock(DistributedLockService.class);
        DistributedLockService.LockHandle mockHandle = mock(DistributedLockService.LockHandle.class);
        when(lockService.tryAcquire(any())).thenReturn(mockHandle);
        runner = new ScheduledTaskRunner(schedulerMetrics, lockService);
        ReflectionTestUtils.setField(runner, "sysOperationLogService", sysOperationLogService);
    }

    @Test
    void successClearsContextWithoutWritingAudit() {
        MDC.put("traceId", "trace-1");
        SecurityUtils.setCurrentUserId(0L);

        runner.run("test_task", () -> {
            assertThat(SecurityUtils.getCurrentUsername()).isEqualTo("system");
        });

        verify(sysOperationLogService, never()).save(any(SysOperationLog.class));
        // finally 清理：SecurityUtils、SecurityContextHolder、MDC
        assertThat(SecurityUtils.getCurrentUserId()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void failureRecordsMetricAndAudit() {
        runner.run("test_task", () -> {
            throw new IllegalStateException("boom");
        });

        verify(schedulerMetrics).recordFailure("test_task");
        verify(sysOperationLogService).save(any(SysOperationLog.class));
    }

    @Test
    void failureDoesNotPropagateToCaller() {
        runner.run("test_task", () -> {
            throw new IllegalStateException("boom");
        });
        // 无异常抛出即为通过
    }

    @Test
    void nullAuditServiceIsTolerated() {
        ReflectionTestUtils.setField(runner, "sysOperationLogService", null);
        runner.run("test_task", () -> { });
        verify(sysOperationLogService, never()).save(any(SysOperationLog.class));
    }
}
