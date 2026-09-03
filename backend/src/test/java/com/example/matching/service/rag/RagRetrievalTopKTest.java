package com.example.matching.service.rag;

import com.example.matching.config.VolcengineKnowledgeBaseProperties;
import com.example.matching.service.rag.impl.MysqlKnowledgeSearchProvider;
import com.example.matching.service.rag.impl.RagContextServiceImpl;
import com.example.matching.service.rag.impl.RagRetrievalServiceImpl;
import com.example.matching.service.rag.impl.VolcengineKnowledgeSearchProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C-04 行为测试：RAG 上下文 Top-K 上限必须统一由 rag.context.max-chunks 配置控制。
 * <p>
 * 验证场景：场景默认 topK=8 实际传到底层 Provider；显式 topK&gt;8 被截断为 8；
 * 场景默认值小于上限时仍使用场景较小值；两条检索路径结果一致。
 */
class RagRetrievalTopKTest {

    private static final int MAX_CHUNKS = 8;

    private MysqlKnowledgeSearchProvider mysqlProvider;
    private VolcengineKnowledgeSearchProvider volcengineProvider;
    private VolcengineKnowledgeBaseProperties props;

    @BeforeEach
    void setUp() {
        mysqlProvider = mock(MysqlKnowledgeSearchProvider.class);
        volcengineProvider = mock(VolcengineKnowledgeSearchProvider.class);
        props = mock(VolcengineKnowledgeBaseProperties.class);
        when(props.getProviderMode()).thenReturn("mysql");
        when(mysqlProvider.search(any())).thenReturn(List.of());
    }

    private RagRetrievalServiceImpl newRetrievalService() {
        RagRetrievalServiceImpl service = new RagRetrievalServiceImpl(
                mysqlProvider, volcengineProvider, props, mock(RagQueryLogService.class));
        ReflectionTestUtils.setField(service, "maxContextChunks", MAX_CHUNKS);
        return service;
    }

    private RagContextServiceImpl newContextService(RagRetrievalService retrievalService) {
        return new RagContextServiceImpl(retrievalService);
    }

    private int capturedTopK() {
        ArgumentCaptor<KnowledgeSearchRequest> captor = ArgumentCaptor.forClass(KnowledgeSearchRequest.class);
        verify(mysqlProvider).search(captor.capture());
        return captor.getValue().topK();
    }

    @Test
    void scenarioDefaultTopKOfEightReachesProvider() {
        RagRetrievalServiceImpl service = newRetrievalService();

        service.retrieve(RagRetrievalRequest.builder()
                .queryText("需要Java开发能力")
                .scenario(RagScenarioEnum.REPORT_GENERATION)
                .build());

        assertThat(capturedTopK()).isEqualTo(8);
    }

    @Test
    void explicitTopKAboveMaxIsTruncatedToConfiguredMax() {
        RagRetrievalServiceImpl service = newRetrievalService();

        service.retrieve(RagRetrievalRequest.builder()
                .queryText("需要Java开发能力")
                .scenario(RagScenarioEnum.REPORT_GENERATION)
                .topK(10)
                .build());

        assertThat(capturedTopK()).isEqualTo(8);
    }

    @Test
    void scenarioSmallerDefaultTopKIsRespected() {
        RagRetrievalServiceImpl service = newRetrievalService();

        service.retrieve(RagRetrievalRequest.builder()
                .queryText("需要Java开发能力")
                .scenario(RagScenarioEnum.MATCHING_ANALYSIS)
                .build());

        assertThat(capturedTopK()).isEqualTo(5);
    }

    @Test
    void contextServiceAndRetrievalServiceUseSameTopKCap() {
        RagRetrievalServiceImpl retrievalService = newRetrievalService();
        RagContextServiceImpl contextService = newContextService(retrievalService);

        contextService.retrieveHits("需要Java开发能力", RagScenarioEnum.REPORT_GENERATION.name(), 0);

        assertThat(capturedTopK()).isEqualTo(8);
    }

    @Test
    void contextServicePassesExplicitTopKWithinCap() {
        RagRetrievalServiceImpl retrievalService = newRetrievalService();
        RagContextServiceImpl contextService = newContextService(retrievalService);

        contextService.retrieveHits("需要Java开发能力", RagScenarioEnum.REPORT_GENERATION.name(), 6);

        assertThat(capturedTopK()).isEqualTo(6);
    }
}
