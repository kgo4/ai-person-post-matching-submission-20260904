package com.example.matching.application.agent;

import java.util.List;

/**
 * Port for persisting and querying capability claims.
 * Implemented by a MyBatis adapter in the infrastructure layer.
 */
public interface CapabilityClaimRepository {

    /**
     * Persist a claim that passed governance.
     *
     * @param claim    the claim to persist
     * @param decision the governance decision
     * @return the persisted claim ID, or null on failure
     */
    Long admitClaim(AbilityClaimCandidate claim, GovernanceDecision decision);

    /**
     * Persist a claim in pending review state.
     *
     * @param claim    the claim to persist
     * @param decision the governance decision (REVIEW)
     * @return the persisted claim ID, or null on failure
     */
    Long admitPendingClaim(AbilityClaimCandidate claim, GovernanceDecision decision);

    /**
     * Load all admitted claims for an employee (status READY_FOR_FUSION or FUSED).
     */
    List<AbilityClaimCandidate> loadAdmittedClaims(Long employeeId);

    /**
     * Check if a claim with the same deduplication key already exists.
     */
    boolean existsByDeduplicationKey(Long employeeId, ClaimSource source,
                                     Long sourceRefId, String normalizedName);
}
