package com.example.matching.service.agent;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.dto.governance.GovernanceAdmission;
import com.example.matching.dto.governance.GovernanceGrant;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.ability.PersonAbilityClaimAdmissionService;
import com.example.matching.service.agent.impl.AgentBusinessApplyServiceImpl;
import com.example.matching.service.governance.GovernedAdmissionService;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.ReflectionUtils;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentBusinessApplyServiceTest {

    @Mock private GovernedAdmissionService governedAdmissionService;
    @Mock private PersonAbilityClaimAdmissionService admissionService;
    @Mock private AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;

    @Mock private com.example.matching.service.common.VectorRecallCacheEpoch vectorRecallCacheEpoch;
    @Mock private com.example.matching.agent.service.AgentClaimConflictDetector conflictDetector;
    @InjectMocks private AgentBusinessApplyServiceImpl service;

    @Test
    void pass_delegatesPersonnelClaimToGovernedAdmission() {
        PersonAbilityClaim claim = claim();
        when(governedAdmissionService.admitPersonAbility(claim))
                .thenReturn(admission(GovernanceGrant.PASS, 500L));

        var result = service.applyPersonAbilities(result(claim));

        assertThat(result.getPassCount()).isEqualTo(1);
        verify(governedAdmissionService).admitPersonAbility(claim);
        verify(abilityEvidenceIngestionService).ingestEmployeeAbility(500L, "EMP_ABILITY");
    }

    @Test
    void review_persistsPendingCandidateWithoutSideEffects() {
        PersonAbilityClaim claim = claim();
        when(governedAdmissionService.admitPersonAbility(claim))
                .thenReturn(admission(GovernanceGrant.REVIEW, null));

        var result = service.applyPersonAbilities(result(claim));

        assertThat(result.getReviewCount()).isEqualTo(1);
        verify(abilityEvidenceIngestionService, never()).ingestEmployeeAbility(any(), any());
        verify(admissionService, never()).completeBatchForEmployee(any());
    }

    @Test
    void block_skipsAllSideEffects() {
        PersonAbilityClaim claim = claim();
        when(governedAdmissionService.admitPersonAbility(claim))
                .thenReturn(admission(GovernanceGrant.BLOCK, null));

        var result = service.applyPersonAbilities(result(claim));

        assertThat(result.getBlockCount()).isEqualTo(1);
        verify(abilityEvidenceIngestionService, never()).ingestEmployeeAbility(any(), any());
        verify(admissionService, never()).completeBatchForEmployee(any());
    }

    @Test
    void sourceValidatedPostClaimWritesProfileWithoutHarness() {
        com.example.matching.agent.dto.post.PostAbilityClaim claim = new com.example.matching.agent.dto.post.PostAbilityClaim();
        claim.setPostId(1L);
        claim.setAbilityTagId(7L);
        claim.setAbilityName("Java");
        claim.setRequiredLevel(4);
        claim.setWeight(new BigDecimal("100"));
        claim.setIsCore(true);
        claim.setIsRequired(true);
        claim.setSourceType("JD_IMPORT");
        claim.setEvidenceText("岗位原文中的 Java 服务开发要求");
        claim.setSourceRefs(List.of("source:JD_IMPORT:1"));
        when(postAbilityModelMapper.selectOne(any())).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            ((com.example.matching.entity.post.PostAbilityModel) invocation.getArgument(0)).setId(101L);
            return 1;
        }).when(postAbilityModelMapper).insert(any(com.example.matching.entity.post.PostAbilityModel.class));

        com.example.matching.agent.dto.post.PostAbilityExtractionResult result =
                new com.example.matching.agent.dto.post.PostAbilityExtractionResult();
        result.setClaims(List.of(claim));

        service.applyPostAbilities(result);

        verify(abilityEvidenceIngestionService, never()).ingestPostAbilityModel(any(), any());
        verify(governedAdmissionService, never()).admitPostAbility(any());
        verify(eventPublisher).publishEvent(any(com.example.matching.event.PostModelChangeEvent.class));
    }

    @Test
    void graphRefreshDebounce_usesAtomicTimestampState() {
        Field field = ReflectionUtils.findField(AgentBusinessApplyServiceImpl.class, "lastGraphRefreshAt");

        assertThat(field).isNotNull();
        assertThat(field.getType()).isEqualTo(AtomicLong.class);
    }

    @Test
    void batchImport_coalescesProfileRefreshAndAbilityChangePerEmployee() throws Exception {
        PersonAbilityClaim first = claim();
        PersonAbilityClaim second = claim();
        second.setAbilityName("Spring");
        second.setAbilityTagId(8L);
        when(governedAdmissionService.admitPersonAbility(any()))
                .thenReturn(admission(GovernanceGrant.PASS, 500L));

        Method batchMethod = AgentBusinessApplyServiceImpl.class.getMethod(
                "applyPersonAbilities", PersonAbilityExtractionResult.class, boolean.class);
        var result = (AgentBusinessApplyService.PersonAbilityApplyResult) batchMethod.invoke(
                service, result(first, second), true);

        assertThat(result.getPassCount()).isEqualTo(2);
        verify(admissionService).completeBatchForEmployee(1L);
    }

    private PersonAbilityExtractionResult result(PersonAbilityClaim... claims) {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claims));
        return result;
    }

    private PersonAbilityClaim claim() {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setEmpId(1L);
        claim.setSourceType("RESUME_PARSE");
        claim.setSourceRefId(11L);
        claim.setAbilityName("Java");
        claim.setAbilityTagId(7L);
        claim.setMasteryLevel(4);
        claim.setConfidenceScore(new BigDecimal("80"));
        claim.setEvidenceText("Relevant project evidence");
        claim.setSourceRefs(List.of("fact:EMP_ABILITY:11"));
        return claim;
    }

    private GovernanceAdmission admission(GovernanceGrant grant, Long businessTargetId) {
        GovernanceAdmission admission = new GovernanceAdmission();
        admission.setFinalDecision(grant.name());
        admission.setBusinessTargetId(businessTargetId);
        return admission;
    }
}
