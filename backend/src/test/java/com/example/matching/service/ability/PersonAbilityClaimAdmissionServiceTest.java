package com.example.matching.service.ability;

import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.mapper.governance.GovernanceAdmissionMapper;
import com.example.matching.event.AbilityChangeEvent;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.ability.impl.PersonAbilityClaimAdmissionServiceImpl;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.service.harness.AiTrustHarnessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonAbilityClaimAdmissionServiceTest {

    @Mock private PersonAbilityClaimMapper claimMapper;
    @Mock private EmpAbilityMapper empAbilityMapper;
    @Mock private AiHarnessCheckLogMapper harnessLogMapper;
    @Mock private GovernanceAdmissionMapper admissionMapper;
    @Mock private AiTrustHarnessService harnessService;
    @Mock private AbilityTagService abilityTagService;
    @Mock private PersonAbilityProfileAgent profileAgent;
    @Mock private AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private PersonAbilityClaimAdmissionServiceImpl admissionService;

    @Test
    void pass_persistsClaimWritesSourceFactAndRefreshesProfile() {
        var source = sourceClaim("Java", 7L);
        var decision = decision("PASS", "HNS_PASS");
        var harnessLog = new AiHarnessCheckLog();
        harnessLog.setId(101L);
        when(harnessLogMapper.selectOne(any())).thenReturn(harnessLog);
        when(empAbilityMapper.selectOne(any())).thenReturn(null);

        PersonAbilityClaim admitted = admissionService.admit(source, decision);

        ArgumentCaptor<PersonAbilityClaim> claimCaptor = ArgumentCaptor.forClass(PersonAbilityClaim.class);
        verify(claimMapper).insert(claimCaptor.capture());
        assertThat(claimCaptor.getValue().getHarnessLogId()).isEqualTo(101L);
        assertThat(claimCaptor.getValue().getTagId()).isEqualTo(7L);
        assertThat(admitted.getStatus()).isEqualTo("FUSED");
        verify(empAbilityMapper).insert(any(EmpAbility.class));
        verify(profileAgent).refreshProfile(1L);
        verify(abilityEvidenceIngestionService).ingestEmployeeAbility(any(), eq("EMP_ABILITY"));
        verify(eventPublisher).publishEvent(any(AbilityChangeEvent.class));
        var order = inOrder(claimMapper, empAbilityMapper, profileAgent);
        order.verify(claimMapper).insert(any(PersonAbilityClaim.class));
        order.verify(empAbilityMapper).insert(any(EmpAbility.class));
        order.verify(profileAgent).refreshProfile(1L);
        order.verify(claimMapper).updateById(any(PersonAbilityClaim.class));
    }

    @Test
    void review_persistsPendingClaimWithoutProfileRefreshOrFacts() {
        var source = sourceClaim("Java", 7L);
        var decision = decision("REVIEW", "HNS_REVIEW");
        var harnessLog = new AiHarnessCheckLog();
        harnessLog.setId(102L);
        when(harnessLogMapper.selectOne(any())).thenReturn(harnessLog);

        admissionService.admit(source, decision);

        ArgumentCaptor<PersonAbilityClaim> claimCaptor = ArgumentCaptor.forClass(PersonAbilityClaim.class);
        verify(claimMapper).insert(claimCaptor.capture());
        assertThat(claimCaptor.getValue().getStatus()).isEqualTo("PENDING_HARNESS_REVIEW");
        // REVIEW 只能写待审候选记录：不得写 emp_ability、不得刷新画像、不得触发事件
        verify(empAbilityMapper, never()).insert(any(EmpAbility.class));
        verify(profileAgent, never()).refreshProfile(1L);
        verify(eventPublisher, never()).publishEvent(any(AbilityChangeEvent.class));
    }

    @Test
    void acceptReviewFindsLegacyPendingClaimWhenHarnessLinkWasNotPersisted() {
        AiHarnessCheckLog harnessLog = new AiHarnessCheckLog();
        harnessLog.setId(1313L);
        harnessLog.setScenario("PERSON_ABILITY");
        harnessLog.setClaimType("EMP_ABILITY");
        harnessLog.setSourceType("RESUME_PARSE");
        harnessLog.setSourceRefId(55L);
        harnessLog.setBusinessTargetType("EMP_ABILITY");
        harnessLog.setBusinessTargetId(1L);
        harnessLog.setClaimText("Java");

        PersonAbilityClaim pending = new PersonAbilityClaim();
        pending.setEmpId(1L);
        pending.setTagId(7L);
        pending.setAbilityName("Java");
        pending.setNormalizedAbilityName("java");
        pending.setClaimedLevel(4);
        pending.setSourceType("RESUME_PARSE");
        pending.setSourceRefId(55L);
        pending.setEvidenceText("Relevant project evidence");
        pending.setConfidenceScore(new BigDecimal("80"));
        pending.setStatus("PENDING_HARNESS_REVIEW");
        pending.setHarnessLogId(null);
        when(claimMapper.selectOne(any())).thenReturn(null, pending);
        when(harnessLogMapper.selectById(1313L)).thenReturn(harnessLog);
        when(harnessService.verify(any())).thenReturn(decision("PASS", "HNS_REVERIFY"));
        when(empAbilityMapper.selectOne(any())).thenReturn(null);

        assertThat(admissionService.acceptReview(1313L)).isTrue();
        assertThat(pending.getHarnessLogId()).isEqualTo(1313L);
        verify(claimMapper, org.mockito.Mockito.atLeastOnce()).updateById(pending);
        verify(empAbilityMapper).insert(any(EmpAbility.class));
    }

    @Test
    void pass_withMissingTagWritesFormalPersonnelAbilityWithoutCreatingTag() {
        var source = sourceClaim("Rust", null);
        var decision = decision("PASS", "HNS_NEW_TAG");
        var harnessLog = new AiHarnessCheckLog();
        harnessLog.setId(103L);
        when(harnessLogMapper.selectOne(any())).thenReturn(harnessLog);
        when(empAbilityMapper.selectOne(any())).thenReturn(null);

        admissionService.admit(source, decision);

        org.mockito.Mockito.verifyNoInteractions(abilityTagService);
        ArgumentCaptor<PersonAbilityClaim> claimCaptor = ArgumentCaptor.forClass(PersonAbilityClaim.class);
        verify(claimMapper).insert(claimCaptor.capture());
        assertThat(claimCaptor.getValue().getTagId()).isNull();
        ArgumentCaptor<EmpAbility> abilityCaptor = ArgumentCaptor.forClass(EmpAbility.class);
        verify(empAbilityMapper).insert(abilityCaptor.capture());
        assertThat(abilityCaptor.getValue().getTagId()).isNull();
        assertThat(abilityCaptor.getValue().getAbilityName()).isEqualTo("Rust");
        verify(profileAgent).refreshProfile(1L);
    }

    private com.example.matching.agent.dto.person.PersonAbilityClaim sourceClaim(String name, Long tagId) {
        var claim = new com.example.matching.agent.dto.person.PersonAbilityClaim();
        claim.setEmpId(1L);
        claim.setSourceType("RESUME_PARSE");
        claim.setSourceRefId(11L);
        claim.setAbilityName(name);
        claim.setNormalizedAbilityName(name.toLowerCase());
        claim.setAbilityTagId(tagId);
        claim.setMasteryLevel(4);
        claim.setConfidenceScore(new BigDecimal("80"));
        claim.setEvidenceText("Relevant project evidence");
        claim.setSourceRefs(java.util.List.of("source:RESUME_PARSE:11"));
        return claim;
    }

    private AiHarnessDecisionDTO decision(String value, String checkCode) {
        var decision = new AiHarnessDecisionDTO();
        decision.setDecision(value);
        decision.setCheckCode(checkCode);
        return decision;
    }
}
