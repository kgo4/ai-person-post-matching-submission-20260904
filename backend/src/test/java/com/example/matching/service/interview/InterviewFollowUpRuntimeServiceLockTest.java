package com.example.matching.service.interview;

import com.example.matching.mapper.interview.InterviewFollowUpQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.schedule.SchedulerMetrics;
import com.example.matching.service.common.DistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewFollowUpRuntimeServiceLockTest {

    private DistributedLockService distributedLockService;
    private InterviewFollowUpRuntimeService service;

    @BeforeEach
    void setUp() {
        distributedLockService = mock(DistributedLockService.class);
        service = new InterviewFollowUpRuntimeService(
                mock(InterviewFollowUpQuestionMapper.class),
                mock(EmpVideoInterviewSessionMapper.class),
                mock(ApplicationEventPublisher.class),
                new InterviewDurationPolicy(),
                distributedLockService,
                mock(SchedulerMetrics.class));
    }

    @Test
    void lockNotAcquiredSkipsFlush() {
        when(distributedLockService.tryAcquire("interview-followup-flush"))
                .thenReturn(null);

        assertThatCode(() -> service.flushPendingAnswers())
                .doesNotThrowAnyException();
    }

    @Test
    void lockAcquiredFlushesAndReleasesLock() {
        DistributedLockService.LockHandle handle = mock(DistributedLockService.LockHandle.class);
        when(distributedLockService.tryAcquire("interview-followup-flush"))
                .thenReturn(handle);

        service.flushPendingAnswers();

        verify(handle).close();
    }
}
