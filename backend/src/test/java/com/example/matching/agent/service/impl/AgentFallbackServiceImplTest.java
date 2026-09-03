package com.example.matching.agent.service.impl;

import com.example.matching.agent.dto.AgentContextPackage;
import com.example.matching.agent.dto.AgentSourceRef;
import com.example.matching.agent.dto.MatchingAnalysisAgentResult;
import com.example.matching.application.agent.AgentScoreBreakdown;
import com.example.matching.application.agent.EmployeeAbilitySnapshot;
import com.example.matching.application.agent.PostRequirementSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentFallbackServiceImplTest {

    @Test
    void fallbackMatchingAnalysisBuildsTraceableReportFromServerFacts() {
        AgentContextPackage context = new AgentContextPackage();
        context.setMatchScore(new BigDecimal("62.5"));
        context.setEmployeeAbilities(List.of(
                new EmployeeAbilitySnapshot(10L, "Java", 4, "AI_ASSESSMENT", new BigDecimal("90"), 2),
                new EmployeeAbilitySnapshot(null, "接口自动化", 2, "AI_ASSESSMENT", new BigDecimal("80"), 1)));
        context.setPostRequirements(List.of(
                new PostRequirementSnapshot(10L, "Java", 3, new BigDecimal("0.6"), true, true),
                new PostRequirementSnapshot(null, "接口自动化", 3, new BigDecimal("0.4"), true, false)));
        context.setScoreBreakdown(List.of(
                new AgentScoreBreakdown("能力匹配", new BigDecimal("62.5"), new BigDecimal("0.7"), "岗位能力等级匹配结果")));
        AgentSourceRef ref = new AgentSourceRef();
        ref.setRef("fact:EMP_ABILITY:101");
        context.setSourceRefs(List.of(ref));

        MatchingAnalysisAgentResult result = new AgentFallbackServiceImpl(mock(AgentRunConfidencePolicy.class))
                .fallbackMatchingAnalysis(context);

        assertThat(result.getStrengths()).contains("Java达到要求等级 L3");
        assertThat(result.getGaps()).contains("接口自动化差距 1 级 (L2 -> L3)");
        assertThat(result.getSuggestions()).isNotEmpty();
        assertThat(result.getDimensionScores()).singleElement().satisfies(score -> {
            assertThat(score).containsEntry("dimension", "能力匹配");
            assertThat(score).containsEntry("score", new BigDecimal("62.5"));
            assertThat(score).containsEntry("weight", new BigDecimal("0.7"));
        });
        assertThat(result.getScoreReasons()).singleElement().satisfies(reason ->
                assertThat(reason.get("factRefs")).isEqualTo(List.of("fact:EMP_ABILITY:101")));
        assertThat(result.getEvidenceAnalysis()).hasSize(2);
        assertThat(result.getEvidenceAnalysis().get(1))
                .containsEntry("ability", "接口自动化")
                .containsEntry("fusedLevel", "L2");
    }
}
