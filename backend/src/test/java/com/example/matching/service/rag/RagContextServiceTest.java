package com.example.matching.service.rag;

import com.example.matching.config.VolcengineKnowledgeBaseProperties;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.rag.RagKnowledgeDocumentMapper;
import com.example.matching.service.rag.RagQueryLogService;
import com.example.matching.service.rag.impl.MysqlKnowledgeSearchProvider;
import com.example.matching.service.rag.impl.RagContextServiceImpl;
import com.example.matching.service.rag.impl.RagRetrievalServiceImpl;
import com.example.matching.service.rag.impl.VolcengineKnowledgeSearchProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagContextServiceTest {

    @Mock
    private MysqlKnowledgeSearchProvider mysqlKnowledgeSearchProvider;

    @Mock
    private VolcengineKnowledgeSearchProvider volcengineKnowledgeSearchProvider;

    @Mock
    private VolcengineKnowledgeBaseProperties knowledgeBaseProperties;

    @Mock
    private RagKnowledgeDocumentMapper documentMapper;

    private RagContextServiceImpl ragContextService;

    private static final int TEST_BUDGET = 3500;

    private RagKnowledgeDocument doc;
    private RagKnowledgeChunk chunk1;

    @BeforeEach
    void setUp() {
        ragContextService = new RagContextServiceImpl(newRetrievalService());
        doc = new RagKnowledgeDocument();
        doc.setId(1L);
        doc.setSourceType("JD_IMPORT");
        doc.setTitle("Java开发工程师JD");

        chunk1 = new RagKnowledgeChunk();
        chunk1.setId(1L);
        chunk1.setDocumentId(1L);
        chunk1.setChunkText("熟悉Java编程语言，具备3年以上开发经验");

        lenient().when(knowledgeBaseProperties.getProviderMode()).thenReturn("mysql");
    }

    private RagRetrievalServiceImpl newRetrievalService() {
        RagRetrievalServiceImpl service = new RagRetrievalServiceImpl(
                mysqlKnowledgeSearchProvider, volcengineKnowledgeSearchProvider,
                knowledgeBaseProperties, mock(RagQueryLogService.class));
        ReflectionTestUtils.setField(service, "maxContextChunks", 8);
        ReflectionTestUtils.setField(service, "maxEstimatedTokens", TEST_BUDGET);
        return service;
    }

    @Test
    @DisplayName("检索上下文：返回格式化的来源和片段")
    void retrieveContext_returnsFormattedSourceAndChunk() {
        KnowledgeSearchHit hit = new KnowledgeSearchHit(
                "mysql:1", "mysql-doc:1", "JD_IMPORT", "Java开发工程师JD",
                "熟悉Java编程语言，具备3年以上开发经验", 0.95f, Collections.emptyMap());
        when(mysqlKnowledgeSearchProvider.search(any())).thenReturn(Collections.singletonList(hit));

        String context = ragContextService.retrieveContext("Java开发", "JD_ABILITY_EXTRACT", 5);

        assertNotNull(context);
        assertTrue(context.contains("<retrieved_context>"));
        assertTrue(context.contains("<evidence index=\"1\""));
        assertTrue(context.contains("sourceType=\"JD_IMPORT\""));
        assertTrue(context.contains("documentId=\"mysql-doc:1\""));
        assertTrue(context.contains("title=\"Java开发工程师JD\""));
        assertTrue(context.contains("熟悉Java编程语言，具备3年以上开发经验"));
    }

    @Test
    @DisplayName("空检索返回空上下文")
    void retrieveContext_emptyRetrievalReturnsEmptyContext() {
        when(mysqlKnowledgeSearchProvider.search(any())).thenReturn(Collections.emptyList());

        String context = ragContextService.retrieveContext("不存在的内容", "JD_ABILITY_EXTRACT", 5);

        assertEquals("", context);
    }

    @Test
    @DisplayName("查询日志存储场景和分块ID")
    void retrieveChunkIds_returnsChunkIds() {
        KnowledgeSearchHit hit = new KnowledgeSearchHit(
                "mysql:1", "mysql-doc:1", "JD_IMPORT", "Java开发工程师JD",
                "content", 0.9f, Collections.emptyMap());
        when(mysqlKnowledgeSearchProvider.search(any())).thenReturn(Collections.singletonList(hit));

        List<Long> chunkIds = ragContextService.retrieveChunkIds("Java", "JD_ABILITY_EXTRACT", 5);

        assertEquals(1, chunkIds.size());
        assertEquals(1L, chunkIds.get(0));
    }

    @Test
    @DisplayName("空查询返回空结果")
    void retrieveContext_emptyQueryReturnsEmpty() {
        assertEquals("", ragContextService.retrieveContext("", "JD_ABILITY_EXTRACT", 5));
        assertEquals("", ragContextService.retrieveContext(null, "JD_ABILITY_EXTRACT", 5));
    }
}
