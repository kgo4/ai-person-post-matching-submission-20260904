package com.example.matching.service.governance.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AiGovernanceEnumsTest {

    @Test
    void scenariosExposeNonBlankChineseLabelsForEverySupportedScenario() {
        assertThat(Arrays.stream(AiGovernanceScenario.values()))
                .allSatisfy(scenario -> assertThat(scenario.getLabel()).isNotBlank());
        assertThat(AiGovernanceScenario.POST_EVOLUTION.getLabel()).isEqualTo("岗位演化");
        assertThat(AiGovernanceScenario.LEARNING_PATH_SUGGESTION.getLabel()).isEqualTo("学习路径建议");
    }

    @Test
    void claimTypesExposeNonBlankChineseLabelsForEverySupportedClaim() {
        assertThat(Arrays.stream(AiGovernanceClaimType.values()))
                .allSatisfy(type -> assertThat(type.getLabel()).isNotBlank());
        assertThat(AiGovernanceClaimType.EMP_ABILITY.getLabel()).isEqualTo("人员能力");
        assertThat(AiGovernanceClaimType.POST_ABILITY.getLabel()).isEqualTo("岗位能力");
    }

    @Test
    void reviewStatusesExposeNonBlankChineseLabelsForEveryLifecycleState() {
        assertThat(Arrays.stream(AiGovernanceReviewStatus.values()))
                .allSatisfy(status -> assertThat(status.getLabel()).isNotBlank());
        assertThat(AiGovernanceReviewStatus.PENDING.getLabel()).isEqualTo("待处理");
        assertThat(AiGovernanceReviewStatus.AUTO_PASSED.getLabel()).isEqualTo("自动通过");
    }
}
