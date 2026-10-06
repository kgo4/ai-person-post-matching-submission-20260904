package com.example.matching.service.system;

import com.example.matching.common.enums.AbilitySourceCredibility;
import com.example.matching.common.enums.AbilitySourceType;
import com.example.matching.common.util.WeightScale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Unified source weight resolver — single source of truth for evidence weighting.
 * <p>
 * ALL services that compute weighted scores from evidence sources MUST use this service.
 * This replaces direct calls to {@link AbilitySourceCredibility#getWeightBySource}
 * and {@link SourceWeightConfigService#getWeight} scattered across the codebase.
 * <p>
 * Weight composition: configurableWeight * credibilityWeight
 * Default: 0.10 * 0.50 = 0.05 when DB config is absent and source is unknown.
 */
@Slf4j
@Service
public class SourceWeightResolver {

    /** Default weight when DB config is missing */
    private static final BigDecimal DEFAULT_CONFIG_WEIGHT = new BigDecimal("10");

    private final SourceWeightConfigService weightConfigService;

    public SourceWeightResolver(@Qualifier("sourceWeightConfigServiceImpl") SourceWeightConfigService weightConfigService) {
        this.weightConfigService = weightConfigService;
    }

    /**
     * Get the combined effective weight for a source type.
     * effective = configurableWeight(from DB) * credibilityWeight(from enum)
     *
     * @param sourceType raw or canonical source type
     * @return effective weight in [0, 1]
     */
    public BigDecimal resolveEffectiveWeight(String sourceType) {
        String canonical = AbilitySourceType.canonicalize(sourceType);
        BigDecimal configWeight = resolveConfigWeight(canonical);
        double credibility = AbilitySourceCredibility.getWeightBySource(canonical);
        return WeightScale.toFraction(configWeight).multiply(BigDecimal.valueOf(credibility));
    }

    /**
     * Get the configurable weight from DB, with a versioned default.
     *
     * @param sourceType raw or canonical source type
     * @return config weight, never null
     */
    public BigDecimal resolveConfigWeight(String sourceType) {
        String canonical = AbilitySourceType.canonicalize(sourceType);
        BigDecimal weight = weightConfigService.getWeight(canonical);
        if (weight == null) {
            log.warn("Source weight not configured for {}, using default {}", canonical, DEFAULT_CONFIG_WEIGHT);
            return DEFAULT_CONFIG_WEIGHT;
        }
        return weight;
    }

    /**
     * Get credibility weight from enum (authority level of the source).
     *
     * @param sourceType raw or canonical source type
     * @return credibility in [0, 1]
     */
    public double resolveCredibility(String sourceType) {
        String canonical = AbilitySourceType.canonicalize(sourceType);
        return AbilitySourceCredibility.getWeightBySource(canonical);
    }
}
