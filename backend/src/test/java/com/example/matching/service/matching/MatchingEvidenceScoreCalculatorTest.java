package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.service.system.SourceWeightConfigService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchingEvidenceScoreCalculatorTest {

    @Test
    void includesConfiguredSourceWeightRecordConfidenceAndFreshness() {
        SourceWeightConfigService sourceWeightConfigService = mock(SourceWeightConfigService.class);
        MatchingAlgorithmService matchingAlgorithmService = mock(MatchingAlgorithmService.class);
        MatchingEvidenceScoreCalculator calculator = new MatchingEvidenceScoreCalculator(
                sourceWeightConfigService, matchingAlgorithmService);
        EmpAbility ability = new EmpAbility();
        ability.setEvaluationSource("PERFORMANCE");
        ability.setSourceWeight(new BigDecimal("0.50"));
        ability.setEvaluationDate(LocalDate.of(2025, 1, 1));

        when(sourceWeightConfigService.getWeight("PERFORMANCE")).thenReturn(new BigDecimal("0.80"));
        when(matchingAlgorithmService.calculateTimeFactor(ability.getEvaluationDate())).thenReturn(0.90d);

        // formula: avg(credibility * timeFactor) * 100 = (0.80 * 0.90) * 100 = 72.00
        assertThat(calculator.computeEvidenceScore(List.of(ability))).isEqualByComparingTo("72.00");
    }

    @Test
    void snapshotBasedScore_matchesEntityBasedScore() {
        SourceWeightConfigService sourceWeightConfigService = mock(SourceWeightConfigService.class);
        MatchingAlgorithmService matchingAlgorithmService = mock(MatchingAlgorithmService.class);
        MatchingEvidenceScoreCalculator calculator = new MatchingEvidenceScoreCalculator(
                sourceWeightConfigService, matchingAlgorithmService);

        MatchingAbilitySnapshot snapshot = new MatchingAbilitySnapshot(
                100L, 10L, "Java", 4, new BigDecimal("0.85"), "PERFORMANCE",
                new BigDecimal("0.80"), LocalDate.of(2025, 1, 1));

        when(sourceWeightConfigService.getWeight("PERFORMANCE")).thenReturn(new BigDecimal("0.80"));
        when(matchingAlgorithmService.calculateTimeFactor(LocalDate.of(2025, 1, 1))).thenReturn(0.90d);

        assertThat(calculator.computeEvidenceScoreFromSnapshots(List.of(snapshot)))
                .isEqualByComparingTo("72.00");
        assertThat(calculator.computeEvidenceScoreFromSnapshots(List.of()))
                .isEqualByComparingTo("0.00");
        assertThat(calculator.computeEvidenceScoreFromSnapshots(null))
                .isEqualByComparingTo("0.00");
    }
}
