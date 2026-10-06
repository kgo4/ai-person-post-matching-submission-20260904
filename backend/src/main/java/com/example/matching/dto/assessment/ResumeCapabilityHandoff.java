package com.example.matching.dto.assessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Resume agent output contract; it proposes abilities but never writes formal tags or profiles. */
public record ResumeCapabilityHandoff(String contractVersion, String messageId, String traceId,
                                      Long workflowId, Long resumeParseId, String scopeHash,
                                      String taxonomyVersion, List<Ability> abilities,
                                      List<String> warnings, Instant createdAt) {
    public record Ability(Long assessmentAbilityId, String abilityName, Long canonicalTagId,
                          boolean tagExists, String parentTagName, Integer claimedLevel,
                          BigDecimal confidenceScore, String evidenceText, List<Long> sourceClaimIds,
                          List<String> resumeEvidenceRefs, String resolutionStatus) {}
}
