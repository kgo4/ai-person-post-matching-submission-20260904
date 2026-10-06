package com.example.matching.service.matching.impl;

import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeePostRecommendServiceImplTest {

    @Test
    void untaggedRequirementMatchesOnlyTheSameAbilityName() {
        MatchingRequirementSnapshot requirement = new MatchingRequirementSnapshot(
                null, "接口自动化测试", 3, BigDecimal.ONE, 1, 1, null);
        MatchingAbilitySnapshot sameName = new MatchingAbilitySnapshot(
                1L, null, "接口自动化测试", 3, BigDecimal.ONE, "MANUAL", BigDecimal.ONE, null);
        MatchingAbilitySnapshot otherName = new MatchingAbilitySnapshot(
                2L, null, "持续集成", 3, BigDecimal.ONE, "MANUAL", BigDecimal.ONE, null);

        assertThat(EmployeePostRecommendServiceImpl.matchesRequirement(requirement, sameName)).isTrue();
        assertThat(EmployeePostRecommendServiceImpl.matchesRequirement(requirement, otherName)).isFalse();
    }
}
