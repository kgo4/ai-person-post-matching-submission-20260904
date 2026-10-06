package com.example.matching.service.agent;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.dto.governance.GovernanceAdmission;
import com.example.matching.dto.governance.GovernanceGrant;
import com.example.matching.service.agent.impl.AgentBusinessApplyServiceImpl;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.ability.PersonAbilityClaimAdmissionService;
import com.example.matching.service.governance.GovernedAdmissionService;
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
import static org.mockito.Mockito.when;

/**
 * Tests the full person ability pipeline: Extract -> GovernedAdmission (PASS/REVIEW/BLOCK/RETRY) ->
 * Evidence ingestion -> Profile refresh -> Graph refresh.
 * <p>
 * All fact-table writes happen inside GovernedAdmissionService; AgentBusinessApplyServiceImpl
 * only applies side effects for granted admissions.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Person Ability Pipeline (Governed)")
class PersonAbilityPipelineTest {

    @Mock private GovernedAdmissionService governedAdmissionService;
    @Mock private PersonAbilityClaimAdmissionService admissionService;
    @Mock private AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private com.example.matching.service.common.VectorRecallCacheEpoch vectorRecallCacheEpoch;
    @Mock private com.example.matching.agent.service.AgentClaimConflictDetector conflictDetector;
    @InjectMocks private AgentBusinessApplyServiceImpl service;

    // ---- PASS path ----

    @Test
    @DisplayName("PASS: evidence ingested, profile refreshed, passCount incremented")
    void passPath_claimAdmitted_passCountIncremented() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.PASS, 500L));

        var result = service.applyPersonAbilities(extractionResult(claim));

        assertThat(result.getPassCount()).isEqualTo(1);
        assertThat(result.getReviewCount()).isEqualTo(0);
        assertThat(result.getBlockCount()).isEqualTo(0);
        assertThat(result.getErrorCount()).isEqualTo(0);
        verify(abilityEvidenceIngestionService).ingestEmployeeAbility(500L, "EMP_ABILITY");
        verify(admissionService).completeBatchForEmployee(1L);
    }

    @Test
    @DisplayName("PASS: graph refresh triggered when passCount > 0")
    void passPath_graphRefreshTriggered() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.PASS, 500L));

        service.applyPersonAbilities(extractionResult(claim));

        verify(eventPublisher).publishEvent(any(com.example.matching.event.KnowledgeGraphRebuildRequestedEvent.class));
    }

    // ---- REVIEW path ----

    @Test
    @DisplayName("REVIEW: candidate only, no ingest, no profile refresh, reviewCount incremented")
    void reviewPath_claimStoredAsPending_reviewCountIncremented() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "NewAbility", null, 3);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.REVIEW, null));

        var result = service.applyPersonAbilities(extractionResult(claim));

        assertThat(result.getReviewCount()).isEqualTo(1);
        assertThat(result.getPassCount()).isEqualTo(0);
        assertThat(result.getBlockCount()).isEqualTo(0);
        verify(abilityEvidenceIngestionService, never()).ingestEmployeeAbility(any(), any());
        verify(admissionService, never()).completeBatchForEmployee(any());
    }

    @Test
    @DisplayName("REVIEW: no graph refresh triggered (only PASS triggers refresh)")
    void reviewPath_noGraphRefresh() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "NewAbility", null, 3);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.REVIEW, null));

        service.applyPersonAbilities(extractionResult(claim));

        verify(eventPublisher, never()).publishEvent(any(com.example.matching.event.KnowledgeGraphRebuildRequestedEvent.class));
    }

    // ---- BLOCK path ----

    @Test
    @DisplayName("BLOCK: no writes, blockCount incremented, no graph refresh")
    void blockPath_noAdmissionCall_blockCountIncremented() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "SuspiciousSkill", null, 5);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.BLOCK, null));

        var result = service.applyPersonAbilities(extractionResult(claim));

        assertThat(result.getBlockCount()).isEqualTo(1);
        assertThat(result.getPassCount()).isEqualTo(0);
        assertThat(result.getReviewCount()).isEqualTo(0);
        assertThat(result.getErrorCount()).isEqualTo(0);
        verify(abilityEvidenceIngestionService, never()).ingestEmployeeAbility(any(), any());
        verify(eventPublisher, never()).publishEvent(any(com.example.matching.event.KnowledgeGraphRebuildRequestedEvent.class));
    }

    // ---- Error paths ----

    @Test
    @DisplayName("RETRY admission -> errorCount incremented, no writes")
    void passPath_admissionReturnsNull_errorCountIncremented() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.RETRY, null));

        var result = service.applyPersonAbilities(extractionResult(claim));

        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getPassCount()).isEqualTo(0);
        verify(abilityEvidenceIngestionService, never()).ingestEmployeeAbility(any(), any());
    }

    @Test
    @DisplayName("Governed admission throws exception -> errorCount incremented, claim skipped")
    void harnessThrows_errorCountIncremented() {
        PersonAbilityClaim claim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenThrow(new RuntimeException("Admission unavailable"));

        var result = service.applyPersonAbilities(extractionResult(claim));

        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getTotalClaims()).isEqualTo(1);
        verify(abilityEvidenceIngestionService, never()).ingestEmployeeAbility(any(), any());
    }

    @Test
    @DisplayName("Null extraction result -> empty result with zero counts")
    void nullExtractionResult_returnsZeros() {
        var result = service.applyPersonAbilities(null);

        assertThat(result.getTotalClaims()).isEqualTo(0);
        assertThat(result.getPassCount()).isEqualTo(0);
        assertThat(result.getErrorCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Null claims list -> empty result with zero counts")
    void nullClaimsList_returnsZeros() {
        PersonAbilityExtractionResult extractionResult = new PersonAbilityExtractionResult();
        extractionResult.setClaims(null);

        var result = service.applyPersonAbilities(extractionResult);

        assertThat(result.getTotalClaims()).isEqualTo(0);
    }

    // ---- Multi-claim mixed outcomes ----

    @Test
    @DisplayName("Mixed decisions: PASS + REVIEW + BLOCK counted correctly")
    void mixedDecisions_countedCorrectly() {
        PersonAbilityClaim passClaim = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        PersonAbilityClaim reviewClaim = personClaim(1L, "RESUME_PARSE", 11L, "NewTech", null, 2);
        PersonAbilityClaim blockClaim = personClaim(1L, "RESUME_PARSE", 11L, "FakeSkill", null, 5);

        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.PASS, 500L))
                .thenReturn(admission(GovernanceGrant.REVIEW, null))
                .thenReturn(admission(GovernanceGrant.BLOCK, null));

        PersonAbilityExtractionResult extractionResult = new PersonAbilityExtractionResult();
        extractionResult.setClaims(List.of(passClaim, reviewClaim, blockClaim));

        var result = service.applyPersonAbilities(extractionResult);

        assertThat(result.getTotalClaims()).isEqualTo(3);
        assertThat(result.getPassCount()).isEqualTo(1);
        assertThat(result.getReviewCount()).isEqualTo(1);
        assertThat(result.getBlockCount()).isEqualTo(1);
        assertThat(result.getErrorCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Coalesce mode batches profile refresh per employee")
    void coalesceMode_batchesProfileRefresh() {
        PersonAbilityClaim first = personClaim(1L, "RESUME_PARSE", 11L, "Java", 7L, 4);
        PersonAbilityClaim second = personClaim(1L, "RESUME_PARSE", 12L, "Spring", 8L, 3);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.PASS, 500L));

        var result = service.applyPersonAbilities(extractionResult(first, second), true);

        assertThat(result.getPassCount()).isEqualTo(2);
        // coalesce 模式按员工去重：同一员工只合并刷新一次
        verify(admissionService, times(1)).completeBatchForEmployee(1L);
        verify(abilityEvidenceIngestionService, times(2)).ingestEmployeeAbility(500L, "EMP_ABILITY");
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
