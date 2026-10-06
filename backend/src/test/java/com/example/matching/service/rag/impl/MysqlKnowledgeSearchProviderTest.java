package com.example.matching.service.rag.impl;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.rag.RagKnowledgeChunkMapper;
import com.example.matching.mapper.rag.RagKnowledgeDocumentMapper;
import com.example.matching.service.rag.KnowledgeSearchHit;
import com.example.matching.service.rag.KnowledgeSearchRequest;
import com.example.matching.service.rag.RagVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MysqlKnowledgeSearchProvider RRF 融合")
class MysqlKnowledgeSearchProviderTest {

    @Mock private VectorEmbeddingService vectorEmbeddingService;
    @Mock private RagVectorStore ragVectorStore;
    @Mock private RagKnowledgeDocumentMapper documentMapper;
    @Mock private RagKnowledgeChunkMapper chunkMapper;

    private MysqlKnowledgeSearchProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MysqlKnowledgeSearchProvider(
                vectorEmbeddingService, ragVectorStore, documentMapper, chunkMapper,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        lenient().when(vectorEmbeddingService.embed(any())).thenReturn(List.of(0.1f, 0.2f, 0.3f));
    }

    private RagKnowledgeChunk chunk(Long id, Long docId, String text) {
        RagKnowledgeChunk chunk = new RagKnowledgeChunk();
        chunk.setId(id);
        chunk.setDocumentId(docId);
        chunk.setChunkText(text);
        chunk.setChunkStatus("ACTIVE");
        return chunk;
    }

    private RagKnowledgeDocument doc(Long id) {
        RagKnowledgeDocument doc = new RagKnowledgeDocument();
        doc.setId(id);
        doc.setSourceType("JD_IMPORT");
        doc.setTitle("文档" + id);
        return doc;
    }

    @Test
    @DisplayName("向量分与关键词分不直接相加；融合分归一化到 [0,1]")
    void fusedScoreIsNormalizedToUnitInterval() {
        RagKnowledgeChunk vectorOnly = chunk(1L, 1L, "Java并发编程。");
        RagKnowledgeChunk keywordHit = chunk(2L, 2L, "Java开发经验丰富。");
        when(ragVectorStore.search(anyList(), anyInt(), nullable(List.class)))
                .thenReturn(List.of(new RagVectorStore.ScoredChunk(vectorOnly, 0.85f)));
        when(chunkMapper.findActiveByKeywordBigrams(anyList(), anyInt())).thenReturn(List.of(keywordHit));
        when(chunkMapper.selectBatchIds(any())).thenReturn(List.of(vectorOnly, keywordHit));
        when(documentMapper.selectBatchIds(any())).thenReturn(List.of(doc(1L), doc(2L)));

        List<KnowledgeSearchHit> hits = provider.search(
                new KnowledgeSearchRequest("Java开发", "JD_ABILITY_EXTRACT", 5, null));

        assertThat(hits).isNotEmpty();
        for (KnowledgeSearchHit hit : hits) {
            assertThat(hit.score())
                    .as("RRF 融合分必须在 [0,1]，不得直接 0.7*0.85 + 0.3*100")
                    .isBetween(0f, 1f);
        }
        assertThat(hits.get(0).metadata()).containsEntry("rerankApplied", false);
    }

    @Test
    @DisplayName("关键词检索委托给单次预编译 Mapper 查询，非通用 selectList")
    void keywordSearchUsesSingleBoundMapperQuery() {
        RagKnowledgeChunk chunk = chunk(1L, 1L, "Java开发三年经验，Spring Boot 微服务。");
        when(ragVectorStore.search(anyList(), anyInt(), nullable(List.class))).thenReturn(List.of());
        when(chunkMapper.findActiveByKeywordBigrams(anyList(), anyInt())).thenReturn(List.of(chunk));
        when(chunkMapper.selectBatchIds(any())).thenReturn(List.of(chunk));
        when(documentMapper.selectBatchIds(any())).thenReturn(List.of(doc(1L)));

        provider.search(new KnowledgeSearchRequest("Java开发微服务", "JD_ABILITY_EXTRACT", 5, null));

        verify(chunkMapper, times(1))
                .findActiveByKeywordBigrams(anyList(), eq(10));
        verify(chunkMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("相同 chunk 同时在两路命中 -> 排名融合贡献叠加后仍归一化")
    void chunkInBothRanksGetsFusedScore() {
        RagKnowledgeChunk chunk = chunk(1L, 1L, "Java开发三年经验。");
        when(ragVectorStore.search(anyList(), anyInt(), nullable(List.class)))
                .thenReturn(List.of(new RagVectorStore.ScoredChunk(chunk, 0.9f)));
        when(chunkMapper.findActiveByKeywordBigrams(anyList(), anyInt())).thenReturn(List.of(chunk));
        when(chunkMapper.selectBatchIds(any())).thenReturn(List.of(chunk));
        when(documentMapper.selectBatchIds(any())).thenReturn(List.of(doc(1L)));

        List<KnowledgeSearchHit> hits = provider.search(
                new KnowledgeSearchRequest("Java开发", "JD_ABILITY_EXTRACT", 5, null));

        assertThat(hits).hasSize(1);
        // 两路命中第 1 名: (1/61 + 1/61) / (2/61) = 1.0
        assertThat(hits.get(0).score()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("空查询返回空结果")
    void blankQueryReturnsEmpty() {
        assertThat(provider.search(new KnowledgeSearchRequest("  ", "JD_ABILITY_EXTRACT", 5, null))).isEmpty();
        assertThat(provider.search(null)).isEmpty();
    }
}
