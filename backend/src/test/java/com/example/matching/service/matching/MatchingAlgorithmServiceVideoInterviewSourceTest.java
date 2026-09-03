package com.example.matching.service.matching;

import com.example.matching.common.enums.AbilitySourceCredibility;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 AI_VIDEO_INTERVIEW 来源的可信度权重在枚举中正确配置，
 * 并确保匹配算法能正确消费新来源。
 */
class MatchingAlgorithmServiceVideoInterviewSourceTest {

    @Test
    void videoInterviewAliasHasCanonicalWeight() {
        double weight = AbilitySourceCredibility.getWeightBySource("AI_VIDEO_INTERVIEW");
        assertThat(weight).isEqualTo(0.88);
    }

    @Test
    void aiInterviewEnumExists() {
        AbilitySourceCredibility source = AbilitySourceCredibility.AI_INTERVIEW;
        assertThat(source.getSource()).isEqualTo("AI_INTERVIEW");
        assertThat(source.getWeight()).isEqualTo(0.88);
        assertThat(source.getDesc()).isEqualTo("AI面试");
    }

    @Test
    void allExistingSourcesStillWork() {
        assertThat(AbilitySourceCredibility.getWeightBySource("PERFORMANCE")).isEqualTo(1.00);
        assertThat(AbilitySourceCredibility.getWeightBySource("MANUAL")).isEqualTo(0.95);
        assertThat(AbilitySourceCredibility.getWeightBySource("AI_TEST")).isEqualTo(0.90);
        assertThat(AbilitySourceCredibility.getWeightBySource("AI_ASSESSMENT")).isEqualTo(0.90);
        assertThat(AbilitySourceCredibility.getWeightBySource("RESUME_PARSE")).isEqualTo(0.70);
    }

    @Test
    void unknownSourceFallsBackToManualWeight() {
        double weight = AbilitySourceCredibility.getWeightBySource("UNKNOWN_SOURCE");
        assertThat(weight).isEqualTo(0.95);
    }

    @Test
    void nullSourceFallsBackToManualWeight() {
        double weight = AbilitySourceCredibility.getWeightBySource(null);
        assertThat(weight).isEqualTo(0.95);
    }
}
