package com.example.matching.service.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Composes formal matching score into:
 * 1. rankScore: candidate-comparable ranking score
 * 2. finalScore: the same authoritative formal score
 */
public final class MatchingScoreCalculator {

    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = new BigDecimal("100.00");
    private static final BigDecimal WEIGHT_SUM_EPSILON = new BigDecimal("0.000001");

    private MatchingScoreCalculator() {
    }

    public static ScoreBreakdown composeFormalScore(BigDecimal abilityScore,
                                                    BigDecimal semanticScore,
                                                    BigDecimal evidenceScore,
                                                    BigDecimal aiScore,
                                                    MatchingTrainingWeightProfileStore.WeightProfile profile) {
        if (profile == null) {
            throw new IllegalStateException("Matching weight profile must not be null. "
                    + "Ensure MatchingTrainingWeightProfileStore is properly initialized.");
        }

        BigDecimal abilityWeight = BigDecimal.valueOf(requireValidWeight(profile.getAbilityWeight()));
        BigDecimal semanticWeight = BigDecimal.valueOf(requireValidWeight(profile.getSemanticWeight()));
        BigDecimal evidenceWeight = BigDecimal.valueOf(requireValidWeight(profile.getEvidenceWeight()));
        BigDecimal aiWeight = BigDecimal.valueOf(requireValidWeight(profile.getAiWeight()));
        BigDecimal totalWeight = abilityWeight.add(semanticWeight).add(evidenceWeight).add(aiWeight);
        if (totalWeight.subtract(BigDecimal.ONE).abs().compareTo(WEIGHT_SUM_EPSILON) > 0) {
            throw new IllegalStateException("Unified matching score weights must sum to 1.0, current=" + totalWeight);
        }

        BigDecimal effectiveSemanticScore = semanticScore != null ? semanticScore : zeroIfNull(abilityScore);
        BigDecimal factBasedAiScore = zeroIfNull(abilityScore).multiply(abilityWeight)
                .add(effectiveSemanticScore.multiply(semanticWeight))
                .add(zeroIfNull(evidenceScore).multiply(evidenceWeight))
                .divide(abilityWeight.add(semanticWeight).add(evidenceWeight), 10, RoundingMode.HALF_UP);
        BigDecimal effectiveAiScore = aiScore != null ? aiScore : factBasedAiScore;
        BigDecimal rankScore = zeroIfNull(abilityScore).multiply(abilityWeight)
                .add(effectiveSemanticScore.multiply(semanticWeight))
                .add(zeroIfNull(evidenceScore).multiply(evidenceWeight))
                .add(effectiveAiScore.multiply(aiWeight));

        BigDecimal qualityAdjustment = BigDecimal.ZERO;
        BigDecimal feedbackAdjustment = BigDecimal.ZERO;
        BigDecimal calibrationAdjustment = BigDecimal.ZERO;
        BigDecimal finalScore = scale(clampScore(rankScore));

        return new ScoreBreakdown(
                scale(rankScore),
                qualityAdjustment,
                feedbackAdjustment,
                calibrationAdjustment,
                finalScore
        );
    }


    private static double requireValidWeight(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0d) {
            throw new IllegalStateException("Invalid matching weight value: " + value
                    + ". Check the active-weight-profile configuration.");
        }
        return value;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
    private static BigDecimal clampScore(BigDecimal score) {
        if (score.compareTo(MAX_SCORE) > 0) {
            return MAX_SCORE;
        }
        if (score.compareTo(MIN_SCORE) < 0) {
            return MIN_SCORE;
        }
        return score;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static final class ScoreBreakdown {
        private final BigDecimal rankScore;
        private final BigDecimal qualityAdjustment;
        private final BigDecimal feedbackAdjustment;
        private final BigDecimal calibrationAdjustment;
        private final BigDecimal finalScore;

        private ScoreBreakdown(BigDecimal rankScore,
                               BigDecimal qualityAdjustment,
                               BigDecimal feedbackAdjustment,
                               BigDecimal calibrationAdjustment,
                               BigDecimal finalScore) {
            this.rankScore = rankScore;
            this.qualityAdjustment = qualityAdjustment;
            this.feedbackAdjustment = feedbackAdjustment;
            this.calibrationAdjustment = calibrationAdjustment;
            this.finalScore = finalScore;
        }

        public BigDecimal getRankScore() {
            return rankScore;
        }

        public BigDecimal getQualityAdjustment() {
            return qualityAdjustment;
        }

        public BigDecimal getFeedbackAdjustment() {
            return feedbackAdjustment;
        }

        public BigDecimal getCalibrationAdjustment() {
            return calibrationAdjustment;
        }

        public BigDecimal getFinalScore() {
            return finalScore;
        }
    }
}
