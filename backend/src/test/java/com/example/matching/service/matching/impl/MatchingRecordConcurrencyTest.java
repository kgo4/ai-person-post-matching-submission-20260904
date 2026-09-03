package com.example.matching.service.matching.impl;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingApprovalFlowMapper;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.matching.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for concurrent access patterns on matching records:
 * lock-then-modify rejection, delete-then-getDetail, unlock-then-modify success.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MatchingRecord concurrency tests")
class MatchingRecordConcurrencyTest {

    @Mock private MatchingExecuteService matchingExecuteService;
    @Mock private MatchingEvidenceScoreCalculator evidenceScoreCalculator;
    @Mock private MatchingDataQueryService dataQuery;
    @Mock private MatchingAiAnalysisService matchingAiAnalysisService;
    @Mock private MatchingApprovalFlowMapper matchingApprovalFlowMapper;
    @Mock private MatchingFeedbackDatasetMapper feedbackDatasetMapper;
    @Mock private MatchingRecordMapper matchingRecordMapper;

    private MatchingRecordServiceImpl service;

    private static final Long RECORD_ID = 100L;
    private static final Long EMP_ID = 1L;
    private static final Long POST_ID = 2L;

    @BeforeEach
    void setUp() {
        service = new MatchingRecordServiceImpl(
                matchingExecuteService, evidenceScoreCalculator,
                dataQuery, matchingAiAnalysisService, new ObjectMapper(),
                matchingApprovalFlowMapper, feedbackDatasetMapper
        );
        ReflectionTestUtils.setField(service, "baseMapper", matchingRecordMapper);
    }

    // ========== Lock → modifyResult throws ==========

    @Nested
    @DisplayName("Lock then modify")
    class LockThenModify {

        @Test
        @DisplayName("Locked record rejects modifyResult with BusinessException")
        void lockedRecord_rejectsModify() {
            MatchingRecord record = buildRecord();
            record.setIsLocked(1);
            when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);

            MatchingRecord update = new MatchingRecord();
            update.setFinalMatchScore(new BigDecimal("90.00"));

            assertThatThrownBy(() -> service.modifyResult(RECORD_ID, update))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Locked record does not call updateById")
        void lockedRecord_doesNotUpdate() {
            MatchingRecord record = buildRecord();
            record.setIsLocked(1);
            when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);

            MatchingRecord update = new MatchingRecord();
            update.setFinalMatchScore(new BigDecimal("90.00"));

            try {
                service.modifyResult(RECORD_ID, update);
            } catch (BusinessException ignored) {
            }

            verify(matchingRecordMapper, never()).updateById((MatchingRecord) any());
        }

        @Test
        @DisplayName("Lock then lock again succeeds (idempotent)")
        void lockTwice_succeeds() {
            MatchingRecord record = buildRecord();
            record.setIsLocked(1); // already locked
            when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
            doReturn(1).when(matchingRecordMapper).updateById((MatchingRecord) any());

            // Locking an already-locked record should succeed (sets isLocked=1 again)
            service.lockResult(RECORD_ID);

            assertThat(record.getIsLocked()).isEqualTo(1);
        }

