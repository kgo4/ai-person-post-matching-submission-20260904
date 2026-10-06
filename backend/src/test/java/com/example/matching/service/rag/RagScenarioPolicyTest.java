package com.example.matching.service.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagScenarioPolicyTest {

    @Test
    void postEvolutionOnlyReadsTrendAndDomain() {
        assertThat(RagScenarioPolicy.allowedLayers(RagScenarioEnum.POST_EVOLUTION))
                .containsExactlyInAnyOrder(RagKnowledgeLayer.FACT,
                        RagKnowledgeLayer.TREND, RagKnowledgeLayer.DOMAIN);
    }

    @Test
    void interviewFollowupOnlyReadsEvidence() {
        assertThat(RagScenarioPolicy.allowedLayers(RagScenarioEnum.INTERVIEW_FOLLOWUP))
                .containsExactly(RagKnowledgeLayer.EVIDENCE);
    }

    @Test
    void formalMatchingRankingDoesNotHaveARagPolicy() {
        assertThat(RagScenarioPolicy.usesRagForFormalRanking()).isFalse();
    }

    @Test
    void reportAndGapDiagnosisCanReadBusinessFactsForGroundedOutput() {
        assertThat(RagScenarioPolicy.allowedLayers(RagScenarioEnum.REPORT_GENERATION))
                .containsExactlyInAnyOrder(RagKnowledgeLayer.FACT,
                        RagKnowledgeLayer.EVIDENCE, RagKnowledgeLayer.DOMAIN);
        assertThat(RagScenarioPolicy.allowedLayers(RagScenarioEnum.MATCH_GAP_DIAGNOSIS))
                .containsExactlyInAnyOrder(RagKnowledgeLayer.FACT,
                        RagKnowledgeLayer.EVIDENCE, RagKnowledgeLayer.DOMAIN);
    }

    @Test
    void legacySourceCheckUsesTheLayerPolicy() {
        assertThat(RagScenarioEnum.POST_EVOLUTION.isSourceTypeAllowed("MARKET_JD")).isTrue();
        assertThat(RagScenarioEnum.POST_EVOLUTION.isSourceTypeAllowed("CONTEST_EVIDENCE")).isFalse();
    }
}
