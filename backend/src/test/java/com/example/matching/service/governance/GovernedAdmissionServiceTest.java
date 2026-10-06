package com.example.matching.service.governance;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.dto.governance.GovernanceAdmission;
import com.example.matching.dto.governance.GovernanceGrant;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.governance.GovernanceAdmissionRecord;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.governance.GovernanceAdmissionMapper;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.service.governance.impl.GovernedAdmissionEntityBuilder;
import com.example.matching.service.governance.impl.GovernedAdmissionServiceImpl;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.service.system.AbilityTagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Governed Admission Service")
class GovernedAdmissionServiceTest {

    @Mock private AiTrustHarnessService harnessService;
    @Mock private PersonAbilityClaimMapper personClaimMapper;
    @Mock private EmpAbilityMapper empAbilityMapper;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;
    @Mock private AiHarnessCheckLogMapper harnessLogMapper;
    @Mock private GovernanceAdmissionMapper admissionMapper;
    @Mock private AbilityTagService abilityTagService;
    @Mock private AbilityTagCandidateService abilityTagCandidateService;
    @Mock private ObjectMapper objectMapper;

    private GovernedAdmissionServiceImpl service;

    @BeforeEach
    void setUp() {
        GovernedAdmissionEntityBuilder builder = new GovernedAdmissionEntityBuilder(
                personClaimMapper, empAbilityMapper, postAbilityModelMapper, harnessLogMapper,
                admissionMapper, abilityTagService, abilityTagCandidateService, new com.fasterxml.jackson.databind.ObjectMapper());
        service = new GovernedAdmissionServiceImpl(
                harnessService, admissionMapper, builder, objectMapper);
        lenient().doAnswer(inv -> {
            GovernanceAdmissionRecord record = inv.getArgument(0);
            record.setId(1L);
            return 1;
        }).when(admissionMapper).insert(any(GovernanceAdmissionRecord.class));
    }

    @Test
    @DisplayName("PASS: writes to fact table with governance_admission_id")
    void passWritesToFactTableWithAdmissionId() {
        PersonAbilityClaim claim = personClaim(1L, "Java", 7L, 4);
        when(harnessService.verify(argThat(c -> "PERSON_ABILITY".equals(c.getScenario()))))
                .thenReturn(harnessDecision(AiHarnessDecisionDTO.PASS));

        GovernanceAdmission result = service.admitPersonAbility(claim);

        assertThat(result.getFinalDecision()).isEqualTo(GovernanceGrant.PASS.name());
        assertThat(result.getApplyStatus()).isEqualTo("FUSED");
        assertThat(result.getBusinessTargetType()).isEqualTo("EMP_ABILITY");
        verify(empAbilityMapper).insert(argThat((EmpAbility a) ->
                a.getGovernanceAdmissionId() != null && a.getGovernanceAdmissionId().equals(1L)));
        verify(admissionMapper).insert(any(GovernanceAdmissionRecord.class));
    }

    @Test
    @DisplayName("人员能力 Harness 声明使用 EMP_ABILITY 类型")
    void personHarnessClaimUsesEmployeeAbilityClaimType() {
        PersonAbilityClaim claim = personClaim(1L, "Java", 7L, 4);

        AiHarnessClaimDTO harnessClaim = new GovernedAdmissionEntityBuilder(
                personClaimMapper, empAbilityMapper, postAbilityModelMapper, harnessLogMapper,
                admissionMapper, abilityTagService, abilityTagCandidateService,
                new com.fasterxml.jackson.databind.ObjectMapper()).buildPersonHarnessClaim(claim);

        assertThat(harnessClaim.getScenario()).isEqualTo("PERSON_ABILITY");
        assertThat(harnessClaim.getClaimType()).isEqualTo("EMP_ABILITY");
    }

