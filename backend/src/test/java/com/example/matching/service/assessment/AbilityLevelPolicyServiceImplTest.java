package com.example.matching.service.assessment;

import com.example.matching.entity.workflow.AbilityLevelPolicy;
import com.example.matching.mapper.workflow.AbilityLevelPolicyMapper;
import com.example.matching.service.assessment.impl.AbilityLevelPolicyServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbilityLevelPolicyServiceImplTest {

    @Test
    void activePolicyFallsBackToBuiltInDefaultsWhenNoPolicyIsEnabled() {
        AbilityLevelPolicyMapper mapper = mock(AbilityLevelPolicyMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        AbilityLevelPolicyService service = new AbilityLevelPolicyServiceImpl(mapper, new ObjectMapper());

        AbilityLevelPolicyService.LevelPolicy policy = service.getActivePolicy();

        assertThat(policy.getPolicyVersion()).isEqualTo(AbilityLevelPolicyServiceImpl.DEFAULT_POLICY_VERSION);
        assertThat(policy.getConflictThreshold()).isEqualTo(2);
        assertThat(policy.getSingleSourceLevelCeiling())
                .containsEntry("RESUME_PARSE", 2)
                .containsEntry("AI_TEST", 3)
                .containsEntry("AI_INTERVIEW", 3);
    }

    @Test
    void parsesEnabledPolicyAndUsesConfiguredThresholds() {
        AbilityLevelPolicyMapper mapper = mock(AbilityLevelPolicyMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(policy("custom-v2", "自定义策略", """
                {"conflictThreshold":3,"level4MinIndependentSources":4,
                 "highCredibilityThreshold":0.85,"autoConfirmWeightThreshold":0.72,
                 "reviewWeightThreshold":0.41,
                 "singleSourceLevelCeiling":{"RESUME_PARSE":1,"AI_TEST":4}}
                """)));
        AbilityLevelPolicyService service = new AbilityLevelPolicyServiceImpl(mapper, new ObjectMapper());

        AbilityLevelPolicyService.LevelPolicy parsed = service.getActivePolicy();

        assertThat(parsed.getPolicyVersion()).isEqualTo("custom-v2");
        assertThat(parsed.getPolicyName()).isEqualTo("自定义策略");
        assertThat(parsed.getConflictThreshold()).isEqualTo(3);
        assertThat(parsed.getLevel4MinIndependentSources()).isEqualTo(4);
        assertThat(parsed.getHighCredibilityThreshold()).isEqualTo(0.85d);
        assertThat(parsed.getAutoConfirmWeightThreshold()).isEqualByComparingTo(new BigDecimal("0.72"));
        assertThat(parsed.getReviewWeightThreshold()).isEqualByComparingTo(new BigDecimal("0.41"));
        assertThat(parsed.getSingleSourceLevelCeiling()).containsEntry("RESUME_PARSE", 1).containsEntry("AI_TEST", 4);
    }

    @Test
    void blankOrUnknownVersionUsesCurrentOrDefaultPolicy() {
        AbilityLevelPolicyMapper mapper = mock(AbilityLevelPolicyMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        when(mapper.selectOne(any())).thenReturn(null);
        AbilityLevelPolicyService service = new AbilityLevelPolicyServiceImpl(mapper, new ObjectMapper());

        assertThat(service.getPolicy(" ").getPolicyVersion())
                .isEqualTo(AbilityLevelPolicyServiceImpl.DEFAULT_POLICY_VERSION);
        assertThat(service.getPolicy("missing-v9").getPolicyVersion())
                .isEqualTo(AbilityLevelPolicyServiceImpl.DEFAULT_POLICY_VERSION);
    }

    @Test
    void invalidJsonFallsBackWithoutExposingBrokenPolicy() {
        AbilityLevelPolicyMapper mapper = mock(AbilityLevelPolicyMapper.class);
        when(mapper.selectOne(any())).thenReturn(policy("broken-v1", "损坏策略", "not-json"));
        AbilityLevelPolicyService service = new AbilityLevelPolicyServiceImpl(mapper, new ObjectMapper());

        assertThat(service.getPolicy("broken-v1").getPolicyVersion())
                .isEqualTo(AbilityLevelPolicyServiceImpl.DEFAULT_POLICY_VERSION);
    }

    private AbilityLevelPolicy policy(String version, String name, String configJson) {
        AbilityLevelPolicy policy = new AbilityLevelPolicy();
        policy.setPolicyVersion(version);
        policy.setPolicyName(name);
        policy.setConfigJson(configJson);
        policy.setEnabled(1);
        return policy;
    }
}
