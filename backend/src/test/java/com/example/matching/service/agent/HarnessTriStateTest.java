package com.example.matching.service.agent;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.dto.governance.GovernanceAdmission;
import com.example.matching.dto.governance.GovernanceGrant;
import com.example.matching.service.agent.impl.AgentBusinessApplyServiceImpl;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.ability.PersonAbilityClaimAdmissionService;
import com.example.matching.service.governance.GovernedAdmissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests the three GovernedAdmission decision outcomes in the context of the person ability pipeline:
 * <ul>
 *   <li>PASS -> governed admission returns PASS, evidence ingested, profile refresh requested</li>
 *   <li>REVIEW -> candidate only, no ingest / no profile refresh</li>
 *   <li>BLOCK -> nothing written, no ingest / no profile refresh</li>
 * </ul>
 * <p>
 * All fact-table writes must go through GovernedAdmissionService; AgentBusinessApplyServiceImpl
 * no longer calls AiTrustHarnessService directly.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Agent Business Apply (Governed) Tri-State")
class HarnessTriStateTest {

    @Mock private GovernedAdmissionService governedAdmissionService;
    @Mock private PersonAbilityClaimAdmissionService admissionService;
    @Mock private AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private com.example.matching.service.common.VectorRecallCacheEpoch vectorRecallCacheEpoch;
    @Mock private com.example.matching.agent.service.AgentClaimConflictDetector conflictDetector;
    @InjectMocks private AgentBusinessApplyServiceImpl service;

    @Test
    @DisplayName("PASS -> admission per claim, evidence ingested, profile refresh requested")
    void pass_admissionPerClaim() {
        PersonAbilityClaim claim1 = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        PersonAbilityClaim claim2 = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);

        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.PASS, 500L));

        PersonAbilityExtractionResult extraction = new PersonAbilityExtractionResult();
        extraction.setClaims(List.of(claim1, claim2));

        var result = service.applyPersonAbilities(extraction);

        assertThat(result.getPassCount()).isEqualTo(2);
        verify(governedAdmissionService, times(2)).admitPersonAbility(any());
        verify(abilityEvidenceIngestionService, times(2))
                .ingestEmployeeAbility(500L, "EMP_ABILITY");
        verify(admissionService, times(2)).completeBatchForEmployee(1L);
    }

    @Test
    @DisplayName("Different sources -> independent governed admissions")
    void differentSources_independentClaimsProcessed() {
        PersonAbilityClaim resumeClaim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        PersonAbilityClaim interviewClaim = personClaim(1L, "AI_INTERVIEW", 22L, "Java", 7L, 5);

        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.PASS, 500L));

        PersonAbilityExtractionResult extraction = new PersonAbilityExtractionResult();
        extraction.setClaims(List.of(resumeClaim, interviewClaim));

        var result = service.applyPersonAbilities(extraction);

        assertThat(result.getPassCount()).isEqualTo(2);
        verify(governedAdmissionService).admitPersonAbility(resumeClaim);
        verify(governedAdmissionService).admitPersonAbility(interviewClaim);
    }

    @Test
    @DisplayName("BLOCK -> no ingest, no profile refresh, no event")
    void block_nothingWritten() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.BLOCK, null));

        var result = service.applyPersonAbilities(extractionResult(claim));

        assertThat(result.getBlockCount()).isEqualTo(1);
        assertThat(result.getPassCount()).isEqualTo(0);
        verify(abilityEvidenceIngestionService, never()).ingestEmployeeAbility(any(), any());
        verify(admissionService, never()).completeBatchForEmployee(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("BLOCK: no graph refresh event published")
    void block_noGraphRefresh() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.BLOCK, null));

        service.applyPersonAbilities(extractionResult(claim));

        verify(eventPublisher, never()).publishEvent(
                any(com.example.matching.event.KnowledgeGraphRebuildRequestedEvent.class));
    }

    @Test
    @DisplayName("REVIEW -> candidate only, no ingest, no profile refresh")
    void review_noIngestNoProfileRefresh() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "EmergingTech", null, 2);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.REVIEW, null));

        service.applyPersonAbilities(extractionResult(claim));

        verify(abilityEvidenceIngestionService, never()).ingestEmployeeAbility(any(), any());
        verify(admissionService, never()).completeBatchForEmployee(any());
    }

    @Test
    @DisplayName("RETRY -> counted as error, no writes")
    void retryCountedAsError() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.RETRY, null));

        var result = service.applyPersonAbilities(extractionResult(claim));

        assertThat(result.getErrorCount()).isEqualTo(1);
        verify(abilityEvidenceIngestionService, never()).ingestEmployeeAbility(any(), any());
    }

    @Test
    @DisplayName("All writes go through governed admission only")
    void allWritesGoThroughGovernedAdmission() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.PASS, 500L));

        service.applyPersonAbilities(extractionResult(claim));

        verify(governedAdmissionService).admitPersonAbility(claim);
        verifyNoMoreInteractions(governedAdmissionService);
    }

    // ---- Helpers ----

    private PersonAbilityClaim personClaim(Long empId, String sourceType, Long sourceRefId,
                                            String abilityName, Long tagId, Integer masteryLevel) {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setEmpId(empId);
        claim.setSourceType(sourceType);
        claim.setSourceRefId(sourceRefId);
        claim.setAbilityName(abilityName);
        claim.setAbilityTagId(tagId);
        claim.setMasteryLevel(masteryLevel);
        claim.setConfidenceScore(new BigDecimal("80"));
        claim.setEvidenceText("Evidence for " + abilityName);
        claim.setSourceRefs(List.of("fact:EMP_ABILITY:" + sourceRefId));
        return claim;
    }

    private PersonAbilityExtractionResult extractionResult(PersonAbilityClaim... claims) {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claims));
        return result;
    }

    private GovernanceAdmission admission(GovernanceGrant grant, Long businessTargetId) {
        GovernanceAdmission admission = new GovernanceAdmission();
        admission.setFinalDecision(grant.name());
        admission.setBusinessTargetId(businessTargetId);
        return admission;
    }
}
