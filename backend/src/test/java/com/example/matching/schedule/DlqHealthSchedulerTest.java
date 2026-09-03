package com.example.matching.schedule;

import com.example.matching.entity.system.SysOperationLog;
import com.example.matching.service.common.DlqReplayService;
import com.example.matching.service.system.SysOperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作包6：DLQ 深度告警调度器
 */
class DlqHealthSchedulerTest {

    private DlqReplayService dlqReplayService;
    private SysOperationLogService sysOperationLogService;
    private DlqHealthScheduler scheduler;

    @BeforeEach
    void setUp() {
        dlqReplayService = mock(DlqReplayService.class);
        sysOperationLogService = mock(SysOperationLogService.class);
        scheduler = new DlqHealthScheduler(dlqReplayService, sysOperationLogService);
        ReflectionTestUtils.setField(scheduler, "alertThreshold", 20L);
    }

    @Test
    void exceedingThresholdWritesStructuredErrorAndAudit() {
        when(dlqReplayService.summary())
                .thenReturn(new DlqReplayService.DlqSummary(35, java.time.LocalDateTime.now()));
        // 手工构造带阈值摘要
        when(dlqReplayService.summary()).thenReturn(new DlqReplayService.DlqSummary(
                35, java.time.LocalDateTime.now(), 20, true));

        scheduler.checkDlqDepth();

        verify(sysOperationLogService).save(any(SysOperationLog.class));
    }

    @Test
    void belowThresholdDoesNotWriteAudit() {
        when(dlqReplayService.summary()).thenReturn(new DlqReplayService.DlqSummary(
                5, java.time.LocalDateTime.now(), 20, false));

        scheduler.checkDlqDepth();

        verify(sysOperationLogService, never()).save(any(SysOperationLog.class));
    }

    @Test
    void checkFailureIsLoggedAndDoesNotThrow() {
        when(dlqReplayService.summary()).thenThrow(new RuntimeException("broker unreachable"));

        scheduler.checkDlqDepth();

        verify(sysOperationLogService, never()).save(any(SysOperationLog.class));
    }
}
