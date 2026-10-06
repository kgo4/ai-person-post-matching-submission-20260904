package com.example.matching.service.matching;

import java.math.BigDecimal;

/**
 * Immutable AI scoring result payload. Write through state machine only.
 */
public record MatchingAiScoringResult(
        BigDecimal llmScore, BigDecimal finalScore, BigDecimal evidenceScore,
        BigDecimal rankScore, BigDecimal qualityAdjustment,
        BigDecimal feedbackAdjustment, BigDecimal calibrationAdjustment,
        String aiAnalysisReport, String quantitativeReport, Integer matchStatus,
        String scoreBreakdownJson) {

    public MatchingAiScoringResult(
            BigDecimal llmScore, BigDecimal finalScore, BigDecimal evidenceScore,
            BigDecimal rankScore, BigDecimal qualityAdjustment,
            BigDecimal feedbackAdjustment, BigDecimal calibrationAdjustment,
            String aiAnalysisReport, String quantitativeReport, Integer matchStatus) {
        this(llmScore, finalScore, evidenceScore, rankScore, qualityAdjustment,
                feedbackAdjustment, calibrationAdjustment, aiAnalysisReport,
                quantitativeReport, matchStatus, null);
    }
}