    @Test
    @DisplayName("REVIEW: does not write to emp_ability, persists PENDING claim only")
    void reviewDoesNotWriteToEmpAbility() {
        PersonAbilityClaim claim = personClaim(1L, "EmergingTech", null, 2);
        when(harnessService.verify(any())).thenReturn(harnessDecision(AiHarnessDecisionDTO.REVIEW));

        GovernanceAdmission result = service.admitPersonAbility(claim);

        assertThat(result.getFinalDecision()).isEqualTo(GovernanceGrant.REVIEW.name());
        assertThat(result.getApplyStatus()).isEqualTo("PENDING_HARNESS_REVIEW");
        verify(empAbilityMapper, never()).insert(any(EmpAbility.class));
        verify(empAbilityMapper, never()).updateById(any(EmpAbility.class));
        verify(personClaimMapper).insert(argThat((com.example.matching.entity.ability.PersonAbilityClaim e) ->
                "PENDING_HARNESS_REVIEW".equals(e.getStatus())));
    }

    @Test
    @DisplayName("BLOCK: writes nothing, admission persisted as BLOCKED")
    void blockWritesNothing() {
        PersonAbilityClaim claim = personClaim(1L, "Invalid", 7L, 0);
        when(harnessService.verify(any())).thenReturn(harnessDecision(AiHarnessDecisionDTO.BLOCK));

        GovernanceAdmission result = service.admitPersonAbility(claim);

        assertThat(result.getFinalDecision()).isEqualTo(GovernanceGrant.BLOCK.name());
        assertThat(result.getApplyStatus()).isEqualTo("BLOCKED");
        verify(empAbilityMapper, never()).insert(any(EmpAbility.class));
        verify(personClaimMapper, never()).insert(any(com.example.matching.entity.ability.PersonAbilityClaim.class));
        ArgumentCaptor<GovernanceAdmissionRecord> captured = ArgumentCaptor.forClass(GovernanceAdmissionRecord.class);
        verify(admissionMapper).insert(captured.capture());
        assertThat(captured.getValue().getFinalDecision()).isEqualTo("BLOCK");
    }

    @Test
    @DisplayName("Harness exception -> RETRY, admission persisted, no writes")
    void harnessExceptionReturnsRetry() {
        PersonAbilityClaim claim = personClaim(1L, "Java", 7L, 4);
        when(harnessService.verify(any())).thenThrow(new RuntimeException("DB down"));

        GovernanceAdmission result = service.admitPersonAbility(claim);

        assertThat(result.getFinalDecision()).isEqualTo(GovernanceGrant.RETRY.name());
        assertThat(result.getDecisionRule()).isEqualTo("HARNESS_ERROR");
        verify(empAbilityMapper, never()).insert(any(EmpAbility.class));
        verify(admissionMapper).insert(any(GovernanceAdmissionRecord.class));
    }

    @Test
    @DisplayName("Admission assigns check code and traceId")
    void admissionAssignsCheckCodeAndTraceId() {
        PersonAbilityClaim claim = personClaim(1L, "Java", 7L, 4);
        AiHarnessDecisionDTO decision = harnessDecision(AiHarnessDecisionDTO.PASS);
        decision.setCheckCode("HNS_TEST123");
        decision.setTraceId("trace-abc-123");
        when(harnessService.verify(any())).thenReturn(decision);

        GovernanceAdmission result = service.admitPersonAbility(claim);

        assertThat(result.getHarnessCheckCode()).isEqualTo("HNS_TEST123");
        assertThat(result.getTraceId()).isEqualTo("trace-abc-123");
        ArgumentCaptor<GovernanceAdmissionRecord> captured = ArgumentCaptor.forClass(GovernanceAdmissionRecord.class);
        verify(admissionMapper).insert(captured.capture());
        assertThat(captured.getValue().getHarnessCheckCode()).isEqualTo("HNS_TEST123");
        assertThat(captured.getValue().getTraceId()).isEqualTo("trace-abc-123");
    }

