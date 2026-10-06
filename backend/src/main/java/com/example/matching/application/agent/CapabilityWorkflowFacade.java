package com.example.matching.application.agent;

import java.util.List;

/**
 * Application entry point for generic Agent capability workflows.
 * Controllers and asynchronous consumers that submit raw source text call this
 * facade; they do not call LangChain4j services, mappers, or Agent tools directly.
 * <p>
 * Domain-specific ingestion services may continue to use their typed workflows
 * until they are explicitly migrated to this contract.
 */
public interface CapabilityWorkflowFacade {

    /**
     * Extract capability claims from a source and run them through governance.
     * Claims that pass or need review are persisted.
     *
     * @param request the extraction and admission request
     * @return admission result with counts
     */
    AdmissionResult extractAndAdmit(AdmissionRequest request);

    /**
     * Build or refresh an employee's ability profile from admitted claims.
     */
    List<PersonProfileRepository.ProfileSnapshot> buildProfile(Long employeeId);

    /**
     * Build profiles including a specific interview session.
     */
    List<PersonProfileRepository.ProfileSnapshot> buildProfileWithInterview(
            Long employeeId, Long sessionId);

    /**
     * Process a human review decision on a profile.
     */
    ReviewProfileUseCase.ReviewResult reviewProfile(
            ReviewProfileUseCase.ReviewCommand command);

    /**
     * Request for claim extraction and admission.
     *
     * @param employeeId  the employee to extract claims for
     * @param sourceType  the source type
     * @param sourceRefId the source reference ID
     * @param sourceText  the raw source text (for LLM extraction)
     * @param sources     source references for audit
     */
    record AdmissionRequest(
            Long employeeId,
            ClaimSource sourceType,
            Long sourceRefId,
            String sourceText,
            List<SourceReference> sources
    ) {
    }

    /**
     * Result of claim extraction and admission.
     *
     * @param totalClaims total number of extracted claims
     * @param passCount   claims that passed governance
     * @param reviewCount claims pending review
     * @param blockCount  claims blocked by governance
     * @param errorCount  claims that failed processing
     * @param retryCount  claims deferred into the governed retry queue
     */
    record AdmissionResult(
            int totalClaims,
            int passCount,
            int reviewCount,
            int blockCount,
            int errorCount,
            int retryCount
    ) {
    }
}
