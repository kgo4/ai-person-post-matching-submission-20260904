package com.example.matching.application.agent;

import java.util.List;

/**
 * Bundle of evidence supporting a capability claim.
 * Immutable value object that carries verifiable proof.
 *
 * @param evidenceText    human-readable evidence description
 * @param sourceReferences traceable source references
 * @param rawModelOutput  raw LLM output for audit (restricted storage only)
 */
public record EvidenceBundle(
        String evidenceText,
        List<SourceReference> sourceReferences,
        String rawModelOutput
) {
    /**
     * Create an evidence bundle with text and references.
     */
    public static EvidenceBundle of(String evidenceText, List<SourceReference> refs) {
        return new EvidenceBundle(evidenceText, refs != null ? refs : List.of(), null);
    }

    /**
     * Create an empty evidence bundle (signals missing evidence).
     */
    public static EvidenceBundle empty() {
        return new EvidenceBundle(null, List.of(), null);
    }

    /**
     * Whether this bundle has any traceable evidence.
     */
    public boolean hasEvidence() {
        return evidenceText != null && !evidenceText.isBlank();
    }
}
