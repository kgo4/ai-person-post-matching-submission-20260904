package com.example.matching.service.matching.impl;

import com.example.matching.agent.service.impl.MatchScoringMemoryRuleApplier;
import com.example.matching.dto.matching.CandidateScope;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.matching.MatchingRecord;
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
import com.example.matching.service.matching.MatchingTaskService;
import com.example.matching.service.matching.MatchingTrainingWeightProfileStore;
import com.example.matching.service.matching.RagScoreService;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostHardConditionRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H4 行为测试：候选池默认 ALL_ACTIVE 不再被静默截断；
 * VECTOR_RECALL 为显式性能模式并标记 truncated；
 * 大规模全量任务自动转交异步 MatchingTask。
 */
class MatchingCandidateScopeTest {

    private MatchingAlgorithmService algorithmService;
    private EmployeeVectorRecallService vectorRecallService;
    private PostAbilityModelService postAbilityModelService;
    private PostHardConditionRuleService hardConditionRuleService;
    private FeedbackCalibrationService feedbackCalibrationService;
    private RagScoreService ragScoreService;
    private MatchingTrainingWeightProfileStore weightProfileStore;
    private MatchingDataQueryService dataQuery;
    private MatchingScoreService scoreService;
    private MatchingAiAnalysisService aiAnalysisService;
    private MatchingEvidenceScoreCalculator evidenceScoreCalculator;
    private MatchingRecordMapper recordMapper;
    private MatchingRecordPersistenceService persistenceService;
    private MatchingAiScoringStateMachine stateMachine;
    private MatchEvaluator matchEvaluator;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    private MatchingTaskService matchingTaskService;
    private MatchingExecuteServiceImpl service;

    private MatchingPostProfile post;

