package com.example.matching.service.rag.impl;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.config.MilvusConfig;
import com.example.matching.config.ResilientMilvusClient;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.service.rag.RagVectorStore;
import io.milvus.client.MilvusServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H3 行为测试：Milvus 不可用时 search/insert/delete 真实降级到 MySQL，
 * 与类注释、RagVectorStore 接口注释一致；insert 降级抛可识别异常，
 * 由文档服务标记 DEGRADED 并等待补偿同步重放。
 */
class MilvusRagVectorStoreFallbackTest {

    private MilvusRagVectorStore store;
    private MysqlRagVectorStore mysqlFallback;
    private ResilientMilvusClient resilientMilvusClient;
    private MilvusConfig milvusConfig;
    private MilvusServiceClient milvusClient;

    @BeforeEach
    void setUp() {
        store = new MilvusRagVectorStore();
        mysqlFallback = mock(MysqlRagVectorStore.class);
        resilientMilvusClient = mock(ResilientMilvusClient.class);
        milvusConfig = mock(MilvusConfig.class);
        milvusClient = mock(MilvusServiceClient.class);
        when(milvusConfig.getRagCollectionName()).thenReturn("rag_knowledge_chunks");
        when(milvusConfig.getDimension()).thenReturn(1536);
        ReflectionTestUtils.setField(store, "milvusConfig", milvusConfig);
        ReflectionTestUtils.setField(store, "resilientMilvusClient", resilientMilvusClient);
        ReflectionTestUtils.setField(store, "mysqlFallbackStore", mysqlFallback);
        ReflectionTestUtils.setField(store, "vectorEmbeddingService", mock(VectorEmbeddingService.class));
    }

    private List<Float> vector1536() {
        java.util.ArrayList<Float> v = new java.util.ArrayList<>(1536);
        for (int i = 0; i < 1536; i++) v.add(0.01f);
        return v;
    }

    @Test
    void searchFallsBackToMysqlWhenMilvusClientMissing() {
        when(resilientMilvusClient.getClient()).thenReturn(null);
        RagVectorStore.ScoredChunk chunk = new RagVectorStore.ScoredChunk(
                new RagKnowledgeChunk(), 0.9f);
        when(mysqlFallback.search(any(), anyInt(), any())).thenReturn(List.of(chunk));

        List<RagVectorStore.ScoredChunk> result = store.search(vector1536(), 10, List.of("KNOWLEDGE_DOC"));

        verify(mysqlFallback).search(any(), eq(10), any());
        assertThat(result).hasSize(1);
        assertThat(store.getFallbackCount()).isGreaterThan(0);
    }

    @Test
    void searchFallsBackToMysqlWhenMilvusCallThrows() {
        when(resilientMilvusClient.getClient()).thenReturn(milvusClient);
        when(milvusClient.hasCollection(any())).thenThrow(new RuntimeException("connection refused"));
        when(mysqlFallback.search(any(), anyInt(), any())).thenReturn(List.of());

        store.search(vector1536(), 10, List.of("KNOWLEDGE_DOC"));

        verify(mysqlFallback).search(any(), eq(10), any());
        assertThat(store.getFallbackCount()).isGreaterThan(0);
    }

    @Test
    void insertThrowsFallbackExceptionInsteadOfRawIllegalStateWhenMilvusUnavailable() {
        when(resilientMilvusClient.getClient()).thenReturn(null);

        RagKnowledgeChunk chunk = new RagKnowledgeChunk();
        chunk.setId(1L);
        chunk.setDocumentId(2L);
        chunk.setChunkText("text");

        assertThatThrownBy(() -> store.insert(chunk, "KNOWLEDGE_DOC", vector1536()))
                .isInstanceOf(RagVectorStoreFallbackException.class)
                .hasMessageContaining("Milvus");
        // 降级路径不影响 MySQL 权威表（数据已由文档服务写入）
        verify(mysqlFallback, never()).insert(any(), any(), any());
    }

    @Test
    void deleteDoesNotThrowWhenMilvusUnavailable() {
        when(resilientMilvusClient.getClient()).thenReturn(null);

        store.deleteByDocumentId(2L);
        store.deleteByChunkId(3L);

        assertThat(store.getFallbackCount()).isGreaterThan(0);
    }
}
