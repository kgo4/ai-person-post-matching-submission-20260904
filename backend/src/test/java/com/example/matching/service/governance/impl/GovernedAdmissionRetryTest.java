package com.example.matching.service.governance.impl;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.service.governance.impl.GovernedAdmissionEntityBuilder;
import com.example.matching.dto.governance.GovernanceAdmission;
import com.example.matching.dto.governance.GovernanceGrant;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.governance.GovernanceAdmissionRecord;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.governance.GovernanceAdmissionMapper;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Governed Admission RETRY loop (N9)")
class GovernedAdmissionRetryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private AiTrustHarnessService harnessService;
    @Mock private PersonAbilityClaimMapper personClaimMapper;
    @Mock private EmpAbilityMapper empAbilityMapper;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;
    @Mock private AiHarnessCheckLogMapper harnessLogMapper;
    @Mock private GovernanceAdmissionMapper admissionMapper;
    @Mock private AbilityTagService abilityTagService;
    @Mock private AbilityTagCandidateService abilityTagCandidateService;

    private GovernedAdmissionServiceImpl service;

    @BeforeEach
    void setUp() {
        GovernedAdmissionEntityBuilder builder = new GovernedAdmissionEntityBuilder(
                personClaimMapper, empAbilityMapper, postAbilityModelMapper, harnessLogMapper,
                admissionMapper, abilityTagService, abilityTagCandidateService, MAPPER);
        service = new GovernedAdmissionServiceImpl(
                harnessService, admissionMapper, builder, MAPPER);
    }

    @Test
    @DisplayName("RETRY -> PASS 重试在原始记录上融合，不产生新记录")
    void retryPersonPassFusesOnOriginalRecord() throws Exception {
        GovernanceAdmissionRecord record = retryablePersonRecord();
        when(admissionMapper.selectById(1L)).thenReturn(record);
        when(harnessService.verify(any())).thenReturn(decision(AiHarnessDecisionDTO.PASS));

        GovernanceAdmission result = service.retryDueAdmission(1L);

        assertThat(result.getFinalDecision()).isEqualTo(GovernanceGrant.PASS.name());
        assertThat(result.getApplyStatus()).isEqualTo("FUSED");
        ArgumentCaptor<GovernanceAdmissionRecord> updated = ArgumentCaptor.forClass(GovernanceAdmissionRecord.class);
        verify(admissionMapper, atLeast(2)).updateById(updated.capture());
        GovernanceAdmissionRecord last = updated.getAllValues().get(updated.getAllValues().size() - 1);
        assertThat(last.getFinalDecision()).isEqualTo("PASS");
        assertThat(last.getApplyStatus()).isEqualTo("FUSED");
        verify(empAbilityMapper).insert(any(com.example.matching.entity.employee.EmpAbility.class));
        verify(admissionMapper, never()).insert(any(GovernanceAdmissionRecord.class));
    }

    @Test
    @DisplayName("重试仍为 RETRY：次数+1，退避翻倍（5min -> 10min）")
    void retryAgainBacksOffExponentially() throws Exception {
        GovernanceAdmissionRecord record = retryablePersonRecord();
        when(admissionMapper.selectById(1L)).thenReturn(record);
        when(harnessService.verify(any())).thenReturn(decision(AiHarnessDecisionDTO.RETRY));

        GovernanceAdmission result = service.retryDueAdmission(1L);

        assertThat(result.getApplyStatus()).isEqualTo("RETRYABLE");
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getNextRetryTime()).isAfter(LocalDateTime.now().plusMinutes(9));
        assertThat(result.getNextRetryTime()).isBeforeOrEqualTo(LocalDateTime.now().plusMinutes(11));
        verify(empAbilityMapper, never()).insert(any(com.example.matching.entity.employee.EmpAbility.class));
    }

    @Test
    @DisplayName("未到期的 RETRYABLE 记录不触发重试")
    void notDueRecordIsSkipped() throws Exception {
        GovernanceAdmissionRecord record = retryablePersonRecord();
        record.setNextRetryTime(LocalDateTime.now().plusMinutes(5));
        when(admissionMapper.selectById(1L)).thenReturn(record);

        GovernanceAdmission result = service.retryDueAdmission(1L);

        assertThat(result).isNull();
        verifyNoInteractions(harnessService);
        verify(admissionMapper, never()).updateById(any(GovernanceAdmissionRecord.class));
    }

    @Test
    @DisplayName("重试次数耗尽 -> RETRY_EXHAUSTED，不再调用 Harness")
    void exhaustedRetryMarksTerminal() throws Exception {
        GovernanceAdmissionRecord record = retryablePersonRecord();
        record.setRetryCount(10);
        when(admissionMapper.selectById(1L)).thenReturn(record);

        GovernanceAdmission result = service.retryDueAdmission(1L);

        assertThat(result).isNull();
        verifyNoInteractions(harnessService);
        ArgumentCaptor<GovernanceAdmissionRecord> updated = ArgumentCaptor.forClass(GovernanceAdmissionRecord.class);
        verify(admissionMapper).updateById(updated.capture());
        assertThat(updated.getValue().getApplyStatus()).isEqualTo("RETRY_EXHAUSTED");
    }

    @Test
    @DisplayName("重试 BLOCK -> 原记录 BLOCKED，无任何业务写入")
    void retryBlockMarksBlocked() throws Exception {
        GovernanceAdmissionRecord record = retryablePersonRecord();
        when(admissionMapper.selectById(1L)).thenReturn(record);
        when(harnessService.verify(any())).thenReturn(decision(AiHarnessDecisionDTO.BLOCK));

        GovernanceAdmission result = service.retryDueAdmission(1L);

        assertThat(result.getFinalDecision()).isEqualTo(GovernanceGrant.BLOCK.name());
        assertThat(result.getApplyStatus()).isEqualTo("BLOCKED");
        verify(empAbilityMapper, never()).insert(any(com.example.matching.entity.employee.EmpAbility.class));
        verify(personClaimMapper, never()).insert(any(com.example.matching.entity.ability.PersonAbilityClaim.class));
    }

    @Test
    @DisplayName("重试 REVIEW -> 生成 PENDING_HARNESS_REVIEW 待审声明")
    void retryReviewCreatesPendingClaim() throws Exception {
        GovernanceAdmissionRecord record = retryablePersonRecord();
        when(admissionMapper.selectById(1L)).thenReturn(record);
        when(harnessService.verify(any())).thenReturn(decision(AiHarnessDecisionDTO.REVIEW));

        GovernanceAdmission result = service.retryDueAdmission(1L);

        assertThat(result.getFinalDecision()).isEqualTo(GovernanceGrant.REVIEW.name());
        assertThat(result.getApplyStatus()).isEqualTo("PENDING_HARNESS_REVIEW");
        verify(personClaimMapper).insert(argThat((com.example.matching.entity.ability.PersonAbilityClaim e) ->
                "PENDING_HARNESS_REVIEW".equals(e.getStatus())));
        verify(empAbilityMapper, never()).insert(any(com.example.matching.entity.employee.EmpAbility.class));
    }

    @Test
    @DisplayName("重试执行异常 -> 计数+1 并退避，不崩溃")
    void retryExceptionBacksOff() throws Exception {
        GovernanceAdmissionRecord record = retryablePersonRecord();
        record.setClaimPayloadJson("not-valid-json{{");
        when(admissionMapper.selectById(1L)).thenReturn(record);

        GovernanceAdmission result = service.retryDueAdmission(1L);

        assertThat(result).isNull();
        ArgumentCaptor<GovernanceAdmissionRecord> updated = ArgumentCaptor.forClass(GovernanceAdmissionRecord.class);
        verify(admissionMapper).updateById(updated.capture());
        assertThat(updated.getValue().getRetryCount()).isEqualTo(1);
        assertThat(updated.getValue().getNextRetryTime()).isNotNull();
    }

    @Test
    @DisplayName("POST 能力重试 PASS -> upsert post_ability_model")
    void retryPostPassUpsertsModel() throws Exception {
        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setPostId(1L);
        claim.setAbilityTagId(7L);
        claim.setAbilityName("Java");
        claim.setRequiredLevel(4);
        claim.setSourceType("JD_IMPORT");
        claim.setEvidenceText("JD requires Java");

        GovernanceAdmissionRecord record = new GovernanceAdmissionRecord();
        record.setId(2L);
        record.setScenario("POST_ABILITY");
        record.setApplyStatus("RETRYABLE");
        record.setRetryCount(0);
        record.setNextRetryTime(LocalDateTime.now().minusMinutes(1));
        record.setClaimPayloadJson(MAPPER.writeValueAsString(claim));
        when(admissionMapper.selectById(2L)).thenReturn(record);
        when(harnessService.verify(any())).thenReturn(decision(AiHarnessDecisionDTO.PASS));
        when(postAbilityModelMapper.selectOne(any())).thenReturn(null);

        GovernanceAdmission result = service.retryDueAdmission(2L);

        assertThat(result.getApplyStatus()).isEqualTo("FUSED");
        assertThat(result.getBusinessTargetType()).isEqualTo("POST_ABILITY_MODEL");
        verify(postAbilityModelMapper).insert(argThat((com.example.matching.entity.post.PostAbilityModel m) ->
                m.getGovernanceAdmissionId() != null));
    }

    private GovernanceAdmissionRecord retryablePersonRecord() throws Exception {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setEmpId(1L);
        claim.setAbilityName("Java");
        claim.setAbilityTagId(7L);
        claim.setMasteryLevel(4);
        claim.setSourceType("RESUME_PARSE");
        claim.setSourceRefId(100L);
        claim.setConfidenceScore(new BigDecimal("80"));
        claim.setEvidenceText("Test evidence");
        claim.setSourceRefs(List.of("fact:EMP_ABILITY:100"));

        GovernanceAdmissionRecord record = new GovernanceAdmissionRecord();
        record.setId(1L);
        record.setScenario("PERSON_ABILITY");
        record.setApplyStatus("RETRYABLE");
        record.setRetryCount(0);
        record.setNextRetryTime(LocalDateTime.now().minusMinutes(1));
        record.setClaimPayloadJson(MAPPER.writeValueAsString(claim));
        return record;
    }

    private AiHarnessDecisionDTO decision(String decision) {
        AiHarnessDecisionDTO dto = new AiHarnessDecisionDTO();
        dto.setDecision(decision);
        dto.setCheckCode("HNS_RETRY_" + decision);
        dto.setSupportScore(new BigDecimal("85"));
        dto.setRiskLevel("LOW");
        dto.setReasons(List.of("Test reason"));
        return dto;
    }
}
