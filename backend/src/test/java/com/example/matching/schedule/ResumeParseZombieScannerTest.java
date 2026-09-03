package com.example.matching.schedule;

import com.example.matching.service.employee.ResumeParseService;
import com.example.matching.utils.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeParseZombieScannerTest {

    private ResumeParseService resumeParseService;
    private SchedulerMetrics schedulerMetrics;
    private ResumeParseZombieScanner scanner;

    @BeforeEach
    void setUp() {
        resumeParseService = mock(ResumeParseService.class);
        schedulerMetrics = mock(SchedulerMetrics.class);
        scanner = new ResumeParseZombieScanner(resumeParseService, schedulerMetrics);
    }

    @AfterEach
    void tearDown() {
        SecurityUtils.clear();
    }

    @Test
    void runsRecoveryWhenNoTaskRunner() {
        when(resumeParseService.recoverZombieTasks()).thenReturn(0);

        scanner.scanZombieTasks();

        verify(resumeParseService).recoverZombieTasks();
    }

    @Test
    void reportsRecoveredCount() {
        when(resumeParseService.recoverZombieTasks()).thenReturn(3);

        scanner.scanZombieTasks();

        verify(resumeParseService).recoverZombieTasks();
        verify(schedulerMetrics, never()).recordFailure(any());
    }

    @Test
    void failureRecordsMetric() {
        when(resumeParseService.recoverZombieTasks()).thenThrow(new IllegalStateException("boom"));

        scanner.scanZombieTasks();

        verify(schedulerMetrics).recordFailure("resume_parse_zombie_scan");
    }
}

class ResumeParseWaitingRetryCompensationTest {

    @org.junit.jupiter.api.Test
    void scanZombieTasksInvokesWaitingRetryCompensation() {
        ResumeParseService resumeParseService = mock(ResumeParseService.class);
        ResumeParseZombieScanner scanner = new ResumeParseZombieScanner(
                resumeParseService, mock(SchedulerMetrics.class));

        scanner.scanZombieTasks();

        verify(resumeParseService).recoverWaitingRetryTasks();
    }
}
