package com.example.matching.application.agent;

import java.math.BigDecimal;

/**
 * Immutable snapshot of an employee's ability level for a single tag.
 * Used in matching context assembly and LLM prompts.
 *
 * @param abilityTagId  ability tag ID
 * @param abilityName   human-readable ability name
 * @param currentLevel  current mastery level 1-5
 * @param source        provenance: EMP_ABILITY, RESUME_PARSE, AI_ASSESSMENT, etc.
 * @param credibility   credibility score 0-100
 * @param evidenceCount number of supporting evidence items
 */
public record EmployeeAbilitySnapshot(
        Long abilityTagId,
        String abilityName,
        Integer currentLevel,
        String source,
        BigDecimal credibility,
        int evidenceCount
) {
}
