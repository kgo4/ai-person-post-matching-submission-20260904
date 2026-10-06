package com.example.matching.service.assessment;

import com.example.matching.common.enums.DecisionStatusEnum;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.common.enums.TagResolutionStatusEnum;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.governance.GovernanceAdmissionRecord;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.entity.workflow.PersonAbilityLevelDecision;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.ability.PersonAbilityProfileMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.governance.GovernanceAdmissionMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.mapper.workflow.PersonAbilityLevelDecisionMapper;
import com.example.matching.service.assessment.impl.AbilityProfileProjectionServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbilityProfileProjectionServiceImplTest {

    @Test
    void projectConfirmed_persistsHarnessApprovedAbilityWithoutCanonicalTag() {
        PersonAbilityLevelDecisionMapper decisionMapper = mock(PersonAbilityLevelDecisionMapper.class);
        PersonAbilityClaimGroupMapper claimGroupMapper = mock(PersonAbilityClaimGroupMapper.class);
        PersonAbilityClaimMapper claimMapper = mock(PersonAbilityClaimMapper.class);
        PersonAbilityProfileMapper profileMapper = mock(PersonAbilityProfileMapper.class);
        EmpAbilityMapper empAbilityMapper = mock(EmpAbilityMapper.class);
        GovernanceAdmissionMapper admissionMapper = mock(GovernanceAdmissionMapper.class);
        AbilityEvidenceCollectionService evidenceCollectionService = mock(AbilityEvidenceCollectionService.class);
        AbilityProfileProjectionService service = new AbilityProfileProjectionServiceImpl(
                decisionMapper, claimGroupMapper, claimMapper, profileMapper,
                empAbilityMapper, admissionMapper, evidenceCollectionService);

        PersonAbilityLevelDecision decision = new PersonAbilityLevelDecision();
        decision.setId(99L);
        decision.setWorkflowId(20L);
        decision.setClaimGroupId(265L);
        decision.setDecisionStatus(DecisionStatusEnum.AUTO_CONFIRMED.getCode());
        decision.setFinalLevel(3);
        decision.setFinalConfidence(80);
        when(decisionMapper.selectList(any())).thenReturn(List.of(decision));

        PersonAbilityClaimGroup group = new PersonAbilityClaimGroup();
        group.setId(265L);
        group.setWorkflowId(20L);
        group.setAssessmentAbilityId(10001L);
        group.setEmpId(7L);
        group.setCanonicalTagId(null);
        group.setNormalizedAbilityName("服务器部署");
        when(claimGroupMapper.selectById(265L)).thenReturn(group);
        when(admissionMapper.selectOne(any())).thenReturn(null);
        when(empAbilityMapper.selectOne(any())).thenReturn(null);
        when(profileMapper.selectOne(any())).thenReturn(null);

        service.projectConfirmed(20L, 9L);

        verify(empAbilityMapper).insert(org.mockito.ArgumentMatchers.argThat((EmpAbility ability) ->
                ability.getTagId() == null
                        && "服务器部署".equals(ability.getAbilityName())
                        && Long.valueOf(20L).equals(ability.getWorkflowId())
                        && Long.valueOf(10001L).equals(ability.getAssessmentAbilityId())));
    }

    @Test
    void projectConfirmed_reusesExistingAdmissionForRepeatedWorkflowProjection() {
        PersonAbilityLevelDecisionMapper decisionMapper = mock(PersonAbilityLevelDecisionMapper.class);
        PersonAbilityClaimGroupMapper claimGroupMapper = mock(PersonAbilityClaimGroupMapper.class);
        PersonAbilityClaimMapper claimMapper = mock(PersonAbilityClaimMapper.class);
        PersonAbilityProfileMapper profileMapper = mock(PersonAbilityProfileMapper.class);
        EmpAbilityMapper empAbilityMapper = mock(EmpAbilityMapper.class);
        GovernanceAdmissionMapper admissionMapper = mock(GovernanceAdmissionMapper.class);
        AbilityEvidenceCollectionService evidenceCollectionService = mock(AbilityEvidenceCollectionService.class);
        AbilityProfileProjectionService service = new AbilityProfileProjectionServiceImpl(
                decisionMapper, claimGroupMapper, claimMapper, profileMapper,
                empAbilityMapper, admissionMapper, evidenceCollectionService);

        PersonAbilityLevelDecision decision = new PersonAbilityLevelDecision();
        decision.setId(99L);
        decision.setWorkflowId(20L);
        decision.setClaimGroupId(265L);
        decision.setDecisionStatus(DecisionStatusEnum.HUMAN_CONFIRMED.getCode());
        decision.setFinalLevel(3);
        decision.setFinalConfidence(80);
        when(decisionMapper.selectList(any())).thenReturn(List.of(decision));

        PersonAbilityClaimGroup group = new PersonAbilityClaimGroup();
        group.setId(265L);
        group.setWorkflowId(20L);
        group.setEmpId(7L);
        group.setCanonicalTagId(11L);
        group.setNormalizedAbilityName("Java");
        group.setTagResolutionStatus(TagResolutionStatusEnum.RESOLVED.getCode());
        group.setStatus(EvidenceStatusEnum.CONFIRMED.getCode());
        when(claimGroupMapper.selectById(265L)).thenReturn(group);

        GovernanceAdmissionRecord existingAdmission = new GovernanceAdmissionRecord();
        existingAdmission.setId(501L);
        when(admissionMapper.selectOne(any())).thenReturn(existingAdmission);
        when(empAbilityMapper.selectOne(any())).thenReturn(new EmpAbility());
        when(profileMapper.selectOne(any())).thenReturn(new PersonAbilityProfile());

        service.projectConfirmed(20L, 9L);

        verify(admissionMapper, times(1)).selectOne(any());
        verify(admissionMapper, times(0)).insert(any(GovernanceAdmissionRecord.class));
        verify(empAbilityMapper).updateById(any(EmpAbility.class));
    }

    @Test
    void projectConfirmed_keepsDifferentUntaggedAbilitiesAsSeparateFormalRecords() {
        PersonAbilityLevelDecisionMapper decisionMapper = mock(PersonAbilityLevelDecisionMapper.class);
        PersonAbilityClaimGroupMapper claimGroupMapper = mock(PersonAbilityClaimGroupMapper.class);
        PersonAbilityClaimMapper claimMapper = mock(PersonAbilityClaimMapper.class);
        PersonAbilityProfileMapper profileMapper = mock(PersonAbilityProfileMapper.class);
        EmpAbilityMapper empAbilityMapper = mock(EmpAbilityMapper.class);
        GovernanceAdmissionMapper admissionMapper = mock(GovernanceAdmissionMapper.class);
        AbilityEvidenceCollectionService evidenceCollectionService = mock(AbilityEvidenceCollectionService.class);
        AbilityProfileProjectionService service = new AbilityProfileProjectionServiceImpl(
                decisionMapper, claimGroupMapper, claimMapper, profileMapper,
                empAbilityMapper, admissionMapper, evidenceCollectionService);

        PersonAbilityLevelDecision javaDecision = decision(20L, 101L, 3);
        PersonAbilityLevelDecision redisDecision = decision(20L, 102L, 4);
        when(decisionMapper.selectList(any())).thenReturn(List.of(javaDecision, redisDecision));
        when(claimGroupMapper.selectById(101L)).thenReturn(group(101L, "Java"));
        when(claimGroupMapper.selectById(102L)).thenReturn(group(102L, "Redis"));
        when(empAbilityMapper.selectList(any())).thenReturn(List.of());
        when(profileMapper.selectList(any())).thenReturn(List.of());
        when(admissionMapper.selectOne(any())).thenReturn(null);

        service.projectConfirmed(20L, 9L);

        org.mockito.ArgumentCaptor<EmpAbility> captured = org.mockito.ArgumentCaptor.forClass(EmpAbility.class);
        verify(empAbilityMapper, times(2)).insert(captured.capture());
        assertEquals(Set.of("Java", "Redis"), captured.getAllValues().stream()
                .map(EmpAbility::getAbilityName)
                .collect(java.util.stream.Collectors.toSet()));
    }

    private PersonAbilityLevelDecision decision(Long workflowId, Long groupId, int level) {
        PersonAbilityLevelDecision decision = new PersonAbilityLevelDecision();
        decision.setId(groupId + 1000);
        decision.setWorkflowId(workflowId);
        decision.setClaimGroupId(groupId);
        decision.setDecisionStatus(DecisionStatusEnum.HUMAN_CONFIRMED.getCode());
        decision.setFinalLevel(level);
        decision.setFinalConfidence(80);
        return decision;
    }

    private PersonAbilityClaimGroup group(Long id, String abilityName) {
        PersonAbilityClaimGroup group = new PersonAbilityClaimGroup();
        group.setId(id);
        group.setWorkflowId(20L);
        group.setAssessmentAbilityId(id + 2000);
        group.setEmpId(7L);
        group.setCanonicalTagId(null);
        group.setNormalizedAbilityName(abilityName);
        return group;
    }
}
