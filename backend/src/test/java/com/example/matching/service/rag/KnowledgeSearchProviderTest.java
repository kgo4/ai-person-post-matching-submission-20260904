package com.example.matching.service.rag;

import com.example.matching.entity.rag.RagKnowledgeChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeSearchProviderTest {

    @Test
    void fromMysqlChunkKeepsSourceMetadataAndStableChunkId() {
        RagKnowledgeChunk chunk = new RagKnowledgeChunk();
        chunk.setId(42L);
        chunk.setDocumentId(7L);
        chunk.setChunkText("Java工程师需要Spring Boot、微服务和云原生实践能力。");

        KnowledgeSearchHit hit = KnowledgeSearchHit.fromMysqlChunk(
                chunk,
                0.88f,
                "JD_IMPORT",
                "Java后端工程师JD"
        );

        assertThat(hit.chunkId()).isEqualTo("mysql:42");
        assertThat(hit.documentId()).isEqualTo("mysql-doc:7");
        assertThat(hit.sourceType()).isEqualTo("JD_IMPORT");
        assertThat(hit.title()).isEqualTo("Java后端工程师JD");
        assertThat(hit.content()).contains("Spring Boot");
        assertThat(hit.score()).isEqualTo(0.88f);
        assertThat(hit.metadata()).containsEntry("backend", "mysql");
    }

    @Test
    void providerSearchAcceptsScenarioAndSourceTypeFilters() {
        KnowledgeSearchProvider provider = (request) -> {
            assertThat(request.scenario()).isEqualTo("JD_ABILITY_EXTRACT");
            assertThat(request.sourceTypes()).containsExactly("JD_IMPORT");
            return List.of(new KnowledgeSearchHit(
                    "cloud:1",
                    "cloud-doc:1",
                    "VOLCENGINE_KB",
                    "Java能力证据",
                    "Java能力证据片段",
                    0.91f,
                    Map.of("backend", "volcengine")
            ));
        };

        List<KnowledgeSearchHit> hits = provider.search(new KnowledgeSearchRequest(
                "Java工程师",
                "JD_ABILITY_EXTRACT",
                3,
                List.of("JD_IMPORT")
        ));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).metadata()).containsEntry("backend", "volcengine");
    }
}
