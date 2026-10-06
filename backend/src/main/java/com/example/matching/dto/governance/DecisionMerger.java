package com.example.matching.dto.governance;


import com.example.matching.dto.harness.AiHarnessDecisionDTO;

import java.math.BigDecimal;

public final class DecisionMerger {

    private DecisionMerger() {
    }

    public static MergedResult merge(
            AiHarnessDecisionDTO harnessDecision,
            BigDecimal legacySupportScore,
            String legacyDecision) {
        MergedResult result = new MergedResult();
        result.legacySupportScore = legacySupportScore;
        result.legacyDecision = legacyDecision;
        result.harnessSupportScore = harnessDecision != null ? harnessDecision.getSupportScore() : null;
        result.harnessDecision = harnessDecision != null ? harnessDecision.getDecision() : null;
        result.finalSupportScore = minOf(legacySupportScore, result.harnessSupportScore);

        if (harnessDecision == null) {
            result.finalDecision = GovernanceGrant.BLOCK.name();
            result.finalSupportScore = BigDecimal.ZERO;
            result.decisionRule = "HARNESS_UNAVAILABLE";
            return result;
        }

        if (harnessDecision.isBlock() || "BLOCK".equals(legacyDecision)) {
            result.finalDecision = GovernanceGrant.BLOCK.name();
            result.decisionRule = harnessDecision.isBlock() ? "HARNESS_BLOCKED" : "LEGACY_BLOCKED";
            return result;
        }

        if (harnessDecision.isRetry()) {
            result.finalDecision = GovernanceGrant.RETRY.name();
            result.decisionRule = "HARNESS_RETRY";
            return result;
        }

        if (harnessDecision.isReview() || "REVIEW".equals(legacyDecision)) {
            result.finalDecision = GovernanceGrant.REVIEW.name();
            result.decisionRule = harnessDecision.isReview() ? "HARNESS_REVIEW" : "LEGACY_REVIEW";
            return result;
        }

        result.finalDecision = GovernanceGrant.PASS.name();
        result.decisionRule = "HARNESS_PASS";
        return result;
    }

    private static BigDecimal minOf(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.min(b);
    }

    public static class MergedResult {
        public BigDecimal legacySupportScore;
        public String legacyDecision;
        public BigDecimal harnessSupportScore;
        public String harnessDecision;
        public BigDecimal finalSupportScore;
        public String finalDecision;
        public String decisionRule;

        public BigDecimal getLegacySupportScore() { return legacySupportScore; }
        public String getLegacyDecision() { return legacyDecision; }
        public BigDecimal getHarnessSupportScore() { return harnessSupportScore; }
        public String getHarnessDecision() { return harnessDecision; }
        public BigDecimal getFinalSupportScore() { return finalSupportScore; }
        public String getFinalDecision() { return finalDecision; }
        public String getDecisionRule() { return decisionRule; }
    }
}
