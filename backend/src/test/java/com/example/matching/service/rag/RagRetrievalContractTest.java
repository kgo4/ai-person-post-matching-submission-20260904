package com.example.matching.service.rag;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.config.VolcengineKnowledgeBaseProperties;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.rag.RagKnowledgeChunkMapper;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RAG 检索契约测试 - 验证分数归一化、token 预算、去重与来源保留。
 * <p>
 * 契约（Task 5 后必须全部满足）：
 * <ol>
 *   <li>本地向量分与关键词分不得未经归一化直接数值相加（统一 RRF 排序分，范围 [0,1]）</li>
 *   <li>组装后的上下文必须遵守明确的 token 预算，而非仅 chunk 数量</li>
 *   <li>同一文档的重复 chunk 不得耗尽整个上下文预算（每文档最多 2 块）</li>
 *   <li>来源类型、文档 id、chunk id 必须保留在上下文构建结果中</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RAG 检索契约")
class RagRetrievalContractTest {

    private static final int DEFAULT_TOKEN_BUDGET = 3500;

    @Mock
    private VectorEmbeddingService vectorEmbeddingService;
    @Mock
    private RagVectorStore ragVectorStore;
    @Mock
    private RagKnowledgeDocumentMapper documentMapper;
    @Mock
    private RagKnowledgeChunkMapper chunkMapper;
    @Mock
    private VolcengineKnowledgeBaseProperties knowledgeBaseProperties;
    @Mock
    private VolcengineKnowledgeSearchProvider volcengineKnowledgeSearchProvider;
    @Mock
    private MysqlKnowledgeSearchProvider mysqlKnowledgeSearchProvider;

    private MysqlKnowledgeSearchProvider provider;
    private RagContextServiceImpl contextService;

