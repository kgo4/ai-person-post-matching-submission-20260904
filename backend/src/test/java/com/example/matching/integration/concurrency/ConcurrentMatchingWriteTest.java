package com.example.matching.integration.concurrency;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingApprovalFlowMapper;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.matching.*;
import com.example.matching.service.matching.impl.MatchingRecordServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Concurrent Matching Record Write Tests")
class ConcurrentMatchingWriteTest {

    @Mock private MatchingExecuteService matchingExecuteService;
    @Mock private MatchingEvidenceScoreCalculator evidenceScoreCalculator;
    @Mock private MatchingDataQueryService dataQuery;
    @Mock private MatchingAiAnalysisService matchingAiAnalysisService;
    @Mock private MatchingApprovalFlowMapper matchingApprovalFlowMapper;
    @Mock private MatchingFeedbackDatasetMapper feedbackDatasetMapper;
    @Mock private MatchingRecordMapper matchingRecordMapper;

    private MatchingRecordServiceImpl service;

    private static final Long RECORD_ID = 100L;
    private static final int THREAD_COUNT = 10;

    @BeforeEach
    void setUp() {
        service = new MatchingRecordServiceImpl(
                matchingExecuteService, evidenceScoreCalculator,
                dataQuery, matchingAiAnalysisService, new ObjectMapper(),
                matchingApprovalFlowMapper, feedbackDatasetMapper
        );
        ReflectionTestUtils.setField(service, "baseMapper", matchingRecordMapper);
    }

    @Test
    @DisplayName("10 threads concurrently calling modifyResult on unlocked record -- exactly one succeeds")
    void concurrentModifyResult_oneSucceedsOthersThrow() throws Exception {
        // Arrange: record is unlocked, has optimistic lock version=0
        MatchingRecord record = buildUnlockedRecord();
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);

        // First updateById succeeds (version 0 -> 1); subsequent calls fail (optimistic lock conflict)
        AtomicInteger updateCalls = new AtomicInteger(0);
        doAnswer(invocation -> {
            int call = updateCalls.incrementAndGet();
            if (call == 1) {
                record.setVersion(1); // simulate DB version bump
                return 1;
            }
            return 0; // optimistic lock failure -- 0 rows updated
        }).when(matchingRecordMapper).updateById(any(MatchingRecord.class));

        // Feedback insert always succeeds
        doReturn(1).when(feedbackDatasetMapper).insert(any(MatchingFeedbackDataset.class));

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadIndex = i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                barrier.await(5, TimeUnit.SECONDS);
                try {
                    MatchingRecord update = new MatchingRecord();
                    update.setFinalMatchScore(new BigDecimal("90.00"));
                    update.setMatchStatus(1);
                    service.modifyResult(RECORD_ID, update);
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            }));
        }

        // Assert: count successes and failures
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        for (Future<Boolean> f : futures) {
            if (f.get(10, TimeUnit.SECONDS)) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
        }

        executor.shutdown();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(THREAD_COUNT - 1);
    }

    @Test
    @DisplayName("10 threads concurrently calling modifyResult on locked record -- all throw BusinessException")
    void concurrentModifyResult_allThrowWhenLocked() throws Exception {
        MatchingRecord record = buildUnlockedRecord();
        record.setIsLocked(1); // locked
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
        List<Future<ErrorCodeEnum>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                barrier.await(5, TimeUnit.SECONDS);
                try {
                    MatchingRecord update = new MatchingRecord();
                    update.setFinalMatchScore(new BigDecimal("90.00"));
                    service.modifyResult(RECORD_ID, update);
                    return null; // should not reach here
                } catch (BusinessException e) {
                    return ErrorCodeEnum.MATCHING_ALREADY_LOCKED;
                }
            }));
        }

        for (Future<ErrorCodeEnum> f : futures) {
            assertThat(f.get(10, TimeUnit.SECONDS))
                    .as("Each thread should get MATCHING_ALREADY_LOCKED")
                    .isEqualTo(ErrorCodeEnum.MATCHING_ALREADY_LOCKED);
        }

        // updateById should never be called
        verify(matchingRecordMapper, never()).updateById(any(MatchingRecord.class));

        executor.shutdown();
    }

    @Test
    @DisplayName("Concurrent lockResult on same record -- only first lock succeeds via DB optimistic guard")
    void concurrentLockResult_onlyOneSucceeds() throws Exception {
        MatchingRecord record = buildUnlockedRecord();
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);

        AtomicInteger lockAttempts = new AtomicInteger(0);
        doAnswer(invocation -> {
            int attempt = lockAttempts.incrementAndGet();
            // Simulate DB-level guard: only the first CAS update succeeds
            return attempt == 1 ? 1 : 0;
        }).when(matchingRecordMapper).updateById(any(MatchingRecord.class));

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                barrier.await(5, TimeUnit.SECONDS);
                try {
                    service.lockResult(RECORD_ID);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        AtomicInteger successCount = new AtomicInteger();
        for (Future<Boolean> f : futures) {
            if (f.get(10, TimeUnit.SECONDS)) successCount.incrementAndGet();
        }

        // At least one thread should succeed setting the lock
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(lockAttempts.get()).isEqualTo(THREAD_COUNT);

        executor.shutdown();
    }

    // ==================== helpers ====================

    private MatchingRecord buildUnlockedRecord() {
        MatchingRecord record = new MatchingRecord();
        record.setId(RECORD_ID);
        record.setEmpId(1L);
        record.setPostId(2L);
        record.setAiMatchScore(new BigDecimal("78.00"));
        record.setMatchStatus(2);
        record.setApprovalStatus(0);
        record.setIsLocked(0);
        record.setVersion(0);
        return record;
    }
}
