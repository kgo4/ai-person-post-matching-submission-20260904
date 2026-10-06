package com.example.matching.service.matching;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingScoreCalculatorTest {

    @Test
    void usesOneStableProfileWhetherAiScoreIsPresentOrNot() {
        MatchingTrainingWeightProfileStore.WeightProfile profile =
                MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile();

        MatchingScoreCalculator.ScoreBreakdown withoutAi = MatchingScoreCalculator.composeFormalScore(
                new BigDecimal("80"), new BigDecimal("70"), new BigDecimal("60"),
                null, profile);
        MatchingScoreCalculator.ScoreBreakdown withAi = MatchingScoreCalculator.composeFormalScore(
                new BigDecimal("80"), new BigDecimal("70"), new BigDecimal("60"),
                new BigDecimal("50"), profile);

        assertThat(withoutAi.getRankScore()).isEqualByComparingTo("76.11");
        assertThat(withAi.getRankScore()).isEqualByComparingTo("73.50");
        assertThat(withoutAi.getCalibrationAdjustment()).isZero();
        assertThat(withAi.getCalibrationAdjustment()).isZero();
    }

    @Test
    void unavailableSemanticUsesAbilityScoreWithoutChangingWeights() {
        MatchingScoreCalculator.ScoreBreakdown score = MatchingScoreCalculator.composeFormalScore(
                new BigDecimal("80"), null, new BigDecimal("60"),
                new BigDecimal("50"),
                MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile());

        assertThat(score.getRankScore()).isEqualByComparingTo("75.00");
    }

    @Test
    void scoreContractHasNoRagQualityOrFeedbackInput() {
        MatchingTrainingWeightProfileStore.WeightProfile profile =
                MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile();
        MatchingScoreCalculator.ScoreBreakdown baseline = MatchingScoreCalculator.composeFormalScore(
                new BigDecimal("80"), new BigDecimal("70"), new BigDecimal("60"),
                new BigDecimal("50"), profile);

        assertThat(baseline.getFinalScore()).isEqualByComparingTo("73.50");
    }
}
