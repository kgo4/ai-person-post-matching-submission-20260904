package com.example.matching.service.rag;

import com.example.matching.config.VolcengineKnowledgeBaseProperties;
import com.example.matching.service.rag.impl.MysqlKnowledgeSearchProvider;
import com.example.matching.service.rag.impl.RagRetrievalServiceImpl;
import com.example.matching.service.rag.impl.VolcengineKnowledgeSearchProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H2 行为测试：火山知识库命中不再被来源白名单全量丢弃；
 * hybrid 模式下火山命中过滤为空时必须回退 MySQL，不允许直接返回空。
 */
class RagRetrievalVolcengineFilterTest {

    private MysqlKnowledgeSearchProvider mysqlProvider;
    private VolcengineKnowledgeSearchProvider volcengineProvider;
    private VolcengineKnowledgeBaseProperties props;

    @BeforeEach
    void setUp() {
        mysqlProvider = mock(MysqlKnowledgeSearchProvider.class);
        volcengineProvider = mock(VolcengineKnowledgeSearchProvider.class);
        props = mock(VolcengineKnowledgeBaseProperties.class);
        when(props.getProviderMode()).thenReturn("hybrid");
        when(mysqlProvider.search(any())).thenReturn(List.of());
    }

    private RagRetrievalServiceImpl newRetrievalService() {
        return new RagRetrievalServiceImpl(
                mysqlProvider, volcengineProvider, props, mock(RagQueryLogService.class));
    }

    private KnowledgeSearchHit volcengineHit() {
        return volcengineHit(null);
    }

    private KnowledgeSearchHit volcengineHit(String originSourceType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("backend", "volcengine");
        if (originSourceType != null) {
            metadata.put("originSourceType", originSourceType);
        }
        return new KnowledgeSearchHit("volcengine:c1", "volcengine-doc", "VOLCENGINE_KB",
                "title", "content", 0.9f, metadata, 0.9d, "RERANK", "RANK_BASED", 0.9d);
    }

    private KnowledgeSearchHit mysqlHit() {
        return new KnowledgeSearchHit("mysql:1", "mysql-doc:1", "KNOWLEDGE_DOC",
                "mysql-title", "mysql-content", 0.8f, Map.of("backend", "mysql"));
    }

    @Test
    void volcengineHitPassesCloudAllowedScenario() {
        when(volcengineProvider.search(any())).thenReturn(List.of(volcengineHit()));

        RagRetrievalResult result = newRetrievalService().retrieve(RagRetrievalRequest.builder()
                .queryText("java知识")
                .scenario(RagScenarioEnum.KNOWLEDGE_QA)
                .build());

        assertThat(result.getProviderMode()).isEqualTo("volcengine");
        assertThat(result.getHits()).hasSize(1);
        assertThat(result.getHits().get(0).getSourceType()).isEqualTo("VOLCENGINE_KB");
        verify(mysqlProvider, never()).search(any());
    }

