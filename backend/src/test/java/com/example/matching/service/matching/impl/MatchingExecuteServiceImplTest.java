package com.example.matching.service.matching.impl;

import com.example.matching.agent.service.impl.MatchScoringMemoryRuleApplier;
import com.example.matching.common.enums.MatchingTaskStatus;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.matching.MatchingTask;
import com.example.matching.mapper.matching.MatchingTaskMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.matching.EmployeeVectorRecallService;
import com.example.matching.service.matching.FeedbackCalibrationService;
import com.example.matching.service.matching.MatchEvaluator;
import com.example.matching.service.matching.MatchScoreResult;
import com.example.matching.service.matching.MatchingAiAnalysisService;
import com.example.matching.service.matching.MatchingAiScoringStateMachine;
import com.example.matching.service.matching.MatchingAlgorithmService;
import com.example.matching.service.matching.MatchingDataQueryService;
import com.example.matching.service.matching.MatchingEvidenceScoreCalculator;
import com.example.matching.service.matching.MatchingExecuteResult;
import com.example.matching.service.matching.MatchingRecordPersistenceService;
import com.example.matching.service.matching.MatchingScoreService;
import com.example.matching.service.matching.MatchingTrainingWeightProfileStore;
import com.example.matching.service.matching.MatchingTaskService;
import com.example.matching.service.matching.RagScoreService;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostHardConditionRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingExecuteServiceImplTest {

    @Test
    void execute_doesNotBlindlyPersistRecordsAfterAiScoring() {
        MatchingAlgorithmService algorithmService = mock(MatchingAlgorithmService.class);
        EmployeeVectorRecallService vectorRecallService = mock(EmployeeVectorRecallService.class);
        PostAbilityModelService postAbilityModelService = mock(PostAbilityModelService.class);
        PostHardConditionRuleService hardConditionRuleService = mock(PostHardConditionRuleService.class);
        FeedbackCalibrationService feedbackCalibrationService = mock(FeedbackCalibrationService.class);
        RagScoreService ragScoreService = mock(RagScoreService.class);
        MatchingTrainingWeightProfileStore weightProfileStore = mock(MatchingTrainingWeightProfileStore.class);
        MatchingDataQueryService dataQuery = mock(MatchingDataQueryService.class);
        MatchingScoreService scoreService = mock(MatchingScoreService.class);
        MatchingAiAnalysisService aiAnalysisService = mock(MatchingAiAnalysisService.class);
        MatchingEvidenceScoreCalculator evidenceScoreCalculator = mock(MatchingEvidenceScoreCalculator.class);
        MatchingRecordMapper recordMapper = mock(MatchingRecordMapper.class);
        MatchingRecordPersistenceService persistenceService = mock(MatchingRecordPersistenceService.class);
        MatchingAiScoringStateMachine stateMachine = mock(MatchingAiScoringStateMachine.class);
        MatchEvaluator matchEvaluator = mock(MatchEvaluator.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher =
                mock(org.springframework.context.ApplicationEventPublisher.class);
        MatchingTaskService matchingTaskService = mock(MatchingTaskService.class);

        MatchingExecuteServiceImpl service = new MatchingExecuteServiceImpl(
                algorithmService, vectorRecallService, postAbilityModelService, hardConditionRuleService,
                feedbackCalibrationService, ragScoreService, weightProfileStore,
                new ObjectMapper(), dataQuery, scoreService, aiAnalysisService, evidenceScoreCalculator,
                recordMapper, mock(com.example.matching.mapper.matching.MatchingTaskMapper.class), persistenceService, stateMachine, matchEvaluator, eventPublisher,
                new MatchExecutionScoringEngine(algorithmService, ragScoreService, matchEvaluator, null, new ObjectMapper()),
                matchingTaskService);

        MatchingEmployeeProfile employee = new MatchingEmployeeProfile(
                1L, "E001", "Alice", null, null, null, List.of());
        MatchingPostProfile post = new MatchingPostProfile(
                2L, "P2", "Backend Engineer", null, null, null, List.of());
        MatchingAbilitySnapshot ability = new MatchingAbilitySnapshot(
                null, 10L, null, null, null, null, null, null);

        MatchingRecord l2Record = new MatchingRecord();
        l2Record.setL2Score(new BigDecimal("70.00"));
        l2Record.setPostModelScore(new BigDecimal("70.00"));
        l2Record.setVectorScore(new BigDecimal("80.00"));
        MatchScoreResult scoreResult = new MatchScoreResult(
                new BigDecimal("77.00"), new BigDecimal("1.00"), new BigDecimal("2.00"),
                new BigDecimal("3.00"), new BigDecimal("83.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, false, "test");
        MatchingTrainingWeightProfileStore.WeightProfile weightProfile =
                MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile();
        weightProfile.setVersion("profile-test-v1");
        weightProfile.setRagWeight(0.1);
        when(weightProfileStore.currentProfile()).thenReturn(weightProfile);

        MatchEvaluator.EvaluatedMatch evaluated = new MatchEvaluator.EvaluatedMatch(
                l2Record, new BigDecimal("60.00"), new BigDecimal("80.00"),
                scoreResult, weightProfile);
        when(matchEvaluator.evaluate(any())).thenReturn(evaluated);
        when(matchEvaluator.determineStatus(any())).thenReturn(2);

        when(dataQuery.findPostForMatching(2L)).thenReturn(post);
        when(dataQuery.findPostRequirements(2L)).thenReturn(List.of(validRequirement()));
        when(dataQuery.listBlackWhiteListByPostId(2L)).thenReturn(List.of());
        when(dataQuery.countAllActiveEmployees()).thenReturn(1L);
        when(dataQuery.findEmployeesForMatching(List.of(1L))).thenReturn(List.of(employee));
        when(dataQuery.batchLoadResumeBasicInfo(List.of(1L))).thenReturn(Map.of());
        when(dataQuery.batchLoadAbilitySnapshots(List.of(1L))).thenReturn(Map.of(1L, List.of(ability)));
        when(postAbilityModelService.calculateQualityScore(2L)).thenReturn(new BigDecimal("90.00"));
        when(feedbackCalibrationService.calculateCalibration(2L)).thenReturn(new BigDecimal("4.00"));
        when(vectorRecallService.recallEmployeesForPost(post)).thenReturn(Map.of(1L, new BigDecimal("80.00")));
        when(ragScoreService.calculateRagScore(employee, post, List.of(ability), List.of())).thenReturn(new BigDecimal("50.00"));
        when(algorithmService.generateReport(any(), any(), any(), anyList(), anyList(), any())).thenReturn("{\"ok\":true}");

        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setPostId(2L);
        dto.setEmpIds(List.of(1L));
        dto.setEnableAiMatching(true);

        MatchingRecord result = service.execute(dto).records().get(0);

        assertThat(result.getL2Score()).isEqualByComparingTo("70.00");
        assertThat(result.getVectorScore()).isEqualByComparingTo("80.00");
        assertThat(result.getRagScore()).isNull();
        assertThat(result.getEvidenceScore()).isEqualByComparingTo("60.00");
        assertThat(result.getAiMatchScore()).isEqualByComparingTo("83.00");
        assertThat(result.getRankScore()).isEqualByComparingTo("83.00");
        assertThat(result.getWeightProfileVersion()).isEqualTo("profile-test-v1");
        assertThat(result.getWeightSnapshotJson())
                .contains("profile-test-v1")
                .contains("abilityWeight");
        assertThat(result.getScoreBreakdownJson())
                .contains("ability")
                .contains("rawScore")
                .contains("rankScore");
        assertThat(result.getQuantitativeReport()).isEqualTo("{\"ok\":true}");
        InOrder calls = inOrder(persistenceService, aiAnalysisService);
        calls.verify(persistenceService).saveAll(anyList());
        calls.verify(aiAnalysisService).runAiScoring(
                anyList(), anyMap(), anyMap(), any(), any(), anyMap(), any(), anyList(), anyMap(),
                anyInt(), anyInt(), anyBoolean(), any(), any(), any());
        verify(persistenceService, never()).updateAll(anyList());
    }

    // ==================== 新增：无正式能力不允许参与匹配（正式匹配必须基于正式能力画像） ====================

    private record TestHarness(
            MatchingExecuteServiceImpl service,
            MatchingDataQueryService dataQuery,
            EmployeeVectorRecallService vectorRecallService,
            PostAbilityModelService postAbilityModelService,
            FeedbackCalibrationService feedbackCalibrationService,
            RagScoreService ragScoreService,
            MatchingTrainingWeightProfileStore weightProfileStore,
            MatchEvaluator matchEvaluator,
            MatchingAlgorithmService algorithmService,
            MatchingRecordPersistenceService persistenceService,
            MatchingTaskMapper taskMapper) {
    }

    private static TestHarness buildHarness() {
        MatchingAlgorithmService algorithmService = mock(MatchingAlgorithmService.class);
        EmployeeVectorRecallService vectorRecallService = mock(EmployeeVectorRecallService.class);
        PostAbilityModelService postAbilityModelService = mock(PostAbilityModelService.class);
        PostHardConditionRuleService hardConditionRuleService = mock(PostHardConditionRuleService.class);
        FeedbackCalibrationService feedbackCalibrationService = mock(FeedbackCalibrationService.class);
        RagScoreService ragScoreService = mock(RagScoreService.class);
        MatchingTrainingWeightProfileStore weightProfileStore = mock(MatchingTrainingWeightProfileStore.class);
        MatchingDataQueryService dataQuery = mock(MatchingDataQueryService.class);
        MatchingScoreService scoreService = mock(MatchingScoreService.class);
        MatchingAiAnalysisService aiAnalysisService = mock(MatchingAiAnalysisService.class);
        MatchingEvidenceScoreCalculator evidenceScoreCalculator = mock(MatchingEvidenceScoreCalculator.class);
        MatchingRecordMapper recordMapper = mock(MatchingRecordMapper.class);
        MatchingRecordPersistenceService persistenceService = mock(MatchingRecordPersistenceService.class);
        MatchingAiScoringStateMachine stateMachine = mock(MatchingAiScoringStateMachine.class);
        MatchEvaluator matchEvaluator = mock(MatchEvaluator.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher =
                mock(org.springframework.context.ApplicationEventPublisher.class);
        MatchingTaskService matchingTaskService = mock(MatchingTaskService.class);
        MatchingTaskMapper taskMapper = mock(MatchingTaskMapper.class);

        MatchingExecuteServiceImpl service = new MatchingExecuteServiceImpl(
                algorithmService, vectorRecallService, postAbilityModelService, hardConditionRuleService,
                feedbackCalibrationService, ragScoreService, weightProfileStore,
                new ObjectMapper(), dataQuery, scoreService, aiAnalysisService, evidenceScoreCalculator,
                recordMapper, taskMapper,
                persistenceService, stateMachine, matchEvaluator, eventPublisher,
                new MatchExecutionScoringEngine(algorithmService, ragScoreService, matchEvaluator,
                        null, new ObjectMapper()),
                matchingTaskService);
        return new TestHarness(service, dataQuery, vectorRecallService, postAbilityModelService,
                feedbackCalibrationService, ragScoreService, weightProfileStore, matchEvaluator,
                algorithmService,
                persistenceService, taskMapper);
    }

    /** 通用评分链路 mock（weightProfile + matchEvaluator + ragScore + 报告生成）。 */
    private static void stubScoring(TestHarness h, MatchingPostProfile post) {
        MatchScoreResult scoreResult = new MatchScoreResult(
                new BigDecimal("77.00"), new BigDecimal("1.00"), new BigDecimal("2.00"),
                new BigDecimal("3.00"), new BigDecimal("83.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, false, "test");
        MatchingTrainingWeightProfileStore.WeightProfile weightProfile =
                MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile();
        weightProfile.setVersion("profile-test-v1");
        weightProfile.setRagWeight(0.1);
        when(h.weightProfileStore.currentProfile()).thenReturn(weightProfile);
        MatchingRecord l2Record = new MatchingRecord();
        l2Record.setL2Score(new BigDecimal("70.00"));
        l2Record.setPostModelScore(new BigDecimal("70.00"));
        l2Record.setVectorScore(new BigDecimal("80.00"));
        MatchEvaluator.EvaluatedMatch evaluated = new MatchEvaluator.EvaluatedMatch(
                l2Record, new BigDecimal("60.00"), new BigDecimal("80.00"), scoreResult, weightProfile);
        when(h.matchEvaluator.evaluate(any())).thenReturn(evaluated);
        when(h.matchEvaluator.determineStatus(any())).thenReturn(2);
        when(h.ragScoreService.calculateRagScore(any(), any(), anyList(), anyList()))
                .thenReturn(new BigDecimal("50.00"));
        when(h.algorithmService.generateReport(any(), any(), any(), anyList(), anyList(), any()))
                .thenReturn("{\"ok\":true}");
        when(h.postAbilityModelService.calculateQualityScore(post.postId()))
                .thenReturn(new BigDecimal("90.00"));
        when(h.feedbackCalibrationService.calculateCalibration(post.postId()))
                .thenReturn(new BigDecimal("4.00"));
    }

    private static MatchingRequirementSnapshot validRequirement() {
        return new MatchingRequirementSnapshot(
                10L, "Java", 3, new BigDecimal("100.00"), 1, 1, "v1");
    }

    @Test
    void execute_emptyPostModel_rejectedBeforeCandidateRecall() {
        TestHarness h = buildHarness();
        MatchingPostProfile post = new MatchingPostProfile(
                2L, "P2", "Backend Engineer", null, null, null, List.of());
        when(h.dataQuery.findPostForMatching(2L)).thenReturn(post);
        when(h.dataQuery.findPostRequirements(2L)).thenReturn(List.of());

        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setPostId(2L);
        dto.setEmpIds(List.of(1L));

        assertThatThrownBy(() -> h.service.execute(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("能力模型不完整")
                .hasMessageContaining("2");
        verify(h.vectorRecallService, never()).recallEmployeesForPost(any());
        verify(h.persistenceService, never()).saveAll(anyList());
    }

    @Test
    void executeByPairs_emptyPostModel_rejectsWholeRequestBeforeCandidateRecall() {
        TestHarness h = buildHarness();
        MatchingEmployeeProfile employee = new MatchingEmployeeProfile(
                1L, "E001", "Alice", null, null, null, List.of());
        MatchingPostProfile post = new MatchingPostProfile(
                2L, "P2", "Backend Engineer", null, null, null, List.of());
        MatchingAbilitySnapshot ability = new MatchingAbilitySnapshot(
                10L, 10L, "Java", 3, BigDecimal.ONE, "MANUAL", BigDecimal.ONE, null);
        when(h.dataQuery.findActiveEmployeesForMatching(List.of(1L))).thenReturn(List.of(employee));
        when(h.dataQuery.batchLoadResumeBasicInfo(List.of(1L))).thenReturn(Map.of());
        when(h.dataQuery.batchLoadAbilitySnapshots(List.of(1L))).thenReturn(Map.of(1L, List.of(ability)));
        when(h.dataQuery.findPostsForMatching(List.of(2L))).thenReturn(List.of(post));
        when(h.dataQuery.findPostRequirements(2L)).thenReturn(List.of());

        MatchingExecuteDTO.MatchingPair pair = new MatchingExecuteDTO.MatchingPair();
        pair.setEmpId(1L);
        pair.setPostId(2L);
        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setMode("SINGLE_EVAL");
        dto.setPairs(List.of(pair));

        assertThatThrownBy(() -> h.service.execute(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("能力模型不完整")
                .hasMessageContaining("2");
        verify(h.vectorRecallService, never()).recallEmployeesForPost(any());
        verify(h.persistenceService, never()).saveAll(anyList());
    }

    /** 显式指定员工无正式能力（且无强制匹配临时能力）→ 后端拒绝整个请求，不产生任何记录。 */
    @Test
    void execute_explicitEmployeeWithoutFormalAbility_rejected() {
        TestHarness h = buildHarness();
        MatchingEmployeeProfile employee = new MatchingEmployeeProfile(
                1L, "E001", "Alice", null, null, null, List.of());
        MatchingPostProfile post = new MatchingPostProfile(
                2L, "P2", "Backend Engineer", null, null, null, List.of());

        when(h.dataQuery.findPostForMatching(2L)).thenReturn(post);
        when(h.dataQuery.findPostRequirements(2L)).thenReturn(List.of(validRequirement()));
        when(h.dataQuery.listBlackWhiteListByPostId(2L)).thenReturn(List.of());
        when(h.dataQuery.countAllActiveEmployees()).thenReturn(1L);
        when(h.dataQuery.findEmployeesForMatching(List.of(1L))).thenReturn(List.of(employee));
        when(h.dataQuery.batchLoadResumeBasicInfo(List.of(1L))).thenReturn(Map.of());
        when(h.dataQuery.batchLoadAbilitySnapshots(List.of(1L))).thenReturn(Map.of()); // 无正式能力
        when(h.vectorRecallService.recallEmployeesForPost(post)).thenReturn(Map.of());

        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setPostId(2L);
        dto.setEmpIds(List.of(1L));

        assertThatThrownBy(() -> h.service.execute(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无正式能力");
        verify(h.persistenceService, never()).saveAll(anyList());
    }

    /** 全量匹配：无正式能力员工从候选池剔除（不产生匹配记录），结果暴露被排除数。 */
    @Test
    void execute_allActive_filtersEmployeesWithoutFormalAbility() {
        TestHarness h = buildHarness();
        MatchingEmployeeProfile empWithoutAbility = new MatchingEmployeeProfile(
                1L, "E001", "Alice", null, null, null, List.of());
        MatchingEmployeeProfile empWithAbility = new MatchingEmployeeProfile(
                2L, "E002", "Bob", null, null, null, List.of());
        MatchingPostProfile post = new MatchingPostProfile(
                2L, "P2", "Backend Engineer", null, null, null, List.of());
        MatchingAbilitySnapshot ability = new MatchingAbilitySnapshot(
                null, 10L, null, null, null, null, null, null);

        stubScoring(h, post);
        when(h.dataQuery.findPostForMatching(2L)).thenReturn(post);
        when(h.dataQuery.findPostRequirements(2L)).thenReturn(List.of(validRequirement()));
        when(h.dataQuery.listBlackWhiteListByPostId(2L)).thenReturn(List.of());
        when(h.dataQuery.countAllActiveEmployees()).thenReturn(2L);
        when(h.dataQuery.findAllActiveEmployeesForMatching())
                .thenReturn(List.of(empWithoutAbility, empWithAbility));
        when(h.dataQuery.batchLoadResumeBasicInfo(anyList())).thenReturn(Map.of());
        when(h.dataQuery.batchLoadAbilitySnapshots(anyList()))
                .thenReturn(Map.of(2L, List.of(ability)));
        when(h.vectorRecallService.recallEmployeesForPost(post)).thenReturn(Map.of());

        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setPostId(2L);

        MatchingExecuteResult result = h.service.execute(dto);

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(result.totalActiveCount()).isEqualTo(2L);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).getEmpId()).isEqualTo(2L);
    }

    /** 仅有待审核能力、没有正式能力的员工不能参与匹配。 */
    @Test
    void execute_employeeWithProvisionalOnly_rejected() {
        TestHarness h = buildHarness();
        MatchingEmployeeProfile employee = new MatchingEmployeeProfile(
                1L, "E001", "Alice", null, null, null, List.of());
        MatchingPostProfile post = new MatchingPostProfile(
                2L, "P2", "Backend Engineer", null, null, null, List.of());

        when(h.dataQuery.findPostForMatching(2L)).thenReturn(post);
        when(h.dataQuery.findPostRequirements(2L)).thenReturn(List.of(validRequirement()));
        when(h.dataQuery.listBlackWhiteListByPostId(2L)).thenReturn(List.of());
        when(h.dataQuery.countAllActiveEmployees()).thenReturn(1L);
        when(h.dataQuery.findEmployeesForMatching(List.of(1L))).thenReturn(List.of(employee));
        when(h.dataQuery.batchLoadResumeBasicInfo(List.of(1L))).thenReturn(Map.of());
        when(h.dataQuery.batchLoadAbilitySnapshots(List.of(1L))).thenReturn(new java.util.HashMap<>()); // 无正式能力

        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setPostId(2L);
        dto.setEmpIds(List.of(1L));
        assertThatThrownBy(() -> h.service.execute(dto))
                .isInstanceOf(BusinessException.class);
    }

    // ==================== 异步任务取消检查（isTaskAborted） ====================

    /**
     * 异步消费端执行时，任务存在且状态为 PENDING 不应被误判为"已取消/删除"，
     * 匹配必须正常产生记录。回归：isTaskAborted 曾用 selectById(taskId) 按主键查询
     * （主键是自增 id，taskId 是 UUID 字符串字段），永远查不到 → 任务被误判中止 → 0 条记录。
     */
    @Test
    void execute_asyncTaskPending_doesNotAbortMatching() {
        TestHarness h = buildHarness();
        String taskId = "fcbd8102acd6428f9e6745f5384f744c";

        MatchingTask pendingTask = new MatchingTask();
        pendingTask.setId(100L);
        pendingTask.setTaskId(taskId);
        pendingTask.setStatus(MatchingTaskStatus.PENDING.getCode());
        when(h.taskMapper.selectOne(any())).thenReturn(pendingTask);

        MatchingEmployeeProfile employee = new MatchingEmployeeProfile(
                1L, "E001", "Alice", null, null, null, List.of());
        MatchingPostProfile post = new MatchingPostProfile(
                2L, "P2", "Backend Engineer", null, null, null, List.of());
        MatchingAbilitySnapshot ability = new MatchingAbilitySnapshot(
                null, 10L, null, null, null, null, null, null);

        stubScoring(h, post);
        when(h.dataQuery.findPostForMatching(2L)).thenReturn(post);
        when(h.dataQuery.findPostRequirements(2L)).thenReturn(List.of(validRequirement()));
        when(h.dataQuery.listBlackWhiteListByPostId(2L)).thenReturn(List.of());
        when(h.dataQuery.countAllActiveEmployees()).thenReturn(1L);
        when(h.dataQuery.findEmployeesForMatching(List.of(1L))).thenReturn(List.of(employee));
        when(h.dataQuery.batchLoadResumeBasicInfo(List.of(1L))).thenReturn(Map.of());
        when(h.dataQuery.batchLoadAbilitySnapshots(List.of(1L))).thenReturn(Map.of(1L, List.of(ability)));
        when(h.vectorRecallService.recallEmployeesForPost(post)).thenReturn(Map.of(1L, new BigDecimal("80.00")));

        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setPostId(2L);
        dto.setEmpIds(List.of(1L));
        dto.setEnableAiMatching(false);
        dto.setTaskExecution(true);
        dto.setTaskId(taskId);

        MatchingExecuteResult result = h.service.execute(dto);

        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).getEmpId()).isEqualTo(1L);
        verify(h.taskMapper).selectOne(any());
        verify(h.persistenceService).saveAll(anyList());
    }

    /** 异步任务状态为 CANCELLED 时，执行应中止且不产生记录。 */
    @Test
    void execute_asyncTaskCancelled_abortsMatching() {
        TestHarness h = buildHarness();
        String taskId = "cancelled-task-uuid";

        MatchingTask cancelledTask = new MatchingTask();
        cancelledTask.setId(101L);
        cancelledTask.setTaskId(taskId);
        cancelledTask.setStatus(MatchingTaskStatus.CANCELLED.getCode());
        when(h.taskMapper.selectOne(any())).thenReturn(cancelledTask);

        MatchingEmployeeProfile employee = new MatchingEmployeeProfile(
                1L, "E001", "Alice", null, null, null, List.of());
        MatchingPostProfile post = new MatchingPostProfile(
                2L, "P2", "Backend Engineer", null, null, null, List.of());
        MatchingAbilitySnapshot ability = new MatchingAbilitySnapshot(
                null, 10L, null, null, null, null, null, null);

        stubScoring(h, post);
        when(h.dataQuery.findPostForMatching(2L)).thenReturn(post);
        when(h.dataQuery.findPostRequirements(2L)).thenReturn(List.of(validRequirement()));
        when(h.dataQuery.listBlackWhiteListByPostId(2L)).thenReturn(List.of());
        when(h.dataQuery.countAllActiveEmployees()).thenReturn(1L);
        when(h.dataQuery.findEmployeesForMatching(List.of(1L))).thenReturn(List.of(employee));
        when(h.dataQuery.batchLoadResumeBasicInfo(List.of(1L))).thenReturn(Map.of());
        when(h.dataQuery.batchLoadAbilitySnapshots(List.of(1L))).thenReturn(Map.of(1L, List.of(ability)));
        when(h.vectorRecallService.recallEmployeesForPost(post)).thenReturn(Map.of(1L, new BigDecimal("80.00")));

        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setPostId(2L);
        dto.setEmpIds(List.of(1L));
        dto.setEnableAiMatching(false);
        dto.setTaskExecution(true);
        dto.setTaskId(taskId);

        MatchingExecuteResult result = h.service.execute(dto);

        assertThat(result.records()).isEmpty();
    }

    /** 异步任务在库中不存在（已被删除）时，执行应中止且不产生记录。 */
    @Test
    void execute_asyncTaskDeleted_abortsMatching() {
        TestHarness h = buildHarness();
        String taskId = "deleted-task-uuid";

        when(h.taskMapper.selectOne(any())).thenReturn(null);

        MatchingEmployeeProfile employee = new MatchingEmployeeProfile(
                1L, "E001", "Alice", null, null, null, List.of());
        MatchingPostProfile post = new MatchingPostProfile(
                2L, "P2", "Backend Engineer", null, null, null, List.of());
        MatchingAbilitySnapshot ability = new MatchingAbilitySnapshot(
                null, 10L, null, null, null, null, null, null);

        stubScoring(h, post);
        when(h.dataQuery.findPostForMatching(2L)).thenReturn(post);
        when(h.dataQuery.findPostRequirements(2L)).thenReturn(List.of(validRequirement()));
        when(h.dataQuery.listBlackWhiteListByPostId(2L)).thenReturn(List.of());
        when(h.dataQuery.countAllActiveEmployees()).thenReturn(1L);
        when(h.dataQuery.findEmployeesForMatching(List.of(1L))).thenReturn(List.of(employee));
        when(h.dataQuery.batchLoadResumeBasicInfo(List.of(1L))).thenReturn(Map.of());
        when(h.dataQuery.batchLoadAbilitySnapshots(List.of(1L))).thenReturn(Map.of(1L, List.of(ability)));
        when(h.vectorRecallService.recallEmployeesForPost(post)).thenReturn(Map.of(1L, new BigDecimal("80.00")));

        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setPostId(2L);
        dto.setEmpIds(List.of(1L));
        dto.setEnableAiMatching(false);
        dto.setTaskExecution(true);
        dto.setTaskId(taskId);

        MatchingExecuteResult result = h.service.execute(dto);

        assertThat(result.records()).isEmpty();
    }
}