    @BeforeEach
    void setUp() {
        provider = new MysqlKnowledgeSearchProvider(
                vectorEmbeddingService, ragVectorStore, documentMapper, chunkMapper,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        RagRetrievalServiceImpl retrievalService = new RagRetrievalServiceImpl(
                mysqlKnowledgeSearchProvider, volcengineKnowledgeSearchProvider, knowledgeBaseProperties,
                mock(RagQueryLogService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(retrievalService, "maxContextChunks", 8);
        org.springframework.test.util.ReflectionTestUtils.setField(retrievalService, "maxEstimatedTokens", DEFAULT_TOKEN_BUDGET);
        contextService = new RagContextServiceImpl(retrievalService);
        lenient().when(knowledgeBaseProperties.getProviderMode()).thenReturn("mysql");
    }

    @Test
    @DisplayName("契约1: 本地向量分 0.85 与关键词分 100 不得未归一化数值相加")
    void localVectorAndKeywordScoresMustNotBeNumericallyAdded() {
        RagKnowledgeChunk chunk = chunk(1L, 1L, "熟悉Java编程语言，具备3年以上开发经验");
        when(vectorEmbeddingService.embed(any())).thenReturn(List.of(0.1f, 0.2f, 0.3f));
        when(ragVectorStore.search(anyList(), anyInt(), nullable(List.class)))
                .thenReturn(List.of(new RagVectorStore.ScoredChunk(chunk, 0.85f)));
        when(chunkMapper.findActiveByKeywordBigrams(any(), anyInt())).thenReturn(List.of(chunk));
        when(chunkMapper.selectBatchIds(any())).thenReturn(List.of(chunk));
        when(documentMapper.selectBatchIds(any()))
                .thenReturn(List.of(document(1L, "JD_IMPORT", "Java开发工程师JD")));

        List<KnowledgeSearchHit> hits = provider.search(new KnowledgeSearchRequest("Java开发", "JD_ABILITY_EXTRACT", 5, null));

        assertThat(hits).isNotEmpty();
        for (KnowledgeSearchHit hit : hits) {
            assertThat(hit.score())
                    .as("融合后分数必须在 [0,1] 范围内，不得直接 0.7*0.85 + 0.3*100")
                    .isBetween(0f, 1f);
        }
    }

    @Test
    @DisplayName("契约2: 组装上下文必须遵守 token 预算而非仅 chunk 数量")
    void assembledContextMustObeyTokenBudget() {
        List<KnowledgeSearchHit> hits = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String text = "Java并发编程与JVM调优实战经验。".repeat(80);
            hits.add(hit("mysql:" + (i + 1), "mysql-doc:1", "JD_IMPORT",
                    "Java开发工程师JD", text, 0.9f - i * 0.05f));
        }
        when(mysqlKnowledgeSearchProvider.search(any())).thenReturn(hits);

        String context = contextService.retrieveContext("Java开发", "JD_ABILITY_EXTRACT", 5);

        int estimatedTokens = estimateTokens(context);
        assertThat(estimatedTokens)
                .as("上下文估算 token 数不得超出预算 %d", DEFAULT_TOKEN_BUDGET)
                .isLessThanOrEqualTo(DEFAULT_TOKEN_BUDGET);
    }

    @Test
    @DisplayName("契约3: 同一文档的重复 chunk 不得耗尽整个上下文预算")
    void duplicateChunksFromSameDocumentMustNotConsumeFullBudget() {
        List<KnowledgeSearchHit> hits = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String text = "Java集合框架与Stream流处理。".repeat(40);
            hits.add(hit("mysql:" + (i + 1), "mysql-doc:7", "JD_IMPORT",
                    "Java开发工程师JD", text, 0.9f - i * 0.05f));
        }
        when(mysqlKnowledgeSearchProvider.search(any())).thenReturn(hits);

        String context = contextService.retrieveContext("Java集合", "JD_ABILITY_EXTRACT", 5);

        assertThat(estimateTokens(context))
                .as("同一文档的重复块必须去重且每文档最多 2 块")
                .isLessThanOrEqualTo(DEFAULT_TOKEN_BUDGET);
        assertThat(countChunkHeaders(context))
                .as("同一文档的重复块必须被去重（每文档最多 2 块）")
                .isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("契约4: 来源类型、文档 id、chunk id 必须保留在上下文构建结果中")
    void sourceIdentityMustSurviveContextConstruction() {
        List<KnowledgeSearchHit> hits = List.of(
                hit("mysql:42", "mysql-doc:7", "JD_IMPORT", "Java开发工程师JD",
                        "熟悉Java编程语言，具备3年以上开发经验", 0.95f));
        when(mysqlKnowledgeSearchProvider.search(any())).thenReturn(hits);

        String context = contextService.retrieveContext("Java开发", "JD_ABILITY_EXTRACT", 5);

        assertThat(context)
                .as("上下文必须保留来源类型、文档 id 与 chunk id")
                .contains("JD_IMPORT")
                .contains("mysql-doc:7")
                .contains("mysql:42");
    }

    @Test
    @DisplayName("legacy ability-tag scenario remains available through the unified context adapter")
    void legacyAbilityTagScenarioMapsToSupportedScenario() {
        when(mysqlKnowledgeSearchProvider.search(any())).thenReturn(List.of(
                hit("mysql:7", "mysql-doc:3", "ABILITY_TAG", "Java", "Java concurrency", 0.9f)));

        String context = contextService.retrieveContext("Java", "ABILITY_TAG", 2);

        assertThat(context).contains("ABILITY_TAG").contains("mysql:7");
    }

    private RagKnowledgeChunk chunk(Long id, Long documentId, String text) {
        RagKnowledgeChunk chunk = new RagKnowledgeChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setChunkText(text);
        chunk.setChunkStatus("ACTIVE");
        return chunk;
    }

    private RagKnowledgeDocument document(Long id, String sourceType, String title) {
        RagKnowledgeDocument doc = new RagKnowledgeDocument();
        doc.setId(id);
        doc.setSourceType(sourceType);
        doc.setTitle(title);
        return doc;
    }

    private KnowledgeSearchHit hit(String chunkId, String documentId, String sourceType,
                                   String title, String content, float score) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("backend", "mysql");
        metadata.put("documentId", documentId);
        metadata.put("chunkIndex", 0);
        return new KnowledgeSearchHit(chunkId, documentId, sourceType, title, content, score, metadata);
    }

    private int countChunkHeaders(String context) {
        if (context == null || context.isBlank()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = context.indexOf("【检索证据", idx)) != -1) {
            count++;
            idx += 5;
        }
        return count;
    }

    /**
     * 与计划一致的确定性 token 估算: CJK 码点 1.5 token/字, 非 CJK 单词 1 token/词, 加 10% 开销。
     */
    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        double cjk = 0;
        int nonCjkWords = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN
                    || Character.UnicodeScript.of(cp) == Character.UnicodeScript.HIRAGANA
                    || Character.UnicodeScript.of(cp) == Character.UnicodeScript.KATAKANA) {
                cjk++;
                inWord = false;
            } else if (Character.isLetterOrDigit(cp)) {
                if (!inWord) {
                    nonCjkWords++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
            i += Character.charCount(cp);
        }
        double raw = cjk * 1.5 + nonCjkWords;
        return (int) Math.ceil(raw * 1.1);
    }
}
