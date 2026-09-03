package com.example.matching.ai.context.service.impl;

import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiContextPackageServiceImplTest {

    @Test
    void keepsEachUntaggedFormalAbilityInAiContext() {
        List<MatchingAbilitySnapshot> snapshots = List.of(
                new MatchingAbilitySnapshot(1L, null, "接口自动化测试", 3,
                        BigDecimal.ONE, "EMP_ABILITY", BigDecimal.ONE, null),
                new MatchingAbilitySnapshot(2L, null, "持续集成测试配置", 4,
                        BigDecimal.ONE, "EMP_ABILITY", BigDecimal.ONE, null));

        assertThat(AiContextPackageServiceImpl.selectContextAbilities(snapshots))
                .extracting(MatchingAbilitySnapshot::abilityName)
                .containsExactlyInAnyOrder("接口自动化测试", "持续集成测试配置");
    }
}
