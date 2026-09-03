package com.example.matching.application.agent;

import java.util.List;

/**
 * Use case: evaluate a capability claim through the governance pipeline.
 * <p>
 * Flow: deterministic harness first, LLM explanation second, fallback last.
 * The method is total: it returns a result for every non-null request.
 * Null requests must be rejected at the API boundary.
 */
public interface EvidenceGovernanceUseCase {

    /**
     * Evaluate a single capability claim.
     *
     * @param claim the claim to evaluate (must not be null)
     * @return governance decision (never null)
     */
    GovernanceDecision evaluate(AbilityClaimCandidate claim);

    /**
     * Evaluate multiple claims in batch.
     * Individual failures produce REVIEW decisions rather than aborting the batch.
     */
    List<GovernanceDecision> evaluateBatch(List<AbilityClaimCandidate> claims);
}
