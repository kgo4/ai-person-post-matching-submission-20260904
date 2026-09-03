package com.example.matching.application.agent;

import java.math.BigDecimal;

/**
 * Immutable snapshot of a post's requirement for a single ability tag.
 * Used in matching context assembly and LLM prompts.
 *
 * @param abilityTagId  ability tag ID
 * @param abilityName   human-readable ability name
 * @param requiredLevel minimum required mastery level 1-5
 * @param weight        importance weight (0-100)
 * @param required      whether this ability is mandatory
 * @param core          whether this is a core ability for the post
 */
public record PostRequirementSnapshot(
        Long abilityTagId,
        String abilityName,
        Integer requiredLevel,
        BigDecimal weight,
        boolean required,
        boolean core
) {
}
