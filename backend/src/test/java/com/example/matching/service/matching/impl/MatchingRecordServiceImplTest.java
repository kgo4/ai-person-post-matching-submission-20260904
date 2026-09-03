package com.example.matching.service.matching.impl;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostPost;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.mapper.matching.MatchingApprovalFlowMapper;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.matching.*;
import com.example.matching.dto.matching.CandidateScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingRecordServiceImplTest {

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

    // ==================== modifyResult ====================

    @Test
    void modifyResult_updatesRecordSuccessfully() {
        MatchingRecord record = buildRecord();
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        doReturn(1).when(matchingRecordMapper).updateById(any(MatchingRecord.class));
        doReturn(1).when(feedbackDatasetMapper).insert(any(MatchingFeedbackDataset.class));

        MatchingRecord update = new MatchingRecord();
        update.setFinalMatchScore(new BigDecimal("88.00"));
        update.setMatchStatus(1);
        update.setManualRemark("表现优秀");

        service.modifyResult(RECORD_ID, update);

        assertThat(record.getFinalMatchScore()).isEqualByComparingTo("88.00");
        assertThat(record.getMatchStatus()).isEqualTo(1);
        assertThat(record.getManualRemark()).isEqualTo("表现优秀");
        verify(matchingRecordMapper).updateById(record);
        verify(feedbackDatasetMapper).insert(any(MatchingFeedbackDataset.class));
    }

    @Test
    void modifyResult_throwsWhenRecordNotFound() {
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(null);

        MatchingRecord update = new MatchingRecord();
        update.setFinalMatchScore(new BigDecimal("88.00"));

        assertThatThrownBy(() -> service.modifyResult(RECORD_ID, update))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void modifyResult_throwsWhenRecordIsLocked() {
        MatchingRecord record = buildRecord();
        record.setIsLocked(1);
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);

        MatchingRecord update = new MatchingRecord();
        update.setFinalMatchScore(new BigDecimal("88.00"));

        assertThatThrownBy(() -> service.modifyResult(RECORD_ID, update))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void modifyResult_createsFeedbackWithAdoptionStatus1WhenScoresClose() {
        MatchingRecord record = buildRecord();
        record.setAiMatchScore(new BigDecimal("85.00"));
        record.setMatchStatus(2);
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        doReturn(1).when(matchingRecordMapper).updateById(any(MatchingRecord.class));
        doReturn(1).when(feedbackDatasetMapper).insert(any(MatchingFeedbackDataset.class));

        MatchingRecord update = new MatchingRecord();
        update.setFinalMatchScore(new BigDecimal("86.00"));
        update.setMatchStatus(2);

        service.modifyResult(RECORD_ID, update);

        verify(feedbackDatasetMapper).insert(argThat((MatchingFeedbackDataset feedback) ->
                feedback.getAdoptionStatus() == 1
        ));
    }

    @Test
    void modifyResult_createsFeedbackWithAdoptionStatus3WhenScoresDiverge() {
        MatchingRecord record = buildRecord();
        record.setAiMatchScore(new BigDecimal("85.00"));
        record.setMatchStatus(2);
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        doReturn(1).when(matchingRecordMapper).updateById(any(MatchingRecord.class));
        doReturn(1).when(feedbackDatasetMapper).insert(any(MatchingFeedbackDataset.class));

        MatchingRecord update = new MatchingRecord();
        update.setFinalMatchScore(new BigDecimal("50.00"));
        update.setMatchStatus(4);

        service.modifyResult(RECORD_ID, update);

        verify(feedbackDatasetMapper).insert(argThat((MatchingFeedbackDataset feedback) ->
                feedback.getAdoptionStatus() == 3
        ));
    }

    @Test
    void modifyResult_failsWhenFeedbackPersistenceFails() {
        MatchingRecord record = buildRecord();
        record.setAiMatchScore(new BigDecimal("85.00"));
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        doReturn(1).when(matchingRecordMapper).updateById(any(MatchingRecord.class));
        doThrow(new RuntimeException("feedback database unavailable"))
                .when(feedbackDatasetMapper).insert(any(MatchingFeedbackDataset.class));

        MatchingRecord update = new MatchingRecord();
        update.setFinalMatchScore(new BigDecimal("80.00"));

        assertThatThrownBy(() -> service.modifyResult(RECORD_ID, update))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("feedback database unavailable");
    }

    // ==================== M5: 反馈数据集幂等 ====================

    @Test
    void modifyResult_onlyRemarkChangeCreatesCalibrationSampleWithComment() {
        MatchingRecord record = buildRecord();
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        doReturn(1).when(matchingRecordMapper).updateById(any(MatchingRecord.class));
        doReturn(1).when(feedbackDatasetMapper).insert(any(MatchingFeedbackDataset.class));

        MatchingRecord update = new MatchingRecord();
        update.setManualRemark("仅改备注");

        service.modifyResult(RECORD_ID, update);

        verify(feedbackDatasetMapper).insert(argThat((MatchingFeedbackDataset feedback) ->
                "仅改备注".equals(feedback.getFeedbackComment())
        ));
    }

    @Test
    void modifyResult_repeatedFeedbackUpdatesSameSampleInsteadOfInsert() {
        MatchingRecord record = buildRecord();
        record.setAiMatchScore(new BigDecimal("85.00"));
        record.setMatchStatus(2);
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        doReturn(1).when(matchingRecordMapper).updateById(any(MatchingRecord.class));
        MatchingFeedbackDataset existing = new MatchingFeedbackDataset();
        existing.setId(99L);
        existing.setMatchingRecordId(RECORD_ID);
        existing.setFinalMatchScore(new BigDecimal("80.00"));
        when(feedbackDatasetMapper.selectOne(any())).thenReturn(existing);
        doReturn(1).when(feedbackDatasetMapper).updateById(any(MatchingFeedbackDataset.class));

        MatchingRecord update = new MatchingRecord();
        update.setFinalMatchScore(new BigDecimal("86.00"));
        update.setMatchStatus(2);

        service.modifyResult(RECORD_ID, update);
        service.modifyResult(RECORD_ID, update);

        // 同一反馈重复保存：只保留一条样本（更新而非新增）
        verify(feedbackDatasetMapper, never()).insert(any(MatchingFeedbackDataset.class));
        verify(feedbackDatasetMapper, times(2)).updateById(argThat((MatchingFeedbackDataset feedback) ->
                feedback.getId() == 99L
        ));
    }

    @Test
    void modifyResult_scoreChangeUpdatesExistingSampleInPlace() {
        MatchingRecord record = buildRecord();
        record.setAiMatchScore(new BigDecimal("85.00"));
        record.setMatchStatus(2);
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        doReturn(1).when(matchingRecordMapper).updateById(any(MatchingRecord.class));
        MatchingFeedbackDataset existing = new MatchingFeedbackDataset();
        existing.setId(99L);
        existing.setMatchingRecordId(RECORD_ID);
        when(feedbackDatasetMapper.selectOne(any())).thenReturn(existing);
        doReturn(1).when(feedbackDatasetMapper).updateById(any(MatchingFeedbackDataset.class));

        MatchingRecord update = new MatchingRecord();
        update.setFinalMatchScore(new BigDecimal("50.00"));
        update.setMatchStatus(4);

        service.modifyResult(RECORD_ID, update);

        // 修改最终分数 → 更新同一条样本（id 不变）
        verify(feedbackDatasetMapper).updateById(argThat((MatchingFeedbackDataset feedback) ->
                feedback.getId() == 99L
                        && feedback.getFinalMatchScore().compareTo(new BigDecimal("50.00")) == 0
                        && feedback.getFinalMatchStatus() == 4
        ));
    }

    // ==================== lockResult / unlockResult ====================

    @Test
    void lockResult_setsLockFields() {
        MatchingRecord record = buildRecord();
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        doReturn(1).when(matchingRecordMapper).updateById(any(MatchingRecord.class));

        service.lockResult(RECORD_ID);

        assertThat(record.getIsLocked()).isEqualTo(1);
        assertThat(record.getLockedTime()).isNotNull();
        verify(matchingRecordMapper).updateById(record);
    }

    @Test
    void lockResult_throwsWhenRecordNotFound() {
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.lockResult(RECORD_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unlockResult_clearsLockFields() {
        MatchingRecord record = buildRecord();
        record.setIsLocked(1);
        record.setLockedBy(99L);
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        doReturn(1).when(matchingRecordMapper).updateById(any(MatchingRecord.class));

        service.unlockResult(RECORD_ID);

        assertThat(record.getIsLocked()).isEqualTo(0);
        assertThat(record.getLockedBy()).isNull();
        assertThat(record.getLockedTime()).isNull();
        verify(matchingRecordMapper).updateById(record);
    }

    @Test
    void unlockResult_throwsWhenRecordNotFound() {
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.unlockResult(RECORD_ID))
                .isInstanceOf(BusinessException.class);
    }

    // ==================== generateReport ====================

    @Test
    void generateReport_returnsQuantitativeReport() {
        MatchingRecord record = buildRecord();
        record.setQuantitativeReport("{\"score\":85}");
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);

        String report = service.generateReport(RECORD_ID);

        assertThat(report).isEqualTo("{\"score\":85}");
    }

    @Test
    void generateReport_throwsWhenRecordNotFound() {
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.generateReport(RECORD_ID))
                .isInstanceOf(BusinessException.class);
    }

    // ==================== generateAiReport ====================

    @Test
    void generateAiReport_delegatesToAiAnalysisService() {
        when(matchingAiAnalysisService.generateAiReport(RECORD_ID)).thenReturn("{\"aiReport\":true}");

        String report = service.generateAiReport(RECORD_ID);

        assertThat(report).isEqualTo("{\"aiReport\":true}");
    }

    // ==================== deleteRecord ====================

    @Test
    void deleteRecord_removesRecordAndRelatedData() {
        when(matchingRecordMapper.deleteById(RECORD_ID)).thenReturn(1);

        service.deleteRecord(RECORD_ID);

        verify(matchingApprovalFlowMapper).delete(any());
        verify(feedbackDatasetMapper).delete(any());
        verify(matchingRecordMapper).deleteById(RECORD_ID);
    }

    @Test
    void recordMutations_evictAllRecordCacheEntriesIncludingPagedResults() throws Exception {
        for (String methodName : List.of("modifyResult", "lockResult", "unlockResult", "deleteRecord")) {
            Method method = Arrays.stream(MatchingRecordServiceImpl.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            CacheEvict[] evictions = method.isAnnotationPresent(Caching.class)
                    ? method.getAnnotation(Caching.class).evict()
                    : new CacheEvict[]{method.getAnnotation(CacheEvict.class)};

            assertThat(Arrays.stream(evictions)
                    .anyMatch(eviction -> RedisCacheNames.MATCHING_RECORD_PAGE.equals(eviction.cacheNames()[0])
                            && eviction.allEntries()))
                    .as("%s should evict paged matching-record cache entries", methodName)
                    .isTrue();
            assertThat(Arrays.stream(evictions)
                    .anyMatch(eviction -> RedisCacheNames.MATCHING_RECORD_DETAIL.equals(eviction.cacheNames()[0])
                            && eviction.allEntries()))
                    .as("%s should evict matching-record detail cache entries", methodName)
                    .isTrue();
        }
    }

    @Test
    void recordMutations_evictDashboardStatistics() {
        for (String methodName : List.of("executeMatching", "modifyResult", "lockResult", "unlockResult", "deleteRecord")) {
            Method method = Arrays.stream(MatchingRecordServiceImpl.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            CacheEvict[] evictions = method.isAnnotationPresent(Caching.class)
                    ? method.getAnnotation(Caching.class).evict()
                    : new CacheEvict[]{method.getAnnotation(CacheEvict.class)};

            assertThat(Arrays.stream(evictions)
                    .anyMatch(eviction -> RedisCacheNames.DASHBOARD_STATS.equals(eviction.cacheNames()[0])
                            && eviction.allEntries()))
                    .as("%s should evict dashboard statistics", methodName)
                    .isTrue();
        }
    }

    // ==================== getDetailById ====================

    @Test
    void getDetailById_returnsRecordWithEmpAndPostNames() {
        MatchingRecord record = buildRecord();
        record.setQuantitativeReport("{\"rankScore\":75.5}");
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);

        EmpEmployee emp = new EmpEmployee();
        emp.setId(EMP_ID);
        emp.setRealName("张三");
        when(dataQuery.findEmployeeForMatching(EMP_ID))
                .thenReturn(new com.example.matching.dto.matching.MatchingEmployeeProfile(
                        EMP_ID, "E001", "张三", null, null, null, List.of()));

        PostPost post = new PostPost();
        post.setId(POST_ID);
        post.setPostName("Java开发");
        when(dataQuery.findPostForMatching(POST_ID))
                .thenReturn(new com.example.matching.dto.matching.MatchingPostProfile(
                        POST_ID, "P001", "Java开发", null, null, null, List.of()));

        when(dataQuery.batchLoadAbilitySnapshots(any())).thenReturn(Map.of());
        when(evidenceScoreCalculator.computeEvidenceScoreFromSnapshots(any())).thenReturn(BigDecimal.ZERO);

        MatchingRecord result = service.getDetailById(RECORD_ID);

        assertThat(result).isNotNull();
        assertThat(result.getEmpName()).isEqualTo("张三");
        assertThat(result.getPostName()).isEqualTo("Java开发");
        assertThat(result.getRankScore()).isEqualByComparingTo("75.50");
    }

    @Test
    void getDetailById_returnsNullWhenNotFound() {
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(null);

        MatchingRecord result = service.getDetailById(RECORD_ID);

        assertThat(result).isNull();
    }

    @Test
    void pageRecordsLoadsPostNamesInOneBatch() {
        MatchingRecord firstRecord = buildRecord();
        firstRecord.setPostId(2L);
        MatchingRecord secondRecord = buildRecord();
        secondRecord.setId(101L);
        secondRecord.setPostId(3L);
        Page<MatchingRecord> databasePage = new Page<>(1, 10);
        databasePage.setRecords(List.of(firstRecord, secondRecord));
        when(matchingRecordMapper.selectPage(any(), any())).thenReturn(databasePage);

        EmpEmployee employee = new EmpEmployee();
        employee.setId(EMP_ID);
        employee.setRealName("Employee");
        when(dataQuery.findEmployeesForMatching(List.of(EMP_ID)))
                .thenReturn(List.of(new com.example.matching.dto.matching.MatchingEmployeeProfile(
                        EMP_ID, "E001", "Employee", null, null, null, List.of())));
        PostPost firstPost = new PostPost();
        firstPost.setId(2L);
        firstPost.setPostName("First post");
        PostPost secondPost = new PostPost();
        secondPost.setId(3L);
        secondPost.setPostName("Second post");
        when(dataQuery.findPostsForMatching(List.of(2L, 3L)))
                .thenReturn(List.of(
                        new com.example.matching.dto.matching.MatchingPostProfile(2L, "P2", "First post", null, null, null, List.of()),
                        new com.example.matching.dto.matching.MatchingPostProfile(3L, "P3", "Second post", null, null, null, List.of())));

        service.pageRecords(new Page<>(1, 10), null, null, null);

        assertThat(firstRecord.getPostName()).isEqualTo("First post");
        assertThat(secondRecord.getPostName()).isEqualTo("Second post");
        verify(dataQuery).findPostsForMatching(List.of(2L, 3L));
        verify(dataQuery, never()).findPostForMatching(any());
    }

    // ==================== executeMatching delegation ====================

    @Test
    void executeMatching_delegatesToMatchingExecuteService() {
        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setPostId(POST_ID);
        List<MatchingRecord> expectedRecords = List.of(buildRecord());
        when(matchingExecuteService.execute(dto))
                .thenReturn(MatchingExecuteResult.sync(expectedRecords, CandidateScope.ALL_ACTIVE, 1, 1, false));

        List<MatchingRecord> results = service.executeMatching(dto);

        assertThat(results).isSameAs(expectedRecords);
        verify(matchingExecuteService).execute(dto);
    }

    // ==================== helpers ====================

    private MatchingRecord buildRecord() {
        MatchingRecord record = new MatchingRecord();
        record.setId(RECORD_ID);
        record.setEmpId(EMP_ID);
        record.setPostId(POST_ID);
        record.setL2Score(new BigDecimal("75.00"));
        record.setAiMatchScore(new BigDecimal("78.00"));
        record.setMatchStatus(2);
        record.setApprovalStatus(0);
        record.setIsLocked(0);
        return record;
    }
}
