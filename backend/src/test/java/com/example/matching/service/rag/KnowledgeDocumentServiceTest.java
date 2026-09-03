package com.example.matching.service.rag;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.dto.rag.KnowledgeDocumentSaveDTO;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.rag.RagKnowledgeChunkMapper;
import com.example.matching.mapper.rag.RagKnowledgeDocumentMapper;
import com.example.matching.port.contest.ContestQueryPort;
import com.example.matching.port.learning.LearningQueryPort;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.post.PostQueryPort.*;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.tag.TagQueryPort.*;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.port.talent.TalentQueryPort.*;
import com.example.matching.service.rag.impl.KnowledgeDocumentServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceTest {

    @Mock private RagKnowledgeDocumentMapper documentMapper;
    @Mock private RagKnowledgeChunkMapper chunkMapper;
    @Mock private KnowledgeChunker knowledgeChunker;
    @Mock private VectorEmbeddingService vectorEmbeddingService;
    @Mock private RagVectorStore ragVectorStore;
    @Mock private ObjectMapper objectMapper;
    @Mock private PostQueryPort postQueryPort;
    @Mock private TagQueryPort tagQueryPort;
    @Mock private LearningQueryPort learningQueryPort;
    @Mock private TalentQueryPort talentQueryPort;
    @Mock private ContestQueryPort contestQueryPort;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private com.example.matching.port.knowledge.KnowledgeProjectionPort knowledgeProjectionPort;
    @Mock private com.example.matching.service.rag.KnowledgeDocumentDeduplicator deduplicator;
    @Mock private com.example.matching.config.EmbeddingDescriptor embeddingDescriptor;

    @InjectMocks
    private KnowledgeDocumentServiceImpl knowledgeDocumentService;

    private KnowledgeDocumentSaveDTO saveDTO;

    @BeforeEach
    void setUp() {
        lenient().when(deduplicator.canonicalHash(any())).thenReturn("canonical-hash");
        lenient().when(deduplicator.sourceGroup(any())).thenReturn("GENERAL");
        lenient().when(embeddingDescriptor.modelName()).thenReturn("text-embedding-v1");
        lenient().when(embeddingDescriptor.dimension()).thenReturn(1536);
        saveDTO = new KnowledgeDocumentSaveDTO();
        saveDTO.setSourceType("MANUAL_TEXT");
        saveDTO.setTitle("测试文档");
        saveDTO.setContent("这是一段测试内容，用于验证知识文档服务的功能是否正常工作。");
        // stub empty defaults for Port methods
        lenient().when(postQueryPort.listActivePosts(anyInt())).thenReturn(List.of());
        lenient().when(postQueryPort.listActivePrototypes(anyInt())).thenReturn(List.of());
        lenient().when(postQueryPort.listAnalyzedJdImportTasks(anyInt())).thenReturn(List.of());
        lenient().when(postQueryPort.listActivePostAbilityModels(anyInt())).thenReturn(List.of());
        lenient().when(tagQueryPort.listActiveTags(anyInt())).thenReturn(List.of());
        lenient().when(learningQueryPort.listActiveResources(anyInt())).thenReturn(List.of());
        lenient().when(talentQueryPort.listActiveAbilities(anyInt())).thenReturn(List.of());
        lenient().when(talentQueryPort.listActiveEmployees(anyInt())).thenReturn(List.of());
        lenient().when(contestQueryPort.listAllEvidence(anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("保存文档：创建成功")
    void saveDocument_createSuccess() {
        doAnswer(invocation -> {
            invocation.getArgument(0, RagKnowledgeDocument.class).setId(99L);
            return 1;
        }).when(documentMapper).insert(any(RagKnowledgeDocument.class));

        RagKnowledgeDocument result = knowledgeDocumentService.saveDocument(saveDTO);

        assertNotNull(result);
        assertEquals("MANUAL_TEXT", result.getSourceType());
        assertEquals("测试文档", result.getTitle());
        assertEquals("ACTIVE", result.getDocStatus());
        assertNotNull(result.getDocCode());
        assertTrue(result.getDocCode().startsWith("DOC_"));
        verify(documentMapper, times(1)).insert(any(RagKnowledgeDocument.class));
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertInstanceOf(com.example.matching.event.RagKnowledgeDocumentSavedEvent.class, eventCaptor.getValue());
        assertEquals(99L, ((com.example.matching.event.RagKnowledgeDocumentSavedEvent) eventCaptor.getValue()).documentId());
    }

    @Test
    @DisplayName("索引文档：创建分块")
    void indexDocument_createsChunks() throws Exception {
        RagKnowledgeDocument doc = new RagKnowledgeDocument();
        doc.setId(1L);
        doc.setContent("这是一段足够长的测试内容，用于验证分块功能。Java是一种广泛使用的编程语言。");
        doReturn(doc).when(documentMapper).selectById(1L);

        List<String> chunks = Arrays.asList("分块1", "分块2");
        when(knowledgeChunker.chunk(any(), org.mockito.ArgumentMatchers.any())).thenReturn(chunks);
        when(vectorEmbeddingService.embedBatch(any())).thenReturn(Arrays.asList(
                Arrays.asList(0.1f, 0.2f), Arrays.asList(0.3f, 0.4f)));
        when(objectMapper.writeValueAsString(any())).thenReturn("[0.1, 0.2]");
        doReturn(1).when(chunkMapper).insert(any(RagKnowledgeChunk.class));
        doReturn(1).when(documentMapper).updateById(any(RagKnowledgeDocument.class));

        int count = knowledgeDocumentService.indexDocument(1L);

        assertEquals(2, count);
        verify(chunkMapper, never()).delete(any());
        verify(chunkMapper, times(2)).insert(any(RagKnowledgeChunk.class));
        verify(documentMapper, times(1)).updateById(any(RagKnowledgeDocument.class));
    }

    @Test
    @DisplayName("同一文档版本重复索引时复用已有分块，不重复插入")
    void indexDocument_sameRevisionIsIdempotent() {
        RagKnowledgeDocument doc = new RagKnowledgeDocument();
        doc.setId(380L);
        doc.setContentRevision(4L);
        doc.setIndexedRevision(4L);
        doc.setIndexingStatus("INDEXED");
        doReturn(doc).when(documentMapper).selectById(380L);

        RagKnowledgeChunk existing = new RagKnowledgeChunk();
        existing.setId(1L);
        existing.setDocumentId(380L);
        existing.setChunkIndex(0);
        existing.setDocumentRevision(4L);
        when(chunkMapper.selectList(any())).thenReturn(List.of(existing));

        assertEquals(1, knowledgeDocumentService.indexDocument(380L));
        verify(chunkMapper, never()).insert(any(RagKnowledgeChunk.class));
        verify(knowledgeChunker, never()).chunk(any(), any());
    }

    @Test
    @DisplayName("H3: Milvus 不可用时索引降级为 DEGRADED 且不更新 indexedRevision")
    void indexDocument_milvusFallbackMarksDegradedAndKeepsRevision() throws Exception {
        RagKnowledgeDocument doc = new RagKnowledgeDocument();
        doc.setId(1L);
        doc.setContent("这是一段足够长的测试内容，用于验证分块功能。Java是一种广泛使用的编程语言。");
        doc.setContentRevision(5L);
        doc.setIndexedRevision(4L);
        doReturn(doc).when(documentMapper).selectById(1L);

        List<String> chunks = Arrays.asList("分块1", "分块2");
        when(knowledgeChunker.chunk(any(), org.mockito.ArgumentMatchers.any())).thenReturn(chunks);
        when(vectorEmbeddingService.embedBatch(any())).thenReturn(Arrays.asList(
                Arrays.asList(0.1f, 0.2f), Arrays.asList(0.3f, 0.4f)));
        when(objectMapper.writeValueAsString(any())).thenReturn("[0.1, 0.2]");
        doReturn(1).when(chunkMapper).insert(any(RagKnowledgeChunk.class));
        doReturn(1).when(documentMapper).updateById(any(RagKnowledgeDocument.class));
        // Milvus 不可用：insert 抛可识别的降级异常
        org.mockito.Mockito.doThrow(new com.example.matching.service.rag.impl.RagVectorStoreFallbackException(
                        "Milvus is unavailable for RAG projection"))
                .when(ragVectorStore).insert(any(RagKnowledgeChunk.class), any(), any());

        int count = knowledgeDocumentService.indexDocument(1L);

        // 降级成功：文档标记 DEGRADED，indexedRevision 保持旧值（< contentRevision，补偿调度器可重放）
        assertEquals(2, count);
        org.mockito.ArgumentCaptor<RagKnowledgeDocument> captor =
                org.mockito.ArgumentCaptor.forClass(RagKnowledgeDocument.class);
        verify(documentMapper).updateById(captor.capture());
        assertEquals("DEGRADED", captor.getValue().getIndexingStatus());
        assertEquals(4L, captor.getValue().getIndexedRevision().longValue());
    }

    @Test
    @DisplayName("嵌入服务未返回向量时保留 MySQL 分块并标记为可补偿降级")
    void indexDocument_missingEmbeddingsMarksDegradedInsteadOfFailing() {
        RagKnowledgeDocument doc = new RagKnowledgeDocument();
        doc.setId(18L);
        doc.setContent("用于验证嵌入服务临时不可用时的知识文档降级行为。");
        doc.setContentRevision(5L);
        doc.setIndexedRevision(4L);
        doReturn(doc).when(documentMapper).selectById(18L);
        when(knowledgeChunker.chunk(any(), any())).thenReturn(List.of("分块1", "分块2"));
        when(vectorEmbeddingService.embedBatch(any())).thenReturn(List.of(List.of(), List.of()));

        assertEquals(2, knowledgeDocumentService.indexDocument(18L));
        verify(chunkMapper, times(2)).insert(any(RagKnowledgeChunk.class));
        verify(ragVectorStore, never()).insert(any(), any(), any());
        ArgumentCaptor<RagKnowledgeDocument> captor = ArgumentCaptor.forClass(RagKnowledgeDocument.class);
        verify(documentMapper).updateById(captor.capture());
        assertEquals("DEGRADED", captor.getValue().getIndexingStatus());
        assertEquals(4L, captor.getValue().getIndexedRevision());
    }

    @Test
    @DisplayName("索引文档：空内容返回0")
    void indexDocument_emptyContentReturnsZero() {
        RagKnowledgeDocument doc = new RagKnowledgeDocument();
        doc.setId(1L);
        doc.setContent("");
        doReturn(doc).when(documentMapper).selectById(1L);
        when(knowledgeChunker.chunk(any(), org.mockito.ArgumentMatchers.any())).thenReturn(Collections.emptyList());
        doReturn(1).when(documentMapper).updateById(any(RagKnowledgeDocument.class));

        int count = knowledgeDocumentService.indexDocument(1L);

        assertEquals(0, count);
    }

    @Test
    @DisplayName("batch index only unindexed documents")
    void indexDocuments_onlyUnindexedDocuments() throws Exception {
        RagKnowledgeDocument doc1 = new RagKnowledgeDocument();
        doc1.setId(1L);
        doc1.setContent("Java Spring Boot backend service");
        doc1.setChunkCount(0);
        RagKnowledgeDocument doc2 = new RagKnowledgeDocument();
        doc2.setId(2L);
        doc2.setContent("Vue frontend dashboard");
        doc2.setChunkCount(0);

        when(documentMapper.selectList(any())).thenReturn(List.of(doc1, doc2));
        doReturn(doc1).when(documentMapper).selectById(1L);
        doReturn(doc2).when(documentMapper).selectById(2L);
        when(knowledgeChunker.chunk(any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of("chunk"));
        when(vectorEmbeddingService.embedBatch(any())).thenReturn(List.of(List.of(0.1f, 0.2f)));
        when(objectMapper.writeValueAsString(any())).thenReturn("[0.1,0.2]");
        doReturn(1).when(chunkMapper).insert(any(RagKnowledgeChunk.class));
        doReturn(1).when(documentMapper).updateById(any(RagKnowledgeDocument.class));

        Map<String, Object> result = knowledgeDocumentService.indexDocuments("MANUAL_TEXT", true, 100);

        assertEquals(2, result.get("documentCount"));
        assertEquals(2, result.get("chunkCount"));
        verify(chunkMapper, never()).delete(any());
        verify(chunkMapper, times(2)).insert(any(RagKnowledgeChunk.class));
    }

    @Test
    @DisplayName("回填员工能力：原系统人员能力进入RAG知识文档")
    void backfillDocuments_empAbilityCreatesKnowledgeDocument() {
        when(talentQueryPort.listActiveAbilities(anyInt())).thenReturn(List.of(
                new EmployeeAbilityDTO(11L, 1L, 2L, 4, "AI_ASSESSMENT", new BigDecimal("0.85"), null, null)));
        doReturn(0L).when(documentMapper).selectCount(any());
        ArgumentCaptor<RagKnowledgeDocument> captor = ArgumentCaptor.forClass(RagKnowledgeDocument.class);
        doReturn(1).when(documentMapper).insert(captor.capture());

        int created = knowledgeDocumentService.backfillDocuments("EMP_ABILITY", 10);

        assertEquals(1, created);
        RagKnowledgeDocument doc = captor.getValue();
        assertEquals("EMP_ABILITY", doc.getSourceType());
        assertEquals(11L, doc.getSourceRefId());
        assertTrue(doc.getTitle().contains("#11"));
        assertTrue(doc.getContent().contains("AI_ASSESSMENT"));
    }

    @Test
    @DisplayName("回填岗位能力模型：原系统岗位能力进入RAG知识文档")
    void backfillDocuments_postAbilityModelCreatesKnowledgeDocument() {
        when(postQueryPort.listActivePostAbilityModels(anyInt())).thenReturn(List.of(
                new PostAbilityDTO(21L, 3L, 2L, 4, new BigDecimal("75"), 1, 1, null, null, null)));
        doReturn(0L).when(documentMapper).selectCount(any());
        ArgumentCaptor<RagKnowledgeDocument> captor = ArgumentCaptor.forClass(RagKnowledgeDocument.class);
        doReturn(1).when(documentMapper).insert(captor.capture());

        int created = knowledgeDocumentService.backfillDocuments("POST_ABILITY_MODEL", 10);

        assertEquals(1, created);
        RagKnowledgeDocument doc = captor.getValue();
        assertEquals("POST_ABILITY_MODEL", doc.getSourceType());
        assertEquals(21L, doc.getSourceRefId());
        assertTrue(doc.getTitle().contains("#21"));
    }

    @Test
    @DisplayName("searchChunks 批量加载文档而非逐命中 selectById（无 N+1）")
    void searchChunks_batchesDocumentLookup() {
        RagKnowledgeChunk chunkA = new RagKnowledgeChunk();
        chunkA.setId(1L);
        chunkA.setDocumentId(10L);
        chunkA.setChunkText("Java 并发");
        chunkA.setChunkIndex(0);
        RagKnowledgeChunk chunkB = new RagKnowledgeChunk();
        chunkB.setId(2L);
        chunkB.setDocumentId(10L);
        chunkB.setChunkText("JVM 调优");
        chunkB.setChunkIndex(1);
        RagKnowledgeChunk chunkC = new RagKnowledgeChunk();
        chunkC.setId(3L);
        chunkC.setDocumentId(20L);
        chunkC.setChunkText("Vue 前端");
        chunkC.setChunkIndex(0);

        RagKnowledgeDocument doc10 = new RagKnowledgeDocument();
        doc10.setId(10L);
        doc10.setTitle("Java 工程师");
        doc10.setSourceType("JD_IMPORT");
        RagKnowledgeDocument doc20 = new RagKnowledgeDocument();
        doc20.setId(20L);
        doc20.setTitle("前端工程师");
        doc20.setSourceType("JD_IMPORT");

        when(vectorEmbeddingService.embed(any())).thenReturn(List.of(0.1f, 0.2f, 0.3f));
        when(ragVectorStore.search(anyList(), anyInt(), any())).thenReturn(List.of(
                new RagVectorStore.ScoredChunk(chunkA, 0.9f),
                new RagVectorStore.ScoredChunk(chunkB, 0.8f),
                new RagVectorStore.ScoredChunk(chunkC, 0.7f)));
        when(documentMapper.selectBatchIds(any())).thenReturn(List.of(doc10, doc20));

        com.example.matching.dto.rag.KnowledgeChunkSearchDTO dto =
                new com.example.matching.dto.rag.KnowledgeChunkSearchDTO();
        dto.setQueryText("Java");
        dto.setTopK(3);
        List<com.example.matching.dto.rag.KnowledgeChunkResultDTO> results =
                knowledgeDocumentService.searchChunks(dto);

        assertEquals(3, results.size());
        verify(documentMapper, times(1)).selectBatchIds(any());
        verify(documentMapper, never()).selectById(any(Long.class));
        assertEquals("Java 工程师", results.get(0).getDocumentTitle());
        assertEquals("前端工程师", results.get(2).getDocumentTitle());
        assertEquals("JD_IMPORT", results.get(0).getSourceType());
    }
}