    @Test
    @DisplayName("POST ability PASS upserts post_ability_model with admission id")
    void postAbilityPassWritesPostModel() {
        PostAbilityClaim claim = postClaim(1L, 7L, "Java", 4);
        when(harnessService.verify(argThat(c -> "POST_ABILITY".equals(c.getScenario()))))
                .thenReturn(harnessDecision(AiHarnessDecisionDTO.PASS));
        when(postAbilityModelMapper.selectOne(any())).thenReturn(null);

        GovernanceAdmission result = service.admitPostAbility(claim);

        assertThat(result.getFinalDecision()).isEqualTo(GovernanceGrant.PASS.name());
        assertThat(result.getApplyStatus()).isEqualTo("FUSED");
        assertThat(result.getBusinessTargetType()).isEqualTo("POST_ABILITY_MODEL");
        verify(postAbilityModelMapper).insert(argThat((PostAbilityModel m) ->
                m.getGovernanceAdmissionId() != null));
    }

    @Test
    @DisplayName("POST ability PASS updates existing model instead of insert")
    void postAbilityPassUpdatesExistingModel() {
        PostAbilityClaim claim = postClaim(1L, 7L, "Java", 4);
        when(harnessService.verify(any())).thenReturn(harnessDecision(AiHarnessDecisionDTO.PASS));
        PostAbilityModel existing = new PostAbilityModel();
        existing.setId(100L);
        existing.setPostId(1L);
        existing.setTagId(7L);
        when(postAbilityModelMapper.selectOne(any())).thenReturn(existing);

        GovernanceAdmission result = service.admitPostAbility(claim);

        assertThat(result.getFinalDecision()).isEqualTo(GovernanceGrant.PASS.name());
        verify(postAbilityModelMapper, never()).insert(any(PostAbilityModel.class));
        verify(postAbilityModelMapper).updateById(argThat((PostAbilityModel m) ->
                m.getId().equals(100L) && m.getGovernanceAdmissionId() != null));
    }

    @Test
    @DisplayName("POST ability REVIEW writes candidate only, never post_ability_model")
    void postAbilityReviewWritesCandidateOnly() {
        PostAbilityClaim claim = postClaim(1L, 7L, "Emerging", 0);
        when(harnessService.verify(any())).thenReturn(harnessDecision(AiHarnessDecisionDTO.REVIEW));
        when(abilityTagCandidateService.addCandidate(any())).thenReturn(99L);

        GovernanceAdmission result = service.admitPostAbility(claim);

        assertThat(result.getFinalDecision()).isEqualTo(GovernanceGrant.REVIEW.name());
        assertThat(result.getApplyStatus()).isEqualTo("PENDING_HARNESS_REVIEW");
        verify(postAbilityModelMapper, never()).insert(any(PostAbilityModel.class));
        verify(abilityTagCandidateService).addCandidate(any(AbilityTagCandidate.class));
    }

    @Test
    @DisplayName("POST ability BLOCK writes nothing")
    void postAbilityBlockWritesNothing() {
        PostAbilityClaim claim = postClaim(1L, 7L, "Ghost", 0);
        when(harnessService.verify(any())).thenReturn(harnessDecision(AiHarnessDecisionDTO.BLOCK));

        GovernanceAdmission result = service.admitPostAbility(claim);

        assertThat(result.getFinalDecision()).isEqualTo(GovernanceGrant.BLOCK.name());
        verify(postAbilityModelMapper, never()).insert(any(PostAbilityModel.class));
        verify(abilityTagCandidateService, never()).addCandidate(any());
    }

    @Test
    @DisplayName("POST ability without source evidence is blocked before Harness and writes nothing")
    void postAbilityWithoutSourceEvidenceIsBlockedBeforeHarness() {
        PostAbilityClaim claim = postClaim(1L, 7L, "Ghost", 3);
        claim.setEvidenceText(" ");

        GovernanceAdmission result = service.admitPostAbility(claim);

        assertThat(result.getFinalDecision()).isEqualTo(GovernanceGrant.BLOCK.name());
        assertThat(result.getDecisionRule()).isEqualTo("MISSING_SOURCE_EVIDENCE");
        verify(harnessService, never()).verify(any());
        verify(postAbilityModelMapper, never()).insert(any(PostAbilityModel.class));
        verify(abilityTagCandidateService, never()).addCandidate(any());
    }

