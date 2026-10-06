package com.example.matching.application.agent.impl;

import com.example.matching.agent.dto.EvidenceGovernanceAgentRequest;
import com.example.matching.agent.dto.EvidenceGovernanceAgentResult;
import com.example.matching.agent.service.EvidenceGovernanceAgentService;
import com.example.matching.application.agent.AbilityClaimCandidate;
import com.example.matching.application.agent.EvidenceGovernanceUseCase;
import com.example.matching.application.agent.GovernanceDecision;
import com.example.matching.application.agent.SourceReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Delegates each valid claim to the agent-owned Harness -> LLM governance
 * chain. Keeping the decision chain in one place prevents local policy drift.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceGovernanceUseCaseImpl implements EvidenceGovernanceUseCase {

    private final EvidenceGovernanceAgentService evidenceGovernanceAgentService;

    @Override
    public GovernanceDecision evaluate(AbilityClaimCandidate claim) {
        if (claim == null) {
            throw new IllegalArgumentException("Claim must not be null");
        }
        if (!claim.isValid()) {
            return GovernanceDecision.block("Claim is missing required fields: " + describeInvalidFields(claim));
        }

        GovernanceDecision agentDecision = runAgentCheck(claim);
        return agentDecision != null
                ? agentDecision
                : GovernanceDecision.fallbackReview("Governance agent is unavailable");
    }

    @Override
    public List<GovernanceDecision> evaluateBatch(List<AbilityClaimCandidate> claims) {
        if (claims == null || claims.isEmpty()) {
            return List.of();
        }
        return claims.stream()
                .map(claim -> {
                    try {
                        return evaluate(claim);
                    } catch (Exception exception) {
                        log.error("Governance evaluation failed for claim: {}", claim == null ? null : claim.abilityName(), exception);
                        return GovernanceDecision.fallbackReview("Governance evaluation failed");
                    }
                })
                .toList();
    }

    private GovernanceDecision runAgentCheck(AbilityClaimCandidate claim) {
        try {
            EvidenceGovernanceAgentRequest request = new EvidenceGovernanceAgentRequest();
            request.setScenario("CAPABILITY_ADMISSION");
            request.setClaimType("ABILITY_CLAIM");
            request.setClaimText(claim.abilityName() + " (level " + claim.claimedLevel() + ")");
            request.setEvidenceText(claim.evidence() == null ? null : claim.evidence().evidenceText());
            request.setSourceType(claim.source().name());
            request.setSourceRefId(claim.sourceRefId());
            request.setMatchedTagId(claim.abilityTagId());
            request.setSimilarTagId(claim.similarTagId());
            request.setSourceRefs(claim.evidence() == null ? List.of() : claim.evidence().sourceReferences().stream()
                    .map(SourceReference::ref)
                    .filter(Objects::nonNull)
                    .toList());

            EvidenceGovernanceAgentResult result = evidenceGovernanceAgentService.review(request);
            if (result == null || result.getDecision() == null || result.getRiskLevel() == null) {
                return null;
            }
            return new GovernanceDecision(
                    GovernanceDecision.Decision.valueOf(result.getDecision()),
                    GovernanceDecision.RiskLevel.valueOf(result.getRiskLevel()),
                    result.getSupportScore() == null ? claim.confidence() : result.getSupportScore(),
                    Boolean.TRUE.equals(result.getSelfEvidence()),
                    result.getReasons() == null ? List.of() : result.getReasons(),
                    result.getMissingEvidence() == null ? List.of() : result.getMissingEvidence(),
                    result.getSuggestedHumanReviewAction(),
                    Boolean.TRUE.equals(result.getFallbackUsed()),
                    "AGENT_GOVERNANCE"
            );
        } catch (Exception exception) {
            log.warn("Governance agent check failed", exception);
            return null;
        }
    }

    private String describeInvalidFields(AbilityClaimCandidate claim) {
        List<String> issues = new ArrayList<>();
        if (claim.employeeId() == null) issues.add("employeeId");
        if (claim.abilityName() == null || claim.abilityName().isBlank()) issues.add("abilityName");
        if (claim.claimedLevel() == null || claim.claimedLevel() < 1 || claim.claimedLevel() > 5) issues.add("claimedLevel");
        if (claim.source() == null) issues.add("source");
        return String.join(", ", issues);
    }
}
