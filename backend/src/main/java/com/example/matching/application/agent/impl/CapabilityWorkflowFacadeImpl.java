package com.example.matching.application.agent.impl;

import com.example.matching.application.agent.*;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.service.governance.GovernedAdmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Application entry point for generic Agent capability workflows.
 * Controllers and asynchronous consumers that submit raw source text call this
 * facade; they do not call LangChain4j services, mappers, or Agent tools directly.
 * <p>
 * Domain-specific ingestion services may continue to use their typed workflows
 * until they are explicitly migrated to this contract.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CapabilityWorkflowFacadeImpl implements CapabilityWorkflowFacade {

    private final EvidenceGovernanceUseCase governanceUseCase;
    private final ProfileBuildUseCase profileBuildUseCase;
    private final ReviewProfileUseCase reviewProfileUseCase;
    private final CapabilityClaimRepository claimRepository;
    private final ClaimExtractor claimExtractor;
    private final GovernedAdmissionService governedAdmissionService;

    @Override
    @Transactional
    public AdmissionResult extractAndAdmit(AdmissionRequest request) {
        validateAdmissionRequest(request);
        log.info("Extract and admit: employeeId={}, source={}, refId={}",
                request.employeeId(), request.sourceType(), request.sourceRefId());

        // 1. Extract claims from source
        List<AbilityClaimCandidate> claims = extractClaims(request);
        log.info("Extracted {} claims for employeeId={}", claims.size(), request.employeeId());

        // 2. Run governance on each claim
        int passCount = 0, reviewCount = 0, blockCount = 0, errorCount = 0, retryCount = 0;

        for (AbilityClaimCandidate claim : claims) {
            try {
                GovernanceDecision decision = governanceUseCase.evaluate(claim);

                switch (decision.decision()) {
                    case PASS -> {
                        Long id = claimRepository.admitClaim(claim, decision);
                        if (id != null) passCount++;
                        else errorCount++;
                    }
                    case REVIEW -> {
                        Long id = claimRepository.admitPendingClaim(claim, decision);
                        if (id != null) reviewCount++;
                        else errorCount++;
                    }
                    case BLOCK -> blockCount++;
                    case RETRY -> {
                        governedAdmissionService.deferPersonAbilityRetry(
                                toGovernedClaim(claim), String.join("; ", decision.reasons()));
                        log.warn("Claim deferred to governed retry queue: abilityName={}, reason={}",
                                claim.abilityName(), decision.reasons());
                        retryCount++;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process claim: abilityName={}", claim.abilityName(), e);
                errorCount++;
            }
        }

        AdmissionResult result = new AdmissionResult(
                claims.size(), passCount, reviewCount, blockCount, errorCount, retryCount);
        log.info("Admission complete: {}", result);
        return result;
    }

    @Override
    public List<PersonProfileRepository.ProfileSnapshot> buildProfile(Long employeeId) {
        return profileBuildUseCase.buildProfile(employeeId);
    }

    @Override
    public List<PersonProfileRepository.ProfileSnapshot> buildProfileWithInterview(
            Long employeeId, Long sessionId) {
        return profileBuildUseCase.buildProfileWithInterview(employeeId, sessionId);
    }

    @Override
    @Transactional
    public ReviewProfileUseCase.ReviewResult reviewProfile(
            ReviewProfileUseCase.ReviewCommand command) {
        return reviewProfileUseCase.review(command);
    }

    private List<AbilityClaimCandidate> extractClaims(AdmissionRequest request) {
        List<AbilityClaimCandidate> claims = claimExtractor.extract(request);
        if (claims == null) {
            throw new ClaimExtractionException("Capability claim extractor returned null claims");
        }
        return claims;
    }

    private PersonAbilityClaim toGovernedClaim(AbilityClaimCandidate claim) {
        PersonAbilityClaim governedClaim = new PersonAbilityClaim();
        governedClaim.setEmpId(claim.employeeId());
        governedClaim.setSourceType(claim.source().name());
        governedClaim.setSourceRefId(claim.sourceRefId());
        governedClaim.setAbilityName(claim.abilityName());
        governedClaim.setNormalizedAbilityName(claim.normalizedAbilityName());
        governedClaim.setAbilityTagId(claim.abilityTagId());
        governedClaim.setSimilarTagId(claim.similarTagId());
        governedClaim.setMasteryLevel(claim.claimedLevel());
        governedClaim.setConfidenceScore(claim.confidence());
        governedClaim.setEvidenceText(claim.evidence() == null ? null : claim.evidence().evidenceText());
        governedClaim.setRawModelOutput(claim.evidence() == null ? null : claim.evidence().rawModelOutput());
        List<String> sourceRefs = claim.evidence() == null || claim.evidence().sourceReferences() == null
                ? List.of()
                : claim.evidence().sourceReferences().stream()
                .map(SourceReference::ref)
                .filter(Objects::nonNull)
                .toList();
        if (sourceRefs.isEmpty() && claim.sourceRefId() != null) {
            sourceRefs = List.of("source:" + claim.source().name() + ":" + claim.sourceRefId());
        }
        governedClaim.setSourceRefs(sourceRefs);
        return governedClaim;
    }

    private void validateAdmissionRequest(AdmissionRequest request) {
        Objects.requireNonNull(request, "admission request must not be null");
        if (request.employeeId() == null) {
            throw new IllegalArgumentException("employeeId must not be null");
        }
        if (request.sourceType() == null) {
            throw new IllegalArgumentException("sourceType must not be null");
        }
        if (request.sourceText() == null || request.sourceText().isBlank()) {
            throw new IllegalArgumentException("sourceText must not be blank");
        }
    }
}