    @BeforeEach
    void setUp() {
        algorithmService = mock(MatchingAlgorithmService.class);
        vectorRecallService = mock(EmployeeVectorRecallService.class);
        postAbilityModelService = mock(PostAbilityModelService.class);
        hardConditionRuleService = mock(PostHardConditionRuleService.class);
        feedbackCalibrationService = mock(FeedbackCalibrationService.class);
        ragScoreService = mock(RagScoreService.class);
        weightProfileStore = mock(MatchingTrainingWeightProfileStore.class);
        dataQuery = mock(MatchingDataQueryService.class);
        scoreService = mock(MatchingScoreService.class);
        aiAnalysisService = mock(MatchingAiAnalysisService.class);
        evidenceScoreCalculator = mock(MatchingEvidenceScoreCalculator.class);
        recordMapper = mock(MatchingRecordMapper.class);
        persistenceService = mock(MatchingRecordPersistenceService.class);
        stateMachine = mock(MatchingAiScoringStateMachine.class);
        matchEvaluator = mock(MatchEvaluator.class);
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        matchingTaskService = mock(MatchingTaskService.class);

        service = new MatchingExecuteServiceImpl(
                algorithmService, vectorRecallService, postAbilityModelService, hardConditionRuleService,
                feedbackCalibrationService, ragScoreService, weightProfileStore,
                new ObjectMapper(), dataQuery, scoreService, aiAnalysisService, evidenceScoreCalculator,
                recordMapper, mock(com.example.matching.mapper.matching.MatchingTaskMapper.class), persistenceService, stateMachine, matchEvaluator, eventPublisher,
                new MatchExecutionScoringEngine(algorithmService, ragScoreService, matchEvaluator,
                        null, new ObjectMapper()),
                matchingTaskService);

        post = new MatchingPostProfile(2L, "P2", "Backend Engineer", null, null, null, List.of());

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
        when(weightProfileStore.currentProfile()).thenReturn(weightProfile);
        MatchEvaluator.EvaluatedMatch evaluated = new MatchEvaluator.EvaluatedMatch(
                l2Record, new BigDecimal("60.00"), new BigDecimal("80.00"),
                scoreResult, weightProfile);
        when(matchEvaluator.evaluate(any())).thenReturn(evaluated);
        when(matchEvaluator.determineStatus(any())).thenReturn(2);

        when(dataQuery.findPostForMatching(2L)).thenReturn(post);
        when(dataQuery.findPostRequirements(2L)).thenReturn(List.of(
                new MatchingRequirementSnapshot(
                        10L, "Java", 3, new BigDecimal("100.00"), 1, 1, "v1")));
        when(dataQuery.listBlackWhiteListByPostId(2L)).thenReturn(List.of());
        when(dataQuery.batchLoadResumeBasicInfo(anyList())).thenReturn(Map.of());
        // 候选池范围测试关注 scope/截断行为：为每个候选员工提供正式能力，
        // 避免触发"无正式能力"资格过滤（新规则：无正式能力不允许参与匹配）。
        when(dataQuery.batchLoadAbilitySnapshots(anyList())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) inv.getArgument(0);
            Map<Long, List<MatchingAbilitySnapshot>> map = new java.util.HashMap<>();
            for (Long id : ids) {
                map.put(id, List.of(new MatchingAbilitySnapshot(
                        null, id, null, 3, null, "EMP_ABILITY", null, null)));
            }
            return map;
        });
        when(postAbilityModelService.calculateQualityScore(2L)).thenReturn(new BigDecimal("90.00"));
        when(feedbackCalibrationService.calculateCalibration(2L)).thenReturn(BigDecimal.ZERO);
        when(ragScoreService.calculateRagScore(any(), any(), anyList(), anyList())).thenReturn(BigDecimal.ZERO);
        when(algorithmService.generateReport(any(), any(), any(), anyList(), anyList(), any()))
                .thenReturn("{\"ok\":true}");
    }

    private List<MatchingEmployeeProfile> employees(int count) {
        List<MatchingEmployeeProfile> list = new ArrayList<>(count);
        for (long id = 1; id <= count; id++) {
            list.add(new MatchingEmployeeProfile(id, "E" + id, "Emp" + id, null, null, null, List.of()));
        }
        return list;
    }

    private MatchingExecuteDTO dto(CandidateScope scope) {
        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setPostId(2L);
        dto.setCandidateScope(scope);
        return dto;
    }

    @Test
    void allActiveDefaultIncludesAll101EmployeesWithoutTruncation() {
        List<MatchingEmployeeProfile> all = employees(101);
        when(dataQuery.countAllActiveEmployees()).thenReturn(101L);
        when(dataQuery.findAllActiveEmployeesForMatching()).thenReturn(all);
        when(vectorRecallService.recallEmployeesForPost(post)).thenReturn(Map.of());

        MatchingExecuteResult result = service.execute(dto(CandidateScope.ALL_ACTIVE));

        // 101 名员工全部进入候选池，未被 LIMIT 500/向量 topK 截断
        assertThat(result.candidateScope()).isEqualTo(CandidateScope.ALL_ACTIVE);
        assertThat(result.candidateCount()).isEqualTo(101);
        assertThat(result.totalActiveCount()).isEqualTo(101L);
        assertThat(result.truncated()).isFalse();
        assertThat(result.records()).hasSize(101);
        verify(matchingTaskService, never()).submitTask(any());
    }

    @Test
    void vectorRecallScopeReturnsTop100AndMarksTruncated() {
        List<MatchingEmployeeProfile> recalled = employees(100);
        Map<Long, BigDecimal> vectorScores = new java.util.LinkedHashMap<>();
        LongStream.rangeClosed(1, 100).forEach(id -> vectorScores.put(id, new BigDecimal("0.80")));
        when(dataQuery.countAllActiveEmployees()).thenReturn(101L);
        when(vectorRecallService.recallEmployeesForPost(post)).thenReturn(vectorScores);
        when(dataQuery.findActiveEmployeesForMatching(anyList())).thenReturn(recalled);

        MatchingExecuteResult result = service.execute(dto(CandidateScope.VECTOR_RECALL));

        assertThat(result.candidateScope()).isEqualTo(CandidateScope.VECTOR_RECALL);
        assertThat(result.candidateCount()).isEqualTo(100);
        assertThat(result.truncated()).isTrue();
        assertThat(result.totalActiveCount()).isEqualTo(101L);
        verify(matchingTaskService, never()).submitTask(any());
    }

    @Test
    void allActiveOverThresholdDelegatesToAsyncTaskWithoutBlockingHttp() {
        List<MatchingEmployeeProfile> all = employees(501);
        when(dataQuery.countAllActiveEmployees()).thenReturn(501L);
        when(dataQuery.findAllActiveEmployeesForMatching()).thenReturn(all);
        when(vectorRecallService.recallEmployeesForPost(post)).thenReturn(Map.of());
        when(matchingTaskService.submitTask(any())).thenReturn("task-abc");

        MatchingExecuteResult result = service.execute(dto(CandidateScope.ALL_ACTIVE));

        ArgumentCaptor<MatchingExecuteDTO> dtoCaptor = ArgumentCaptor.forClass(MatchingExecuteDTO.class);
        verify(matchingTaskService).submitTask(dtoCaptor.capture());
        assertThat(result.isAsync()).isTrue();
        assertThat(result.taskId()).isEqualTo("task-abc");
        assertThat(result.records()).isEmpty();
        assertThat(result.candidateCount()).isEqualTo(501);
        assertThat(result.truncated()).isFalse();
    }
}

