package com.example.matching.service.matching;

import com.example.matching.agent.service.MatchingAnalysisAgentService;
import com.example.matching.ai.service.AiMatchingService;
import com.example.matching.common.constant.AiConstant;
import com.example.matching.common.exception.AiServiceException;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.resilience.AiServiceResilience;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MatchingAiAnalysisServiceTest {

    @Test
    void runAiScoringProcessesExplicitlyEnabledCandidateBelowL2Threshold() {
        AiMatchingService aiMatchingService = mock(AiMatchingService.class);
        MatchingAnalysisAgentService analysisAgentService = mock(MatchingAnalysisAgentService.class);
        AiServiceResilience resilience = mock(AiServiceResilience.class);
        MatchingAlgorithmService algorithmService = mock(MatchingAlgorithmService.class);
        MatchingScoreService scoreService = mock(MatchingScoreService.class);
        MatchingTrainingWeightProfileStore weightProfileStore = mock(MatchingTrainingWeightProfileStore.class);
        MatchingAiScoringStateMachine stateMachine = mock(MatchingAiScoringStateMachine.class);
        MatchingEvidenceScoreCalculator evidenceScoreCalculator = mock(MatchingEvidenceScoreCalculator.class);
        when(stateMachine.claimForProcessing(any())).thenReturn(true);
        when(stateMachine.completeIfProcessing(any(), any())).thenReturn(true);
        when(evidenceScoreCalculator.computeEvidenceScoreFromSnapshots(anyList())).thenReturn(BigDecimal.ZERO);

        MatchingAiAnalysisService service = new MatchingAiAnalysisService(
                aiMatchingService, analysisAgentService, resilience, new ObjectMapper(), Runnable::run,
                stateMachine, evidenceScoreCalculator,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        MatchingAiAnalysisService spy = spy(service);

        MatchingRecord record = new MatchingRecord();
        record.setId(1L);
        record.setEmpId(2L);
        record.setScreeningLevel(2);
        record.setL2Score(new BigDecimal("55.00"));
        record.setPostModelScore(new BigDecimal("55.00"));
        record.setAiMatchScore(new BigDecimal("55.00"));

        doReturn(Map.of("aiScore", new BigDecimal("75.00"), "aiReport", "test report"))
                .when(spy).generateAiScore(1L);
        when(weightProfileStore.currentProfile())
                .thenReturn(MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile());
        when(scoreService.score(any())).thenReturn(new MatchScoreResult(
                new BigDecimal("75.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("75.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, true, MatchScoreResult.CURRENT_VERSION));

        spy.runAiScoring(List.of(record), Map.of(), Map.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                Map.of(), new MatchingPostProfile(null, null, null, null, null, null, List.of()), List.of(), Map.of(), 10, 70, true,
                algorithmService, scoreService, weightProfileStore);

        // L3 scoring should set aiMatchScore
        org.assertj.core.api.Assertions.assertThat(record.getScreeningLevel()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(record.getAiMatchScore()).isEqualByComparingTo("75.00");
        org.assertj.core.api.Assertions.assertThat(record.getScoreBreakdownJson())
                .contains("scoreCompositionVersion")
                .contains("MATCH_SCORE_V2")
                .doesNotContain("layerScores")
                .doesNotContain("layerWeights");
    }

    @Test
    void runAiScoringSkipsHardConditionFailedCandidateEvenWhenForced() {
        MatchingAiScoringStateMachine stateMachine = mock(MatchingAiScoringStateMachine.class);
        when(stateMachine.claimForProcessing(1L)).thenReturn(true);
        MatchingAiAnalysisService service = new MatchingAiAnalysisService(
                mock(AiMatchingService.class), mock(MatchingAnalysisAgentService.class),
                mock(AiServiceResilience.class), new ObjectMapper(), Runnable::run,
                stateMachine, mock(MatchingEvidenceScoreCalculator.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        MatchingAiAnalysisService spy = spy(service);

        MatchingRecord record = new MatchingRecord();
        record.setId(1L);
        record.setEmpId(2L);
        record.setScreeningLevel(1);
        record.setL2Score(new BigDecimal("55.00"));
        record.setAiScoringStatus(AiConstant.AI_SCORING_PENDING);

        spy.runAiScoring(List.of(record), Map.of(), Map.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                Map.of(), new MatchingPostProfile(null, null, null, null, null, null, List.of()), List.of(), Map.of(), 10, 70, true,
                mock(MatchingAlgorithmService.class), mock(MatchingScoreService.class),
                mock(MatchingTrainingWeightProfileStore.class));

        // forceAi=true must NOT bypass hard-condition failure: screeningLevel=1 is excluded
        verify(spy, never()).generateAiScore(1L);
        verify(stateMachine).skipIfPending(1L);
    }

    @Test
    void runAiScoringSkipsCandidateBelowL2ThresholdByDefault() {
        MatchingAiScoringStateMachine stateMachine = mock(MatchingAiScoringStateMachine.class);
        when(stateMachine.claimForProcessing(1L)).thenReturn(true);
        MatchingAiAnalysisService service = new MatchingAiAnalysisService(
                mock(AiMatchingService.class), mock(MatchingAnalysisAgentService.class),
                mock(AiServiceResilience.class), new ObjectMapper(), Runnable::run,
                stateMachine, mock(MatchingEvidenceScoreCalculator.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        MatchingAiAnalysisService spy = spy(service);

        MatchingRecord record = new MatchingRecord();
        record.setId(1L);
        record.setEmpId(2L);
        record.setScreeningLevel(2);
        record.setL2Score(new BigDecimal("55.00"));
        record.setAiScoringStatus(AiConstant.AI_SCORING_PENDING);

        spy.runAiScoring(List.of(record), Map.of(), Map.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                Map.of(), new MatchingPostProfile(null, null, null, null, null, null, List.of()), List.of(), Map.of(), 10, 70,
                mock(MatchingAlgorithmService.class), mock(MatchingScoreService.class),
                mock(MatchingTrainingWeightProfileStore.class));

        verify(spy, never()).generateAiScore(1L);
        verify(stateMachine).skipIfPending(1L);
    }

    @Test
    void runAiScoringProcessesEligibleCandidatesConcurrently() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            MatchingScoreService scoreService = mock(MatchingScoreService.class);
            MatchingTrainingWeightProfileStore weightProfileStore = mock(MatchingTrainingWeightProfileStore.class);
            MatchingAiScoringStateMachine stateMachine = mock(MatchingAiScoringStateMachine.class);
            MatchingEvidenceScoreCalculator evidenceScoreCalculator = mock(MatchingEvidenceScoreCalculator.class);
            when(stateMachine.claimForProcessing(any())).thenReturn(true);
            when(evidenceScoreCalculator.computeEvidenceScoreFromSnapshots(anyList())).thenReturn(BigDecimal.ZERO);

            MatchingAiAnalysisService service = new MatchingAiAnalysisService(
                    mock(AiMatchingService.class), mock(MatchingAnalysisAgentService.class),
                    mock(AiServiceResilience.class), new ObjectMapper(), executor, stateMachine, evidenceScoreCalculator,
                    new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
            MatchingAiAnalysisService spy = spy(service);

            AtomicInteger inFlight = new AtomicInteger();
            AtomicInteger maxInFlight = new AtomicInteger();
            doAnswer(invocation -> {
                int current = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(current, Math::max);
                try {
                    Thread.sleep(150);
                    return Map.of("aiScore", new BigDecimal("75.00"), "aiReport", "test report");
                } finally {
                    inFlight.decrementAndGet();
                }
            }).when(spy).generateAiScore(any());
            when(weightProfileStore.currentProfile())
                    .thenReturn(MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile());
            when(scoreService.score(any())).thenReturn(new MatchScoreResult(
                    new BigDecimal("75.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("75.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, true, MatchScoreResult.CURRENT_VERSION));

            spy.runAiScoring(List.of(record(1L, 11L), record(2L, 12L)), Map.of(), Map.of(),
                    BigDecimal.ZERO, BigDecimal.ZERO, Map.of(), new MatchingPostProfile(null, null, null, null, null, null, List.of()), List.of(), Map.of(),
                    10, 60, mock(MatchingAlgorithmService.class), scoreService, weightProfileStore);

            org.assertj.core.api.Assertions.assertThat(maxInFlight.get()).isGreaterThanOrEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void generateAiScoreRejectsDegradedResult() {
        AiServiceResilience resilience = mock(AiServiceResilience.class);
        when(resilience.callWithResilience(anyString(), any(), anyString()))
                .thenReturn("{\"aiScore\":null,\"aiReport\":\"AI unavailable\",\"degraded\":true}");

        MatchingAiAnalysisService service = new MatchingAiAnalysisService(
                mock(AiMatchingService.class), mock(MatchingAnalysisAgentService.class),
                resilience, new ObjectMapper(), Runnable::run,
                mock(MatchingAiScoringStateMachine.class), mock(MatchingEvidenceScoreCalculator.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        assertThrows(AiServiceException.class, () -> service.generateAiScore(1L));
    }

    @Test
    void degradedResultDoesNotCallCompleteIfProcessing() {
        AiServiceResilience resilience = mock(AiServiceResilience.class);
        MatchingAiScoringStateMachine stateMachine = mock(MatchingAiScoringStateMachine.class);
        MatchingEvidenceScoreCalculator evidenceScoreCalculator = mock(MatchingEvidenceScoreCalculator.class);
        MatchingScoreService scoreService = mock(MatchingScoreService.class);
        MatchingTrainingWeightProfileStore weightProfileStore = mock(MatchingTrainingWeightProfileStore.class);

        when(resilience.callWithResilience(anyString(), any(), anyString()))
                .thenReturn("{\"aiScore\":null,\"aiReport\":\"AI unavailable\",\"degraded\":true}");
        when(stateMachine.claimForProcessing(any())).thenReturn(true);
        when(stateMachine.failIfProcessing(any(), anyString(), anyInt())).thenReturn(true);
        when(evidenceScoreCalculator.computeEvidenceScoreFromSnapshots(anyList())).thenReturn(BigDecimal.ZERO);
        when(weightProfileStore.currentProfile())
                .thenReturn(MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile());
        when(scoreService.score(any())).thenReturn(new MatchScoreResult(
                new BigDecimal("80.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("80.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, true, MatchScoreResult.CURRENT_VERSION));

        MatchingAiAnalysisService service = new MatchingAiAnalysisService(
                mock(AiMatchingService.class), mock(MatchingAnalysisAgentService.class),
                resilience, new ObjectMapper(), Runnable::run,
                stateMachine, evidenceScoreCalculator,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        MatchingRecord record = new MatchingRecord();
        record.setId(1L);
        record.setEmpId(2L);
        record.setScreeningLevel(2);
        record.setL2Score(new BigDecimal("80.00"));
        record.setPostModelScore(new BigDecimal("80.00"));

        MatchingAlgorithmService algorithmService = mock(MatchingAlgorithmService.class);

        service.scoreClaimedCandidate(record, Map.of(), Map.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                Map.of(), new MatchingPostProfile(null, null, null, null, null, null, List.of()), List.of(), Map.of(),
                algorithmService, scoreService, weightProfileStore);

        verify(stateMachine, never()).completeIfProcessing(any(), any());
        verify(stateMachine, times(1)).failIfProcessing(any(), anyString(), anyInt());
    }

    private MatchingRecord record(Long id, Long empId) {
        MatchingRecord record = new MatchingRecord();
        record.setId(id);
        record.setEmpId(empId);
        record.setScreeningLevel(2);
        record.setL2Score(new BigDecimal("80.00"));
        record.setPostModelScore(new BigDecimal("80.00"));
        return record;
    }

    @Test
    void runAiScoringProcessesMemoryExcludedCandidateWhenForced() {
        // 记忆规则 MATCH_EXCLUDE 排除的记录 screeningLevel=null；
        // force=true 时应进入 L3 AI 分析（结果仅作展示，matchStatus=4 排除语义不变）
        MatchingAiScoringStateMachine stateMachine = mock(MatchingAiScoringStateMachine.class);
        when(stateMachine.claimForProcessing(any())).thenReturn(true);
        when(stateMachine.completeIfProcessing(any(), any())).thenReturn(true);
        MatchingAiAnalysisService service = new MatchingAiAnalysisService(
                mock(AiMatchingService.class), mock(MatchingAnalysisAgentService.class),
                mock(AiServiceResilience.class), new ObjectMapper(), Runnable::run,
                stateMachine, mock(MatchingEvidenceScoreCalculator.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        MatchingAiAnalysisService spy = spy(service);

        MatchingRecord record = new MatchingRecord();
        record.setId(1L);
        record.setEmpId(2L);
        record.setScreeningLevel(null); // memory-excluded
        record.setL2Score(new BigDecimal("55.00"));
        record.setAiScoringStatus(AiConstant.AI_SCORING_PENDING);

        doReturn(Map.of("aiScore", new BigDecimal("75.00"), "aiReport", "test report"))
                .when(spy).generateAiScore(1L);
        MatchingScoreService scoreService = mock(MatchingScoreService.class);
        when(scoreService.score(any())).thenReturn(new MatchScoreResult(
                new BigDecimal("75.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("75.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, true, MatchScoreResult.CURRENT_VERSION));

        spy.runAiScoring(List.of(record), Map.of(), Map.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                Map.of(), new MatchingPostProfile(null, null, null, null, null, null, List.of()), List.of(), Map.of(), 10, 70, true,
                mock(MatchingAlgorithmService.class), scoreService,
                mock(MatchingTrainingWeightProfileStore.class));

        // force=true 时 screeningLevel=null（memory-excluded）应被调用 AI 评分
        verify(spy, times(1)).generateAiScore(1L);
    }

    @Test
    void runAiScoringSkipsMemoryExcludedCandidateWithoutForce() {
        // 非 force 模式下 memory-excluded（screeningLevel=null）仍被排除，行为不变
        MatchingAiScoringStateMachine stateMachine = mock(MatchingAiScoringStateMachine.class);
        when(stateMachine.claimForProcessing(1L)).thenReturn(true);
        MatchingAiAnalysisService service = new MatchingAiAnalysisService(
                mock(AiMatchingService.class), mock(MatchingAnalysisAgentService.class),
                mock(AiServiceResilience.class), new ObjectMapper(), Runnable::run,
                stateMachine, mock(MatchingEvidenceScoreCalculator.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        MatchingAiAnalysisService spy = spy(service);

        MatchingRecord record = new MatchingRecord();
        record.setId(1L);
        record.setEmpId(2L);
        record.setScreeningLevel(null);
        record.setL2Score(new BigDecimal("80.00"));
        record.setAiScoringStatus(AiConstant.AI_SCORING_PENDING);

        spy.runAiScoring(List.of(record), Map.of(), Map.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                Map.of(), new MatchingPostProfile(null, null, null, null, null, null, List.of()), List.of(), Map.of(), 10, 70, false,
                mock(MatchingAlgorithmService.class), mock(MatchingScoreService.class),
                mock(MatchingTrainingWeightProfileStore.class));

        verify(spy, never()).generateAiScore(1L);
        verify(stateMachine).skipIfPending(1L);
    }
}

