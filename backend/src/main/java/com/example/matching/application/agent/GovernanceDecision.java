package com.example.matching.application.agent;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of a governance evaluation on a capability claim.
 * Immutable value object returned by {@code EvidenceGovernanceUseCase}.
 *
 * @param decision          governance decision: PASS, REVIEW, BLOCK, RETRY
 * @param riskLevel         risk assessment: LOW, MEDIUM, HIGH
 * @param supportScore      evidence support score 0-100
 * @param selfEvidence      whether the evidence is AI self-generated
 * @param reasons           human-readable reasons for the decision
 * @param missingEvidence   descriptions of missing evidence
 * @param suggestedAction   suggested human review action
 * @param fallbackUsed      whether a fallback rule was used instead of LLM
 * @param reasonCode        machine-readable reason code for audit
 */
public record GovernanceDecision(
        Decision decision,
        RiskLevel riskLevel,
        BigDecimal supportScore,
        boolean selfEvidence,
        List<String> reasons,
        List<String> missingEvidence,
        String suggestedAction,
        boolean fallbackUsed,
        String reasonCode
) {
    public enum Decision { PASS, REVIEW, BLOCK, RETRY }
    public enum RiskLevel { LOW, MEDIUM, HIGH }

    /**
     * Create a BLOCK decision with a reason.
     */
    public static GovernanceDecision block(String reason) {
        return new GovernanceDecision(
                Decision.BLOCK, RiskLevel.HIGH, BigDecimal.ZERO,
                false, List.of(reason), List.of(), null, false, "BLOCK_RULE"
        );
    }

    /**
     * Create a fallback REVIEW decision (used when both harness and LLM fail).
     */
    public static GovernanceDecision fallbackReview(String reason) {
        return new GovernanceDecision(
                Decision.REVIEW, RiskLevel.MEDIUM, new BigDecimal("30"),
                false, List.of(reason), List.of("需要人工审核"), "建议人工审核",
                true, "FALLBACK"
        );
    }

    /**
     * Whether this decision allows the claim to be admitted.
     */
    public boolean isAdmitted() {
        return decision == Decision.PASS || decision == Decision.REVIEW;
    }
}
