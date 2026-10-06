package com.example.matching.application.agent;

import java.util.List;

/**
 * Reference to a data source that supports a capability claim.
 * Immutable value object used in audit trails and governance decisions.
 *
 * @param ref        full standard reference identifier, e.g. "fact:EMP_ABILITY:123"
 * @param refType    reference type: fact, evidence, source, kg, rag, matching
 * @param refId      reference ID, e.g. "EMP_ABILITY:12"
 * @param title      human-readable title
 * @param snippet    original text excerpt
 * @param sourceType origin type: MANUAL, AI_ASSESSMENT, RESUME_PARSE, etc.
 */
public record SourceReference(
        String ref,
        String refType,
        String refId,
        String title,
        String snippet,
        String sourceType
) {
    /**
     * Create a simple source reference from type and ID.
     */
    public static SourceReference of(String sourceType, Long sourceRefId) {
        return new SourceReference(
                sourceType.toLowerCase() + ":" + sourceRefId,
                sourceType.toLowerCase(),
                String.valueOf(sourceRefId),
                null,
                null,
                sourceType
        );
    }
}
