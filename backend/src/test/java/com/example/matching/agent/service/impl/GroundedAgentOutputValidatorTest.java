package com.example.matching.agent.service.impl;

import com.example.matching.agent.dto.AgentContextPackage;
import com.example.matching.agent.dto.LearningPathAgentResult;
import com.example.matching.agent.dto.MatchingAnalysisAgentResult;
import com.example.matching.application.agent.AgentScoreBreakdown;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GroundedAgentOutputValidatorTest {

    private final GroundedAgentOutputValidator validator = new GroundedAgentOutputValidator(
            org.mockito.Mockito.mock(com.example.matching.ai.context.service.AiContextSourceRefService.class));

    @Test
    void rejectsDimensionScoresOutsideTheDeterministicBreakdown() {
        MatchingAnalysisAgentResult result = resultWithDimension("invented", 90);
        assertThat(validator.validateMatching(result, contextWithDimension("technical"))).isEmpty();
    }

    @Test
    void acceptsDimensionScoresInsideTheDeterministicBreakdown() {
        MatchingAnalysisAgentResult result = resultWithDimension("technical", 85);
        AgentContextPackage context = contextWithDimension("technical");
        // 服务端引用非空：直接采用服务端引用，不触发 LLM 引用校验
        context.setSourceRefs(List.of(serverRef()));
        assertThat(validator.validateMatching(result, context)).isPresent();
    }

    @Test
    void rejectsDimensionScoresWhenNoDeterministicBreakdownExists() {
        MatchingAnalysisAgentResult result = resultWithDimension("invented", 85);

        assertThat(validator.validateMatching(result, new AgentContextPackage())).isEmpty();
    }

    @Test
    void rejectsNullResult() {
        assertThat(validator.validateMatching(null, new AgentContextPackage())).isEmpty();
    }

    @Test
    void rejectsScoreOutsideRange() {
        MatchingAnalysisAgentResult result = new MatchingAnalysisAgentResult();
        result.setSuggestedLlmScore(new BigDecimal("150"));
        result.setConclusion("test");
        assertThat(validator.validateMatching(result, new AgentContextPackage())).isEmpty();
    }

    @Test
    void rejectsBlankConclusion() {
        MatchingAnalysisAgentResult result = new MatchingAnalysisAgentResult();
        result.setSuggestedLlmScore(new BigDecimal("80"));
        assertThat(validator.validateMatching(result, new AgentContextPackage())).isEmpty();
    }

    @Test
    void filtersARecommendationForAnAbilityOutsideVerifiedGaps() {
        LearningPathAgentResult result = new LearningPathAgentResult();
        LearningPathAgentResult.LearningStepSuggestion step = new LearningPathAgentResult.LearningStepSuggestion();
        step.setAbilityTagId(999L);
        step.setTitle("Invented ability");
        result.setSteps(List.of(step));
        assertThat(validator.validateLearningPath(result, new AgentContextPackage(), Set.of(7L))).isEmpty();
    }

    @Test
    void acceptsRecommendationWithinVerifiedGaps() {
        LearningPathAgentResult result = new LearningPathAgentResult();
        LearningPathAgentResult.LearningStepSuggestion step = new LearningPathAgentResult.LearningStepSuggestion();
        step.setAbilityTagId(7L);
        step.setTitle("Valid ability");
        result.setSteps(List.of(step));
        assertThat(validator.validateLearningPath(result, new AgentContextPackage(), Set.of(7L))).isPresent();
    }

    @Test
    void rejectsEmptyLearningStepsSoTheCallerCanUseDeterministicFallback() {
        LearningPathAgentResult result = new LearningPathAgentResult();
        result.setSteps(List.of());

        assertThat(validator.validateLearningPath(result, new AgentContextPackage(), Set.of(7L))).isEmpty();
    }

    @Test
    void rejectsRecommendationWithoutAbilityTagId() {
        LearningPathAgentResult result = new LearningPathAgentResult();
        LearningPathAgentResult.LearningStepSuggestion step = new LearningPathAgentResult.LearningStepSuggestion();
        step.setTitle("Missing tag");
        result.setSteps(List.of(step));

        assertThat(validator.validateLearningPath(result, new AgentContextPackage(), Set.of(7L))).isEmpty();
    }

    @Test
    void serverRefsOverrideLlmReportedRefs() {
        // M24：服务端引用非空时覆盖 LLM 引用
        MatchingAnalysisAgentResult result = resultWithDimension("technical", 85);
        com.example.matching.agent.dto.AgentSourceRef llmRef = new com.example.matching.agent.dto.AgentSourceRef();
        llmRef.setRef("fact:INVENTED:1");
        result.setSourceRefs(List.of(llmRef));
        AgentContextPackage context = contextWithDimension("technical");
        context.setSourceRefs(List.of(serverRef()));

        assertThat(validator.validateMatching(result, context))
                .isPresent()
                .get()
                .extracting(MatchingAnalysisAgentResult::getSourceRefs)
                .isEqualTo(List.of(serverRef()));
    }

    @Test
    void llmRefsValidatedWhenServerRefsEmpty() {
        // M24：服务端引用为空时逐条校验 LLM 自报引用
        com.example.matching.ai.context.service.AiContextSourceRefService sourceRefService =
                org.mockito.Mockito.mock(com.example.matching.ai.context.service.AiContextSourceRefService.class);
        com.example.matching.ai.context.dto.AiContextSourceRefDTO resolved =
                new com.example.matching.ai.context.dto.AiContextSourceRefDTO();
        org.mockito.Mockito.when(sourceRefService.resolve("fact:INTERVIEW_SESSION:1"))
                .thenReturn(resolved);
        org.mockito.Mockito.when(sourceRefService.resolve("fact:INVENTED:1")).thenReturn(null);
        GroundedAgentOutputValidator strictValidator = new GroundedAgentOutputValidator(sourceRefService);

        MatchingAnalysisAgentResult result = resultWithDimension("technical", 85);
        com.example.matching.agent.dto.AgentSourceRef valid = new com.example.matching.agent.dto.AgentSourceRef();
        valid.setRef("fact:INTERVIEW_SESSION:1");
        com.example.matching.agent.dto.AgentSourceRef invalid = new com.example.matching.agent.dto.AgentSourceRef();
        invalid.setRef("fact:INVENTED:1");
        result.setSourceRefs(List.of(valid, invalid));
        AgentContextPackage context = contextWithDimension("technical");

        var validated = strictValidator.validateMatching(result, context);

        // 有效引用保留、虚构引用剔除
        assertThat(validated).isPresent();
        assertThat(validated.get().getSourceRefs()).hasSize(1);
        assertThat(validated.get().getSourceRefs().get(0).getRef()).isEqualTo("fact:INTERVIEW_SESSION:1");
    }

    @Test
    void llmResultRejectedWhenNoValidRefs() {
        // M24：无任何有效引用时拒绝结果，不能采纳自报置信度
        MatchingAnalysisAgentResult result = resultWithDimension("technical", 85);
        com.example.matching.agent.dto.AgentSourceRef invalid = new com.example.matching.agent.dto.AgentSourceRef();
        invalid.setRef("fact:INVENTED:1");
        result.setSourceRefs(List.of(invalid));

        assertThat(validator.validateMatching(result, contextWithDimension("technical"))).isEmpty();
    }

    @Test
    void filtersFindingsWithUnknownAbilityTag() {
        // 模型返回子图外能力标签 → 该 finding 被过滤；无合法 finding 时拒绝
        MatchingAnalysisAgentResult result = resultWithDimension("technical", 85);
        com.example.matching.agent.dto.AgentFinding good = new com.example.matching.agent.dto.AgentFinding();
        good.setType("STRENGTH");
        good.setAbilityTagId(11L);
        good.setText("擅长 Java");
        good.setSourceRefs(List.of("fact:EMP_ABILITY:1"));
        good.setGraphNodeKeys(List.of("ABILITY:11"));
        com.example.matching.agent.dto.AgentFinding bad = new com.example.matching.agent.dto.AgentFinding();
        bad.setType("GAP");
        bad.setAbilityTagId(99L);
        bad.setText("虚构能力缺口");
        bad.setSourceRefs(List.of("fact:EMP_ABILITY:1"));
        result.setFindings(List.of(good, bad));

        AgentContextPackage context = contextWithDimension("technical");
        context.setSourceRefs(List.of(serverRef()));
        context.setGraphContext(graphContext());

        var validated = validator.validateMatching(result, context);

        assertThat(validated).isPresent();
        assertThat(validated.get().getFindings()).hasSize(1);
        assertThat(validated.get().getFindings().get(0).getAbilityTagId()).isEqualTo(11L);
        // 报告由 findings 服务端转换生成
        assertThat(validated.get().getStrengths()).containsExactly("擅长 Java");
        assertThat(validated.get().getGaps()).isEmpty();
    }

    @Test
    void rejectsFindingsReferencingSourceRefsOutsideSubGraph() {
        MatchingAnalysisAgentResult result = resultWithDimension("technical", 85);
        com.example.matching.agent.dto.AgentFinding bad = new com.example.matching.agent.dto.AgentFinding();
        bad.setType("STRENGTH");
        bad.setAbilityTagId(11L);
        bad.setText("引用子图外证据");
        bad.setSourceRefs(List.of("fact:EVIDENCE:999"));
        result.setFindings(List.of(bad));

        AgentContextPackage context = contextWithDimension("technical");
        context.setSourceRefs(List.of(serverRef()));
        context.setGraphContext(graphContext());

        assertThat(validator.validateMatching(result, context)).isEmpty();
    }

    @Test
    void graphContextUnusableRejectsUnverifiedFindings() {
        // STALE/UNAVAILABLE 时不得使用图谱事实：跳过 findings 校验，保留字符串行为
        MatchingAnalysisAgentResult result = resultWithDimension("technical", 85);
        com.example.matching.agent.dto.AgentFinding bad = new com.example.matching.agent.dto.AgentFinding();
        bad.setType("STRENGTH");
        bad.setAbilityTagId(99L);
        bad.setText("未知能力");
        bad.setSourceRefs(List.of("fact:EVIDENCE:999"));
        result.setFindings(List.of(bad));

        AgentContextPackage context = contextWithDimension("technical");
        context.setSourceRefs(List.of(serverRef()));
        com.example.matching.agent.dto.graph.AgentGraphContext stale =
                new com.example.matching.agent.dto.graph.AgentGraphContext();
        stale.setStatus("STALE");
        context.setGraphContext(stale);

        assertThat(validator.validateMatching(result, context)).isEmpty();
    }

    @Test
    void clearsModelStringFindingsWhenNoStructuredFindingsExist() {
        MatchingAnalysisAgentResult result = resultWithDimension("technical", 85);
        result.setStrengths(List.of("model supplied unsupported strength"));
        result.setGaps(List.of("model supplied unsupported gap"));
        AgentContextPackage context = contextWithDimension("technical");
        context.setSourceRefs(List.of(serverRef()));
        context.setGraphContext(graphContext());

        assertThat(validator.validateMatching(result, context)).isPresent();
        assertThat(result.getStrengths()).isEmpty();
        assertThat(result.getGaps()).isEmpty();
    }

    private com.example.matching.agent.dto.graph.AgentGraphContext graphContext() {
        com.example.matching.agent.dto.graph.AgentGraphContext ctx =
                new com.example.matching.agent.dto.graph.AgentGraphContext();
        ctx.setStatus("FRESH");
        ctx.getAllowedAbilityTagIds().add(11L);
        ctx.getAllowedSourceRefs().add("fact:EMP_ABILITY:1");
        ctx.getNodes().add(com.example.matching.agent.dto.graph.AgentGraphNode.of(
                "ABILITY:11", "ABILITY", 11L, "Java"));
        return ctx;
    }

    private com.example.matching.agent.dto.AgentSourceRef serverRef() {
        com.example.matching.agent.dto.AgentSourceRef ref = new com.example.matching.agent.dto.AgentSourceRef();
        ref.setRef("fact:INTERVIEW_SESSION:1");
        ref.setRefType("FACT");
        return ref;
    }

    private MatchingAnalysisAgentResult resultWithDimension(String dimension, int score) {
        MatchingAnalysisAgentResult result = new MatchingAnalysisAgentResult();
        result.setSuggestedLlmScore(new BigDecimal("80"));
        result.setConclusion("test conclusion");
        result.setDimensionScores(List.of(Map.of("dimension", dimension, "score", score)));
        return result;
    }

    private AgentContextPackage contextWithDimension(String dimension) {
        AgentContextPackage context = new AgentContextPackage();
        context.setScoreBreakdown(List.of(new AgentScoreBreakdown(dimension, BigDecimal.valueOf(80), BigDecimal.ONE, "desc")));
        return context;
    }
}
