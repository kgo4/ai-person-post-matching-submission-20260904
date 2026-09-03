package com.example.matching.agent.service;

import com.example.matching.common.enums.AbilitySourceType;

import java.math.BigDecimal;

/** Source-specific confidence values used only when model extraction is unavailable. */
public class AgentFallbackConfidencePolicy {

    public BigDecimal confidenceFor(String sourceType) {
        return switch (AbilitySourceType.canonicalize(sourceType)) {
            case AbilitySourceType.RESUME_PARSE -> new BigDecimal("70");
            case AbilitySourceType.AI_TEST -> new BigDecimal("60");
            case AbilitySourceType.AI_PROJECT, AbilitySourceType.LEARNING_PROJECT -> new BigDecimal("65");
            case AbilitySourceType.AI_INTERVIEW -> new BigDecimal("70");
            case AbilitySourceType.PERFORMANCE -> new BigDecimal("80");
            case AbilitySourceType.MANUAL -> new BigDecimal("75");
            case AbilitySourceType.PROFILE_FUSED -> new BigDecimal("70");
            default -> new BigDecimal("50");
        };
    }
}
