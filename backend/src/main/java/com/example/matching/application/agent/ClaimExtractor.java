package com.example.matching.application.agent;

import java.util.List;

/**
 * Port for extracting typed capability claims from a source document.
 * Implementations belong to infrastructure and may use an LLM or another
 * source-specific extraction engine.
 */
public interface ClaimExtractor {

    /**
     * Extract claims from the supplied source.
     *
     * @throws ClaimExtractionException when the extraction engine is not
     *                                  available or returns an unusable result
     */
    List<AbilityClaimCandidate> extract(CapabilityWorkflowFacade.AdmissionRequest request);
}