    @Test
    @DisplayName("grantFrom maps decisions correctly")
    void grantFromMapsCorrectly() {
        assertThat(GovernedAdmissionServiceImpl.grantFrom(harnessDecision(AiHarnessDecisionDTO.PASS)))
                .isEqualTo(GovernanceGrant.PASS);
        assertThat(GovernedAdmissionServiceImpl.grantFrom(harnessDecision(AiHarnessDecisionDTO.REVIEW)))
                .isEqualTo(GovernanceGrant.REVIEW);
        assertThat(GovernedAdmissionServiceImpl.grantFrom(harnessDecision(AiHarnessDecisionDTO.BLOCK)))
                .isEqualTo(GovernanceGrant.BLOCK);
    }

    @Test
    @DisplayName("RETRY decision is recognized")
    void retryDecisionIsRecognized() {
        AiHarnessDecisionDTO dto = new AiHarnessDecisionDTO();
        dto.setDecision(AiHarnessDecisionDTO.RETRY);
        assertThat(dto.isRetry()).isTrue();
        assertThat(GovernedAdmissionServiceImpl.grantFrom(dto)).isEqualTo(GovernanceGrant.RETRY);
    }

    @Test
    @DisplayName("RETRY decision writes nothing and persists admission")
    void retryWritesNothing() {
        PersonAbilityClaim claim = personClaim(1L, "Java", 7L, 4);
        when(harnessService.verify(any())).thenReturn(harnessDecision(AiHarnessDecisionDTO.RETRY));

        GovernanceAdmission result = service.admitPersonAbility(claim);

        assertThat(result.getFinalDecision()).isEqualTo(GovernanceGrant.RETRY.name());
        assertThat(result.getApplyStatus()).isEqualTo("RETRYABLE");
        verify(empAbilityMapper, never()).insert(any(EmpAbility.class));
        verify(personClaimMapper, never()).insert(any(com.example.matching.entity.ability.PersonAbilityClaim.class));
        verify(admissionMapper).insert(any(GovernanceAdmissionRecord.class));
    }

    private PersonAbilityClaim personClaim(Long empId, String abilityName, Long tagId, int masteryLevel) {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setEmpId(empId);
        claim.setAbilityName(abilityName);
        claim.setAbilityTagId(tagId);
        claim.setMasteryLevel(masteryLevel);
        claim.setSourceType("RESUME_PARSE");
        claim.setSourceRefId(100L);
        claim.setConfidenceScore(new BigDecimal("80"));
        claim.setEvidenceText("Test evidence");
        claim.setSourceRefs(List.of("fact:EMP_ABILITY:100"));
        return claim;
    }

    private PostAbilityClaim postClaim(Long postId, Long tagId, String abilityName, int requiredLevel) {
        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setPostId(postId);
        claim.setAbilityTagId(tagId);
        claim.setAbilityName(abilityName);
        claim.setRequiredLevel(requiredLevel);
        claim.setWeight(new BigDecimal("100"));
        claim.setIsCore(true);
        claim.setIsRequired(true);
        claim.setSourceType("JD_IMPORT");
        claim.setEvidenceText("JD requires " + abilityName);
        claim.setSourceRefs(List.of("fact:POST_ABILITY_MODEL:100"));
        return claim;
    }

    private AiHarnessDecisionDTO harnessDecision(String decision) {
        AiHarnessDecisionDTO dto = new AiHarnessDecisionDTO();
        dto.setDecision(decision);
        dto.setCheckCode("HNS_" + decision);
        dto.setSupportScore(new BigDecimal("85"));
        dto.setRiskLevel("LOW");
        dto.setReasons(List.of("Test reason"));
        return dto;
    }
}