    @Test
    void volcengineHitFilteredEmptyFallsBackToMysql() {
        // allowCloud=false 场景：火山命中被白名单过滤为空 → 必须回退 MySQL，而不是返回空
        when(volcengineProvider.search(any())).thenReturn(List.of(volcengineHit()));
        when(mysqlProvider.search(any())).thenReturn(List.of(mysqlHit()));

        RagRetrievalResult result = newRetrievalService().retrieve(RagRetrievalRequest.builder()
                .queryText("java知识")
                .scenario(RagScenarioEnum.EVIDENCE_TRACE)
                .build());

        verify(mysqlProvider).search(any());
        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getProviderMode()).isEqualTo("mysql");
        assertThat(result.getHits()).hasSize(1);
        assertThat(result.getHits().get(0).getSourceType()).isEqualTo("KNOWLEDGE_DOC");
    }

    @Test
    void nonCloudScenarioNeverAdoptsVolcengineHit() {
        when(volcengineProvider.search(any())).thenReturn(List.of(volcengineHit()));

        RagRetrievalResult result = newRetrievalService().retrieve(RagRetrievalRequest.builder()
                .queryText("java知识")
                .scenario(RagScenarioEnum.EVIDENCE_TRACE)
                .build());

        assertThat(result.getProviderMode()).isEqualTo("mysql");
        assertThat(result.getHits()).isEmpty();
    }

    @Test
    void cloudHitWithAllowedOriginSourceTypePassesFineFilter() {
        // 火山命中携带真实业务来源类型且属于场景白名单 → 精细过滤放行
        when(volcengineProvider.search(any())).thenReturn(List.of(volcengineHit("KNOWLEDGE_DOC")));

        RagRetrievalResult result = newRetrievalService().retrieve(RagRetrievalRequest.builder()
                .queryText("java知识")
                .scenario(RagScenarioEnum.KNOWLEDGE_QA)
                .build());

        assertThat(result.getProviderMode()).isEqualTo("volcengine");
        assertThat(result.getHits()).hasSize(1);
    }

    @Test
    void cloudHitWithDisallowedOriginSourceTypeFallsBackToMysql() {
        // 火山命中携带真实来源类型但不在场景白名单 → 过滤后回退 MySQL
        when(volcengineProvider.search(any())).thenReturn(List.of(volcengineHit("CONTEST_EVIDENCE")));
        when(mysqlProvider.search(any())).thenReturn(List.of(mysqlHit()));

        RagRetrievalResult result = newRetrievalService().retrieve(RagRetrievalRequest.builder()
                .queryText("java知识")
                .scenario(RagScenarioEnum.KNOWLEDGE_QA)
                .build());

        verify(mysqlProvider).search(any());
        assertThat(result.getProviderMode()).isEqualTo("mysql");
        assertThat(result.getHits()).hasSize(1);
        assertThat(result.getHits().get(0).getSourceType()).isEqualTo("KNOWLEDGE_DOC");
    }
}

class RagScoreSemanticsTest {

    private MysqlKnowledgeSearchProvider mysqlProvider;
    private VolcengineKnowledgeSearchProvider volcengineProvider;
    private VolcengineKnowledgeBaseProperties props;
    private RagRetrievalServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mysqlProvider = mock(MysqlKnowledgeSearchProvider.class);
        volcengineProvider = mock(VolcengineKnowledgeSearchProvider.class);
        props = mock(VolcengineKnowledgeBaseProperties.class);
        when(props.getProviderMode()).thenReturn("mysql");
        when(mysqlProvider.search(any())).thenReturn(List.of());
        service = new RagRetrievalServiceImpl(mysqlProvider, volcengineProvider, props,
                mock(RagQueryLogService.class));
    }

    @Test
    void rankBasedVolcengineHitNotKilledBySimilarityThreshold() {
        // M12：火山 rank 分不得当 similarity 用；RANK_BASED 命中跳过相似度阈值过滤
        KnowledgeSearchHit hit = new KnowledgeSearchHit("volcengine:c1", "volcengine-doc",
                "VOLCENGINE_KB", "title", "content", 0.1f, Map.of("backend", "volcengine"),
                0.1d, "RERANK", "RANK_BASED", 0.1d);
        when(volcengineProvider.search(any())).thenReturn(List.of(hit));
        when(props.getProviderMode()).thenReturn("volcengine");

        RagRetrievalResult result = service.retrieve(RagRetrievalRequest.builder()
                .queryText("java知识")
                .scenario(RagScenarioEnum.KNOWLEDGE_QA)
                .forceCloud(true)
                .build());

        assertThat(result.getHits()).hasSize(1);
    }

    @Test
    void rawSemanticScoreBelowThresholdIsFiltered() {
        // M12：minSimilarity 用原始语义相似度（rawScore）过滤
        KnowledgeSearchHit hit = new KnowledgeSearchHit("mysql:1", "doc:1", "KNOWLEDGE_DOC",
                "title", "content", 0.3f, Map.of("backend", "mysql"),
                0.6d, "RERANK", "SEMANTIC", 0.3d);
        when(mysqlProvider.search(any())).thenReturn(List.of(hit));

        RagRetrievalResult result = service.retrieve(RagRetrievalRequest.builder()
                .queryText("java知识")
                .scenario(RagScenarioEnum.KNOWLEDGE_QA)
                .minSimilarity(0.7d)
                .build());

        assertThat(result.getHits()).isEmpty();
    }
}
