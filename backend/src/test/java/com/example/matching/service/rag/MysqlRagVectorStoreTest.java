package com.example.matching.service.rag;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.rag.RagKnowledgeChunkMapper;
import com.example.matching.mapper.rag.RagKnowledgeDocumentMapper;
import com.example.matching.service.rag.impl.MysqlRagVectorStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MySQL向量存储单元测试
 */
@ExtendWith(MockitoExtension.class)
class MysqlRagVectorStoreTest {

    @Mock
    private RagKnowledgeChunkMapper chunkMapper;

    @Mock
    private RagKnowledgeDocumentMapper documentMapper;

    @Mock
    private VectorEmbeddingService vectorEmbeddingService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MysqlRagVectorStore vectorStore;

    private RagKnowledgeDocument activeDoc;
    private RagKnowledgeChunk chunk1;
    private RagKnowledgeChunk chunk2;

    @BeforeEach
    void setUp() {
        activeDoc = new RagKnowledgeDocument();
        activeDoc.setId(1L);
        activeDoc.setDocStatus("ACTIVE");
        activeDoc.setIsDeleted(0);
        activeDoc.setSourceType("JD_IMPORT");

        chunk1 = new RagKnowledgeChunk();
        chunk1.setId(1L);
        chunk1.setDocumentId(1L);
        chunk1.setChunkStatus("ACTIVE");
        chunk1.setEmbeddingVector("[0.1, 0.2, 0.3]");

        chunk2 = new RagKnowledgeChunk();
        chunk2.setId(2L);
        chunk2.setDocumentId(1L);
        chunk2.setChunkStatus("ACTIVE");
        chunk2.setEmbeddingVector("[0.4, 0.5, 0.6]");
    }

    @Test
    @DisplayName("检索返回最高余弦相似度的分块")
    void search_returnsHighestCosineScoreFirst() throws Exception {
        List<Float> queryVector = Arrays.asList(0.1f, 0.2f, 0.3f);
        List<Float> vector1 = Arrays.asList(0.1f, 0.2f, 0.3f);
        List<Float> vector2 = Arrays.asList(0.4f, 0.5f, 0.6f);

        when(documentMapper.selectList(any())).thenReturn(Collections.singletonList(activeDoc));
        when(chunkMapper.selectList(any())).thenReturn(Arrays.asList(chunk1, chunk2));
        when(objectMapper.readValue(eq("[0.1, 0.2, 0.3]"), any(TypeReference.class))).thenReturn(vector1);
        when(objectMapper.readValue(eq("[0.4, 0.5, 0.6]"), any(TypeReference.class))).thenReturn(vector2);
        when(vectorEmbeddingService.cosineSimilarity(queryVector, vector1)).thenReturn(1.0f);
        when(vectorEmbeddingService.cosineSimilarity(queryVector, vector2)).thenReturn(0.8f);

        List<RagVectorStore.ScoredChunk> results = vectorStore.search(queryVector, 5, null);

        assertEquals(2, results.size());
        assertEquals(1.0f, results.get(0).score());
        assertEquals(1L, results.get(0).chunk().getId());
        assertEquals(0.8f, results.get(1).score());
        assertEquals(2L, results.get(1).chunk().getId());
    }

    @Test
    @DisplayName("空查询向量返回空结果")
    void search_emptyQueryVectorReturnsEmpty() {
        List<RagVectorStore.ScoredChunk> results = vectorStore.search(Collections.emptyList(), 5, null);
        assertTrue(results.isEmpty());

        results = vectorStore.search(null, 5, null);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("无活跃文档返回空结果")
    void search_noActiveDocsReturnsEmpty() {
        when(documentMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<Float> queryVector = Arrays.asList(0.1f, 0.2f, 0.3f);
        List<RagVectorStore.ScoredChunk> results = vectorStore.search(queryVector, 5, null);

        assertTrue(results.isEmpty());
        verify(chunkMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("topK限制返回数量")
    void search_topKLimitsResults() throws Exception {
        List<Float> queryVector = Arrays.asList(0.1f, 0.2f, 0.3f);
        List<Float> vector1 = Arrays.asList(0.1f, 0.2f, 0.3f);
        List<Float> vector2 = Arrays.asList(0.4f, 0.5f, 0.6f);

        when(documentMapper.selectList(any())).thenReturn(Collections.singletonList(activeDoc));
        when(chunkMapper.selectList(any())).thenReturn(Arrays.asList(chunk1, chunk2));
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(vector1).thenReturn(vector2);
        when(vectorEmbeddingService.cosineSimilarity(any(), any())).thenReturn(0.9f);

        List<RagVectorStore.ScoredChunk> results = vectorStore.search(queryVector, 1, null);

        assertEquals(1, results.size());
    }
}
