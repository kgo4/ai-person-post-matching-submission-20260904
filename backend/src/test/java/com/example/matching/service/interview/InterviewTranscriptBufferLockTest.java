package com.example.matching.service.interview;

import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.schedule.SchedulerMetrics;
import com.example.matching.service.common.DistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewTranscriptBufferLockTest {

    private DistributedLockService distributedLockService;
    private InterviewTranscriptBuffer buffer;

    @BeforeEach
    void setUp() {
        distributedLockService = mock(DistributedLockService.class);
        buffer = new InterviewTranscriptBuffer(
                mock(EmpVideoInterviewSessionMapper.class),
                mock(EmpVideoInterviewQuestionMapper.class),
                mock(StringRedisTemplate.class),
                distributedLockService,
                mock(SchedulerMetrics.class));
    }

    @Test
    void lockNotAcquiredSkipsFlush() {
        when(distributedLockService.tryAcquire("interview-transcript-flush"))
                .thenReturn(null);

        assertThatCode(() -> buffer.flushPending())
                .doesNotThrowAnyException();
    }

    @Test
    void lockAcquiredFlushesAndReleasesLock() {
        DistributedLockService.LockHandle handle = mock(DistributedLockService.LockHandle.class);
        when(distributedLockService.tryAcquire("interview-transcript-flush"))
                .thenReturn(handle);

        buffer.flushPending();

        verify(handle).close();
    }

    @Test
    void appendDistinctKeepsOnlyOneCopyOfTheSameFinalTranscript() {
        StringBuilder transcript = new StringBuilder();

        assertThat(InterviewTranscriptBuffer.appendDistinct(transcript, "我负责慢 SQL 优化")).isTrue();
        assertThat(InterviewTranscriptBuffer.appendDistinct(transcript, "我负责慢 SQL 优化")).isFalse();

        assertThat(transcript.toString()).isEqualTo("我负责慢 SQL 优化");
    }
}
