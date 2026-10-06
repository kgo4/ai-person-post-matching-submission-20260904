package com.example.matching.service.rag;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RagSourceCatalogTest {

    @Test
    void mapsStructuredBusinessSourcesToFactLayer() {
        assertThat(RagSourceCatalog.resolveLayer("POST_ABILITY_MODEL", Map.of()))
                .isEqualTo(RagKnowledgeLayer.FACT);
        assertThat(RagSourceCatalog.resolveLayer("EMP_ABILITY", Map.of()))
                .isEqualTo(RagKnowledgeLayer.FACT);
        assertThat(RagSourceCatalog.resolveLayer("MARKET_JD", Map.of()))
                .isEqualTo(RagKnowledgeLayer.FACT);
        assertThat(RagSourceCatalog.resolveLayer("RECRUITMENT_JD", Map.of()))
                .isEqualTo(RagKnowledgeLayer.FACT);
    }

    @Test
    void mapsEvidenceAndExternalSourcesToTheirOwnLayers() {
        assertThat(RagSourceCatalog.resolveLayer("CONTEST_EVIDENCE", Map.of()))
                .isEqualTo(RagKnowledgeLayer.EVIDENCE);
        assertThat(RagSourceCatalog.resolveLayer("OFFICIAL_POLICY", Map.of()))
                .isEqualTo(RagKnowledgeLayer.TREND);
        assertThat(RagSourceCatalog.resolveLayer("INDUSTRY_REPORT", Map.of()))
                .isEqualTo(RagKnowledgeLayer.TREND);
    }

    @Test
    void cloudHitRequiresExplicitOriginLayer() {
        assertThat(RagSourceCatalog.resolveLayer("VOLCENGINE_KB", Map.of())).isNull();
        assertThat(RagSourceCatalog.resolveLayer("VOLCENGINE_KB", Map.of("originSourceType", "OFFICIAL_POLICY")))
                .isEqualTo(RagKnowledgeLayer.TREND);
        assertThat(RagSourceCatalog.resolveLayer("VOLCENGINE_KB", Map.of("knowledgeLayer", "EVIDENCE")))
                .isEqualTo(RagKnowledgeLayer.EVIDENCE);
    }

    @Test
    void filtersSourceTypesToAllowedLayersWithoutAddingUnknownSources() {
        assertThat(RagSourceCatalog.sourceTypesForLayers(Set.of(RagKnowledgeLayer.EVIDENCE)))
                .contains("CONTEST_EVIDENCE")
                .doesNotContain("POST_ABILITY_MODEL", "OFFICIAL_POLICY");
    }
}
