package com.example.matching.service.matching.impl;

import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.example.matching.dto.matching.MatchDetailDTO;
import com.example.matching.entity.matching.MatchingBlackWhiteList;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.service.matching.MatchEvaluator;
import com.example.matching.service.matching.MatchScoreResult;
import com.example.matching.service.matching.MatchingAlgorithmService;
import com.example.matching.service.matching.MatchingTrainingWeightProfileStore;
import com.example.matching.agent.service.impl.MatchScoringMemoryRuleApplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchExecutionScoringEngineTest {

    private MatchingAlgorithmService matchingAlgorithmService;
    private MatchEvaluator matchEvaluator;
    private MatchExecutionScoringEngine engine;

    @BeforeEach
    void setUp() {
        matchingAlgorithmService = mock(MatchingAlgorithmService.class);
        matchEvaluator = mock(MatchEvaluator.class);
        engine = new MatchExecutionScoringEngine(
                matchingAlgorithmService,
                matchEvaluator,
                null,
                new ObjectMapper());
    }

    @Test
    void whitelistBypassReturnsForcedRecordWithoutHardConditionCheck() {
        MatchingRecord result = engine.buildScoredRecord(contextWithWhitelistAndFailedHardCondition(true));

        assertThat(result.getForcedByList()).isEqualTo(1);
        assertThat(result.getAiMatchScore()).isEqualByComparingTo("100");
        verify(matchingAlgorithmService, never()).checkHardConditions(any(), any(), any());
    }

    @Test
    void disabledWhitelistBypassRunsL1AndExcludesFailedCandidate() {
        MatchingRecord result = engine.buildScoredRecord(contextWithWhitelistAndFailedHardCondition(false));

        assertThat(result.getAiMatchScore()).isEqualByComparingTo("0");
        assertThat(result.getScreeningLevel()).isEqualTo(1);
        verify(matchingAlgorithmService).checkHardConditions(any(), any(), any());
    }

    @Test
    void blacklistWinsWhenBothEntriesMatch() {
        MatchingRecord result = engine.buildScoredRecord(contextWithBothListEntries());

        assertThat(result.getForcedByList()).isEqualTo(2);
        assertThat(result.getMatchStatus()).isEqualTo(4);
    }

    @Test
    void deterministicScoreUsesUnifiedDimensionsAndPersistsDimensionBreakdown() {
        MatchingRecord l2Record = new MatchingRecord();
        l2Record.setL2Score(new BigDecimal("70"));
        l2Record.setPostModelScore(new BigDecimal("70"));
        l2Record.setVectorScore(new BigDecimal("80"));
        l2Record.setMatchDetails(List.of(new MatchDetailDTO()));
        MatchingTrainingWeightProfileStore.WeightProfile profile =
                MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile();
        MatchScoreResult scoreResult = new MatchScoreResult(
                new BigDecimal("70"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("70"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, false, MatchScoreResult.CURRENT_VERSION);
        when(matchEvaluator.evaluate(any())).thenReturn(new MatchEvaluator.EvaluatedMatch(
                l2Record, new BigDecimal("60"), new BigDecimal("80"), scoreResult, profile));
        when(matchEvaluator.determineStatus(any())).thenReturn(2);
        when(matchingAlgorithmService.generateReport(any(), any(), any(), any(), any(), any()))
                .thenReturn("{}");
        when(matchingAlgorithmService.generateReport(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("{}");

        MatchingRecord result = engine.buildScoredRecord(contextWithoutRules(profile));

        assertThat(result.getAiMatchScore()).isEqualByComparingTo("70");
        assertThat(result.getRankScore()).isEqualByComparingTo("70");
        assertThat(result.getScoreBreakdownJson())
                .contains("ability")
                .contains("semantic")
                .doesNotContain("layerScores")
                .doesNotContain("layerWeights");
        verify(matchingAlgorithmService).generateReport(any(), any(), any(), any(), any(), any(), any());
        verify(matchingAlgorithmService, never()).generateReport(any(), any(), any(), any(), any(), any());
    }

    private MatchExecutionScoringEngine.MatchContext contextWithWhitelistAndFailedHardCondition(
            boolean bypass) {
        MatchingTrainingWeightProfileStore.WeightProfile profile =
                MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile();
        profile.setWhitelistBypassHardRules(bypass);
        MatchingAlgorithmService.HardConditionResult failed = new MatchingAlgorithmService.HardConditionResult();
        failed.setPassed(false);
        when(matchingAlgorithmService.checkHardConditions(any(), any(), any())).thenReturn(failed);
        return new MatchExecutionScoringEngine.MatchContext(
                "B1", employee(), post(), "v1", null, null,
                List.of(new HardCondition()), Map.of(),
                List.of(), List.of(), List.of(whitelist()), Map.of(1L, BigDecimal.ZERO), Map.of(),
                false, profile);
    }

    private MatchExecutionScoringEngine.MatchContext contextWithBothListEntries() {
        return new MatchExecutionScoringEngine.MatchContext(
                "B1", employee(), post(), "v1", null, null,
                List.of(), Map.of(),
                List.of(), List.of(), List.of(whitelist(), blacklist()), Map.of(1L, BigDecimal.ZERO), Map.of(),
                false, MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile());
    }

    private MatchExecutionScoringEngine.MatchContext contextWithoutRules(
            MatchingTrainingWeightProfileStore.WeightProfile profile) {
        return new MatchExecutionScoringEngine.MatchContext(
                "B1", employee(), post(), "v1", null, null,
                List.of(), Map.of(), List.of(), List.of(), List.of(),
                Map.of(1L, new BigDecimal("80")), Map.of(), false, profile);
    }

    private MatchingEmployeeProfile employee() {
        return new MatchingEmployeeProfile(1L, "E1", "Employee", null, null, null, List.of());
    }

    private MatchingPostProfile post() {
        return new MatchingPostProfile(2L, "P2", "Post", null, null, null, List.of());
    }

    private MatchingBlackWhiteList whitelist() {
        MatchingBlackWhiteList entry = new MatchingBlackWhiteList();
        entry.setEmpId(1L);
        entry.setPostId(2L);
        entry.setListType(1);
        return entry;
    }

    private MatchingBlackWhiteList blacklist() {
        MatchingBlackWhiteList entry = new MatchingBlackWhiteList();
        entry.setEmpId(1L);
        entry.setPostId(2L);
        entry.setListType(2);
        return entry;
    }
}
