package com.example.matching.dto.governance;

import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionMergerTest {

    @Test
    void blocksWhenHarnessIsUnavailable() {
        DecisionMerger.MergedResult result = DecisionMerger.merge(null, new BigDecimal("0.9"), "PASS");

        assertThat(result.getFinalDecision()).isEqualTo("BLOCK");
        assertThat(result.getFinalSupportScore()).isZero();
        assertThat(result.getDecisionRule()).isEqualTo("HARNESS_UNAVAILABLE");
    }

    @Test
    void harnessBlockTakesPriorityAndUsesLowerSupportScore() {
        DecisionMerger.MergedResult result = DecisionMerger.merge(decision("BLOCK", "0.8"),
                new BigDecimal("0.6"), "PASS");

        assertThat(result.getFinalDecision()).isEqualTo("BLOCK");
        assertThat(result.getDecisionRule()).isEqualTo("HARNESS_BLOCKED");
        assertThat(result.getFinalSupportScore()).isEqualByComparingTo("0.6");
    }

    @Test
    void legacyBlockStillBlocksPassingHarness() {
        DecisionMerger.MergedResult result = DecisionMerger.merge(decision("PASS", "0.8"), null, "BLOCK");

        assertThat(result.getFinalDecision()).isEqualTo("BLOCK");
        assertThat(result.getDecisionRule()).isEqualTo("LEGACY_BLOCKED");
        assertThat(result.getFinalSupportScore()).isEqualByComparingTo("0.8");
    }

    @Test
    void preservesHarnessRetryBeforeLegacyReview() {
        DecisionMerger.MergedResult result = DecisionMerger.merge(decision("RETRY", "0.7"),
                new BigDecimal("0.9"), "REVIEW");

        assertThat(result.getFinalDecision()).isEqualTo("RETRY");
        assertThat(result.getDecisionRule()).isEqualTo("HARNESS_RETRY");
    }

    @Test
    void returnsReviewForHarnessOrLegacyReview() {
        DecisionMerger.MergedResult harnessReview = DecisionMerger.merge(decision("REVIEW", "0.7"), null, "PASS");
        DecisionMerger.MergedResult legacyReview = DecisionMerger.merge(decision("PASS", "0.7"), null, "REVIEW");

        assertThat(harnessReview.getFinalDecision()).isEqualTo("REVIEW");
        assertThat(harnessReview.getDecisionRule()).isEqualTo("HARNESS_REVIEW");
        assertThat(legacyReview.getFinalDecision()).isEqualTo("REVIEW");
        assertThat(legacyReview.getDecisionRule()).isEqualTo("LEGACY_REVIEW");
    }

    @Test
    void passesWhenBothDecisionSourcesPass() {
        DecisionMerger.MergedResult result = DecisionMerger.merge(decision("PASS", "0.9"),
                new BigDecimal("0.95"), "PASS");

        assertThat(result.getFinalDecision()).isEqualTo("PASS");
        assertThat(result.getDecisionRule()).isEqualTo("HARNESS_PASS");
        assertThat(result.getFinalSupportScore()).isEqualByComparingTo("0.9");
    }

    private AiHarnessDecisionDTO decision(String decision, String supportScore) {
        AiHarnessDecisionDTO dto = new AiHarnessDecisionDTO();
        dto.setDecision(decision);
        dto.setSupportScore(new BigDecimal(supportScore));
        return dto;
    }
}
