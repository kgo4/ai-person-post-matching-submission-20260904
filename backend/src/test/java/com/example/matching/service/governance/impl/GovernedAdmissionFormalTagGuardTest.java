package com.example.matching.service.governance.impl;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.dto.governance.GovernanceAdmission;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.mapper.governance.GovernanceAdmissionMapper;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task9：治理入口的正式 tagId 守卫。
 * <ul>
 *   <li>PASS + 正式 abilityTagId → 写入正式事实表（emp_ability / post_ability_model）</li>
 *   <li>PASS + 缺少 abilityTagId → 转入 REVIEW（MISSING_FORMAL_TAG_ID），绝不插入空 tagId</li>
 *   <li>REVIEW → 只写声明/候选，不写正式事实（三态行为由 HarnessTriStateTest 覆盖）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Governed Admission 正式标签守卫")
class GovernedAdmissionFormalTagGuardTest {

    @Mock private AiTrustHarnessService harnessService;
    @Mock private GovernanceAdmissionMapper admissionMapper;
    @Mock private GovernedAdmissionEntityBuilder builder;

    private GovernedAdmissionServiceImpl service() {
        return new GovernedAdmissionServiceImpl(harnessService, admissionMapper, builder, new ObjectMapper());
    }

    private AiHarnessDecisionDTO passDecision() {
        AiHarnessDecisionDTO decision = new AiHarnessDecisionDTO();
        decision.setDecision("PASS");
        return decision;
    }

    private PersonAbilityClaim personClaim(Long tagId) {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setEmpId(1L);
        claim.setAbilityName("Java");
        claim.setAbilityTagId(tagId);
        claim.setMasteryLevel(4);
        claim.setEvidenceText("负责Java后端开发");
        return claim;
    }

    private PostAbilityClaim postClaim(Long tagId) {
        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setPostId(7L);
        claim.setAbilityName("Java");
        claim.setAbilityTagId(tagId);
        claim.setRequiredLevel(3);
        claim.setWeight(new BigDecimal("0.5"));
        claim.setEvidenceText("负责Java模块设计");
        claim.setSourceRefs(java.util.List.of("fact:POST_ABILITY_MODEL:7"));
        return claim;
    }

    @Test
    @DisplayName("PASS 缺少正式 tagId：员工能力转入 REVIEW，不写 emp_ability")
    void personPassWithoutFormalTagIdFallsBackToReview() {
        when(harnessService.verify(any())).thenReturn(passDecision());
        when(builder.initAdmission(any(), any())).thenReturn(new GovernanceAdmission());
        when(builder.json(any())).thenReturn("{}");

        GovernanceAdmission result = service().admitPersonAbility(personClaim(null));

        assertThat(result.getFinalDecision()).isEqualTo("REVIEW");
        assertThat(result.getDecisionRule()).isEqualTo("MISSING_FORMAL_TAG_ID");
        assertThat(result.getApplyStatus()).isEqualTo("PENDING_HARNESS_REVIEW");
        verify(builder, never()).writeEmpAbility(any(), any());
    }

    @Test
    @DisplayName("PASS 带正式 tagId：员工能力写入 emp_ability")
    void personPassWithFormalTagIdWritesFact() {
        when(harnessService.verify(any())).thenReturn(passDecision());
        when(builder.initAdmission(any(), any())).thenReturn(new GovernanceAdmission());
        when(builder.findOrCreatePersonClaimEntity(any(), any(), any())).thenReturn(
                new com.example.matching.entity.ability.PersonAbilityClaim());
        when(builder.writeEmpAbility(any(), any())).thenReturn(101L);

        GovernanceAdmission result = service().admitPersonAbility(personClaim(5L));

        assertThat(result.getApplyStatus()).isEqualTo("FUSED");
        verify(builder).writeEmpAbility(any(), any());
    }

    @Test
    @DisplayName("PASS 缺少正式 tagId：岗位能力转入 REVIEW，不写 post_ability_model")
    void postPassWithoutFormalTagIdFallsBackToReview() {
        when(harnessService.verify(any())).thenReturn(passDecision());
        when(builder.initAdmission(any(), any())).thenReturn(new GovernanceAdmission());
        when(builder.json(any())).thenReturn("{}");

        GovernanceAdmission result = service().admitPostAbility(postClaim(null));

        assertThat(result.getFinalDecision()).isEqualTo("REVIEW");
        assertThat(result.getDecisionRule()).isEqualTo("MISSING_FORMAL_TAG_ID");
        assertThat(result.getApplyStatus()).isEqualTo("PENDING_HARNESS_REVIEW");
        verify(builder, never()).upsertPostAbilityModel(any(), any());
    }

    @Test
    @DisplayName("PASS 带正式 tagId：岗位能力写入 post_ability_model")
    void postPassWithFormalTagIdWritesFact() {
        when(harnessService.verify(any())).thenReturn(passDecision());
        when(builder.initAdmission(any(), any())).thenReturn(new GovernanceAdmission());
        when(builder.upsertPostAbilityModel(any(), any())).thenReturn(202L);

        GovernanceAdmission result = service().admitPostAbility(postClaim(5L));

        assertThat(result.getApplyStatus()).isEqualTo("FUSED");
        verify(builder).upsertPostAbilityModel(any(), any());
    }
}
