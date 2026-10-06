package com.example.matching.service.evolution.support;

/**
 * A normalized ability tag resolved from evolution evidence.
 */
public record ResolvedEvolutionAbility(Long tagId, String abilityName, double confidence) {
}
