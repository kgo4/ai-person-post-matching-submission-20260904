package com.example.matching.application.agent;

import java.math.BigDecimal;
import java.util.List;

/**
 * Typed, immutable candidate for a capability claim.
 * Created by extraction use cases and consumed by governance and admission.
 * <p>
 * This is the application-layer DTO. The legacy {@code agent.dto.person.PersonAbilityClaim}
 * remains as an LLM transport DTO in the infrastructure layer.
 *
 * @param employeeId         employee who owns this claim
 * @param abilityTagId       resolved ability tag ID (may be null if unresolved)
 * @param abilityName        human-readable ability name
 * @param normalizedAbilityName normalized name for deduplication
 * @param claimedLevel       mastery level 1-5
 * @param source             claim source enum
 * @param sourceRefId        source-specific reference ID
 * @param evidence           supporting evidence bundle
 * @param confidence         confidence score 0-100
 * @param freshness          freshness score 0-100
 * @param authority          authority score 0-100
 * @param similarTagId       ID of a similar tag if the ability name didn't match exactly
 */
public record AbilityClaimCandidate(
        Long employeeId,
        Long abilityTagId,
        String abilityName,
        String normalizedAbilityName,
        Integer claimedLevel,
        ClaimSource source,
        Long sourceRefId,
        EvidenceBundle evidence,
        BigDecimal confidence,
        BigDecimal freshness,
        BigDecimal authority,
        Long similarTagId
) {
    /**
     * Whether this claim has the minimum required fields for governance evaluation.
     */
    public boolean isValid() {
        return employeeId != null
                && abilityName != null && !abilityName.isBlank()
                && claimedLevel != null && claimedLevel >= 1 && claimedLevel <= 5
                && source != null;
    }

    /**
     * Whether this claim has a resolved ability tag.
     */
    public boolean hasResolvedTag() {
        return abilityTagId != null;
    }

    /**
     * Source weight for this claim's source type.
     */
    public BigDecimal sourceWeight() {
        return BigDecimal.valueOf(source.getDefaultWeight());
    }
}