        @Test
        @DisplayName("Lock sets lockedTime to non-null")
        void lock_setsLockedTime() {
            MatchingRecord record = buildRecord();
            record.setIsLocked(0);
            when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
            doReturn(1).when(matchingRecordMapper).updateById((MatchingRecord) any());

            service.lockResult(RECORD_ID);

            assertThat(record.getLockedTime()).isNotNull();
        }
    }

    // ========== Delete concurrent with getDetailById ==========

    @Nested
    @DisplayName("Delete concurrent with getDetailById")
    class DeleteConcurrentWithGetDetail {

        @Test
        @DisplayName("After delete, getDetailById returns null (no stale cache)")
        void afterDelete_getDetailReturnsNull() {
            // Simulate: record exists initially
            MatchingRecord record = buildRecord();
            when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);

            // First call returns the record
            when(dataQuery.batchLoadAbilitySnapshots(any())).thenReturn(java.util.Map.of());
            when(evidenceScoreCalculator.computeEvidenceScoreFromSnapshots(any())).thenReturn(BigDecimal.ZERO);
            when(dataQuery.findEmployeeForMatching(EMP_ID)).thenReturn(null);
            when(dataQuery.findPostForMatching(POST_ID)).thenReturn(null);

            MatchingRecord detail = service.getDetailById(RECORD_ID);
            assertThat(detail).isNotNull();

            // Simulate delete
            when(matchingRecordMapper.deleteById(RECORD_ID)).thenReturn(1);
            service.deleteRecord(RECORD_ID);

            // After delete, selectById returns null (simulating no stale cache)
            when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(null);

            MatchingRecord afterDelete = service.getDetailById(RECORD_ID);
            assertThat(afterDelete).isNull();
        }

        @Test
        @DisplayName("Delete non-existent record does not throw")
        void deleteNonExistent_doesNotThrow() {
            // deleteRecord calls removeById which delegates to mapper.deleteById
            // If record doesn't exist, deleteById returns 0, no exception
            when(matchingRecordMapper.deleteById(RECORD_ID)).thenReturn(0);

            // Should not throw - delete is idempotent
            service.deleteRecord(RECORD_ID);

            verify(matchingApprovalFlowMapper).delete(any());
            verify(feedbackDatasetMapper).delete(any());
        }

        @Test
        @DisplayName("getDetailById returns null when record not found")
        void getDetailById_notFound_returnsNull() {
            when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(null);

            MatchingRecord result = service.getDetailById(RECORD_ID);

            assertThat(result).isNull();
        }
    }

    // ========== Unlock then modify ==========

    @Nested
    @DisplayName("Unlock then modify")
    class UnlockThenModify {

        @Test
        @DisplayName("Unlock then modify succeeds")
        void unlock_thenModify_succeeds() {
            // Lock the record first
            MatchingRecord lockedRecord = buildRecord();
            lockedRecord.setIsLocked(1);
            lockedRecord.setLockedBy(99L);
            when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(lockedRecord);
            doReturn(1).when(matchingRecordMapper).updateById((MatchingRecord) any());

            service.unlockResult(RECORD_ID);

            assertThat(lockedRecord.getIsLocked()).isEqualTo(0);
            assertThat(lockedRecord.getLockedBy()).isNull();
            assertThat(lockedRecord.getLockedTime()).isNull();

            // Now modify should succeed
            MatchingRecord update = new MatchingRecord();
            update.setFinalMatchScore(new BigDecimal("92.00"));
            update.setMatchStatus(1);
            doReturn(1).when(feedbackDatasetMapper).insert((MatchingFeedbackDataset) any());

            service.modifyResult(RECORD_ID, update);

            assertThat(lockedRecord.getFinalMatchScore()).isEqualByComparingTo("92.00");
            assertThat(lockedRecord.getMatchStatus()).isEqualTo(1);
            verify(matchingRecordMapper, atLeast(2)).updateById((MatchingRecord) any());
        }

        @Test
        @DisplayName("Unlock clears all lock-related fields")
        void unlock_clearsAllLockFields() {
            MatchingRecord record = buildRecord();
            record.setIsLocked(1);
            record.setLockedBy(42L);
            record.setLockedTime(java.time.LocalDateTime.now());
            when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
            doReturn(1).when(matchingRecordMapper).updateById((MatchingRecord) any());

            service.unlockResult(RECORD_ID);

            assertThat(record.getIsLocked()).isEqualTo(0);
            assertThat(record.getLockedBy()).isNull();
            assertThat(record.getLockedTime()).isNull();
        }

        @Test
        @DisplayName("Modify after unlock preserves finalMatchScore through feedback creation")
        void modifyAfterUnlock_preservesFeedbackCreation() {
            MatchingRecord record = buildRecord();
            record.setIsLocked(1);
            when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
            doReturn(1).when(matchingRecordMapper).updateById((MatchingRecord) any());

            service.unlockResult(RECORD_ID);

            MatchingRecord update = new MatchingRecord();
            update.setFinalMatchScore(new BigDecimal("88.00"));
            update.setMatchStatus(2);
            doReturn(1).when(feedbackDatasetMapper).insert((MatchingFeedbackDataset) any());

            service.modifyResult(RECORD_ID, update);

            verify(feedbackDatasetMapper).insert((MatchingFeedbackDataset) any());
        }
    }

    // ========== Not-found scenarios ==========

    @Nested
    @DisplayName("Not-found scenarios")
    class NotFoundScenarios {

        @Test
        @DisplayName("Lock non-existent record throws BusinessException")
        void lockNonExistent_throws() {
            when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.lockResult(RECORD_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Unlock non-existent record throws BusinessException")
        void unlockNonExistent_throws() {
            when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.unlockResult(RECORD_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Modify non-existent record throws BusinessException")
        void modifyNonExistent_throws() {
            when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(null);

            MatchingRecord update = new MatchingRecord();
            update.setFinalMatchScore(new BigDecimal("80.00"));

            assertThatThrownBy(() -> service.modifyResult(RECORD_ID, update))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ========== Helpers ==========

    private MatchingRecord buildRecord() {
        MatchingRecord record = new MatchingRecord();
        record.setId(RECORD_ID);
        record.setEmpId(EMP_ID);
        record.setPostId(POST_ID);
        record.setAiMatchScore(new BigDecimal("78.00"));
        record.setMatchStatus(2);
        record.setApprovalStatus(0);
        record.setIsLocked(0);
        return record;
    }
}