class MatchingRagWeightSkipTest {

    @org.junit.jupiter.api.Test
    void ragWeightZeroSkipsRagScoreService() {
        // M21：ragWeight==0 时 MatchExecutionScoringEngine 不调用 RagScoreService
        MatchingAlgorithmService algorithmService = mock(MatchingAlgorithmService.class);
        RagScoreService ragScoreService = mock(RagScoreService.class);
        MatchEvaluator matchEvaluator = mock(MatchEvaluator.class);
        MatchingTrainingWeightProfileStore.WeightProfile zeroRag = new MatchingTrainingWeightProfileStore.WeightProfile();
        zeroRag.setNoLlmAbilityWeight(0.55);
        zeroRag.setNoLlmSemanticWeight(0.25);
        zeroRag.setNoLlmEvidenceWeight(0.10);
        zeroRag.setWithLlmAbilityWeight(0.55);
        zeroRag.setWithLlmSemanticWeight(0.15);
        zeroRag.setWithLlmEvidenceWeight(0.10);
        zeroRag.setWithLlmLlmWeight(0.05);
        zeroRag.setRagWeight(0.0);
        zeroRag.setVersion("zero-rag");

        MatchingRecord l2Record = new MatchingRecord();
        l2Record.setL2Score(new BigDecimal("70.00"));
        l2Record.setPostModelScore(new BigDecimal("70.00"));
        l2Record.setVectorScore(new BigDecimal("80.00"));
        MatchScoreResult scoreResult = new MatchScoreResult(
                new BigDecimal("77.00"), new BigDecimal("1.00"), new BigDecimal("2.00"),
                new BigDecimal("3.00"), new BigDecimal("83.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, false, "test");
        MatchEvaluator.EvaluatedMatch evaluated = new MatchEvaluator.EvaluatedMatch(
                l2Record, new BigDecimal("60.00"), new BigDecimal("80.00"),
                scoreResult, zeroRag);
        when(matchEvaluator.evaluate(any())).thenReturn(evaluated);
        when(matchEvaluator.determineStatus(any())).thenReturn(2);
        when(algorithmService.generateReport(any(), any(), any(), anyList(), anyList(), any()))
                .thenReturn("{\"ok\":true}");

        com.example.matching.agent.service.impl.MatchScoringMemoryRuleApplier memoryRuleApplier =
                mock(com.example.matching.agent.service.impl.MatchScoringMemoryRuleApplier.class);
        when(memoryRuleApplier.apply(any(), any(), any(), any()))
                .thenReturn(com.example.matching.agent.service.impl.MatchScoringMemoryRuleApplier.MemoryApplyResult.noop());
        MatchExecutionScoringEngine engine = new MatchExecutionScoringEngine(
                algorithmService, ragScoreService, matchEvaluator, memoryRuleApplier,
                new ObjectMapper());

        MatchingPostProfile post = new MatchingPostProfile(2L, "P2", "Backend", null, null, null, List.of());
        MatchExecutionScoringEngine.MatchContext matchContext = new MatchExecutionScoringEngine.MatchContext(
                "B1", new MatchingEmployeeProfile(1L, "E1", "Emp1", null, null, null, List.of()),
                post, "v1", new BigDecimal("90.00"), BigDecimal.ZERO, List.of(), Map.of(),
                List.of(), List.of(), List.of(), Map.of(), Map.of(), false, zeroRag);

        engine.buildScoredRecord(matchContext);

        // ragWeight=0：RagScoreService 零调用
        org.mockito.Mockito.verify(ragScoreService, org.mockito.Mockito.never())
                .calculateRagScore(any(), any(), any(), any());
    }

    @org.junit.jupiter.api.Test
    void ragWeightPositiveStillDoesNotInvokeRagScoreService() {
        // RAG 已退出正式排名；权重保留仅用于旧配置兼容，执行引擎不得调用 RAG 服务。
        MatchingAlgorithmService algorithmService = mock(MatchingAlgorithmService.class);
        RagScoreService ragScoreService = mock(RagScoreService.class);
        MatchEvaluator matchEvaluator = mock(MatchEvaluator.class);
        MatchingTrainingWeightProfileStore.WeightProfile profile = new MatchingTrainingWeightProfileStore.WeightProfile();
        profile.setNoLlmAbilityWeight(0.55);
        profile.setNoLlmSemanticWeight(0.25);
        profile.setNoLlmEvidenceWeight(0.10);
        profile.setWithLlmAbilityWeight(0.55);
        profile.setWithLlmSemanticWeight(0.15);
        profile.setWithLlmEvidenceWeight(0.10);
        profile.setWithLlmLlmWeight(0.05);
        profile.setRagWeight(0.1);
        profile.setVersion("rag-0.1");
        when(ragScoreService.calculateRagScore(any(), any(), anyList(), anyList()))
                .thenReturn(BigDecimal.ZERO);

        MatchingRecord l2Record = new MatchingRecord();
        l2Record.setL2Score(new BigDecimal("70.00"));
        l2Record.setPostModelScore(new BigDecimal("70.00"));
        l2Record.setVectorScore(new BigDecimal("80.00"));
        MatchScoreResult scoreResult = new MatchScoreResult(
                new BigDecimal("77.00"), new BigDecimal("1.00"), new BigDecimal("2.00"),
                new BigDecimal("3.00"), new BigDecimal("83.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, false, "test");
        MatchEvaluator.EvaluatedMatch evaluated = new MatchEvaluator.EvaluatedMatch(
                l2Record, new BigDecimal("60.00"), new BigDecimal("80.00"),
                scoreResult, profile);
        when(matchEvaluator.evaluate(any())).thenReturn(evaluated);
        when(matchEvaluator.determineStatus(any())).thenReturn(2);
        when(algorithmService.generateReport(any(), any(), any(), anyList(), anyList(), any()))
                .thenReturn("{\"ok\":true}");

        com.example.matching.agent.service.impl.MatchScoringMemoryRuleApplier memoryRuleApplier =
                mock(com.example.matching.agent.service.impl.MatchScoringMemoryRuleApplier.class);
        when(memoryRuleApplier.apply(any(), any(), any(), any()))
                .thenReturn(com.example.matching.agent.service.impl.MatchScoringMemoryRuleApplier.MemoryApplyResult.noop());
        MatchExecutionScoringEngine engine = new MatchExecutionScoringEngine(
                algorithmService, ragScoreService, matchEvaluator, memoryRuleApplier,
                new ObjectMapper());

        MatchingPostProfile post = new MatchingPostProfile(2L, "P2", "Backend", null, null, null, List.of());
        MatchExecutionScoringEngine.MatchContext matchContext = new MatchExecutionScoringEngine.MatchContext(
                "B1", new MatchingEmployeeProfile(1L, "E1", "Emp1", null, null, null, List.of()),
                post, "v1", new BigDecimal("90.00"), BigDecimal.ZERO, List.of(), Map.of(),
                List.of(), List.of(), List.of(), Map.of(), Map.of(), false, profile);

        engine.buildScoredRecord(matchContext);

        org.mockito.Mockito.verify(ragScoreService, org.mockito.Mockito.never())
                .calculateRagScore(any(), any(), any(), any());
    }
}

