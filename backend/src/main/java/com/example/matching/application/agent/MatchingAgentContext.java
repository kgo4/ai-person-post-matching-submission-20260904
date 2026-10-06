package com.example.matching.application.agent;

import java.math.BigDecimal;
import java.util.List;

/**
 * Typed, immutable context for matching analysis.
 * Replaces the legacy {@code AgentContextPackage} with strongly-typed fields.
 * <p>
 * Created by context assembly use cases and consumed by LLM adapter and
 * fallback services.
 *
 * @param employeeId       employee ID
 * @param postId           post ID
 * @param matchingRecordId matching record ID (may be null for non-matching contexts)
 * @param employeeAbilities employee's current ability snapshots
 * @param postRequirements post's ability requirements
 * @param sources          source references for audit trail
 * @param matchScore       current match score (may be null if not yet calculated)
 */
public record MatchingAgentContext(
        Long employeeId,
        Long postId,
        Long matchingRecordId,
        List<EmployeeAbilitySnapshot> employeeAbilities,
        List<PostRequirementSnapshot> postRequirements,
        List<SourceReference> sources,
        BigDecimal matchScore
) {
    /**
     * Whether this context has sufficient data for analysis.
     */
    public boolean isComplete() {
        return employeeId != null
                && postId != null
                && employeeAbilities != null && !employeeAbilities.isEmpty()
                && postRequirements != null && !postRequirements.isEmpty();
    }

    /**
     * Whether this context includes employee ability data.
     */
    public boolean hasEmployeeAbilities() {
        return employeeAbilities != null && !employeeAbilities.isEmpty();
    }

    /**
     * Whether this context includes post requirements.
     */
    public boolean hasPostRequirements() {
        return postRequirements != null && !postRequirements.isEmpty();
    }
}
