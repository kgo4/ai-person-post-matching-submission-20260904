package com.example.matching.service.matching.evaluation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DimensionBreakdownTest {

    @Test
    void calculatesRoundedWeightedScoreForAvailableDimension() {
        DimensionBreakdown breakdown = DimensionBreakdown.available(
                "ability", "能力匹配", new BigDecimal("87.65"), new BigDecimal("0.35"));

        assertThat(breakdown.getStatus()).isEqualTo(DimensionBreakdown.DimensionStatus.AVAILABLE);
        assertThat(breakdown.getWeightedScore()).isEqualByComparingTo("30.68");
    }

    @Test
    void createsZeroScoreForMissingAndUnavailableDimensions() {
        DimensionBreakdown missing = DimensionBreakdown.missing("semantic", "语义", new BigDecimal("0.2"));
        DimensionBreakdown unavailable = DimensionBreakdown.unavailable("evidence", "证据", new BigDecimal("0.1"));

        assertThat(missing.getStatus()).isEqualTo(DimensionBreakdown.DimensionStatus.MISSING);
        assertThat(missing.getRawScore()).isZero();
        assertThat(missing.getWeightedScore()).isZero();
        assertThat(unavailable.getStatus()).isEqualTo(DimensionBreakdown.DimensionStatus.UNAVAILABLE);
        assertThat(unavailable.getRawScore()).isZero();
        assertThat(unavailable.getWeightedScore()).isZero();
    }
}
