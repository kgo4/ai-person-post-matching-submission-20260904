package com.example.matching.service.evolution;

import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.rag.KnowledgeSourceDocumentMapper;
import com.example.matching.service.evolution.impl.PostEvolutionKnowledgeRetrievalServiceImpl;
import com.example.matching.service.rag.KnowledgeDocumentService;
import com.example.matching.service.rag.RagRetrievalRequest;
import com.example.matching.service.rag.RagRetrievalResult;
import com.example.matching.service.rag.RagRetrievalService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostEvolutionKnowledgeRetrievalServiceImplTest {

    @Test
    void industryAndInternalRetrievalKeepTheirSourceBoundariesAndChunkTypes() {
        RagRetrievalService rag = mock(RagRetrievalService.class);
        when(rag.retrieve(any(RagRetrievalRequest.class))).thenReturn(RagRetrievalResult.builder()
                .hits(List.of(RagRetrievalResult.RagHit.builder()
                        .chunkId(9L).documentId(3L).sourceType("INDUSTRY_WHITEPAPER")
                        .title("趋势白皮书").content("岗位能力要求发生变化").score(0.9D).build()))
                .build());
        PostEvolutionKnowledgeRetrievalServiceImpl service = new PostEvolutionKnowledgeRetrievalServiceImpl(
                rag, mock(KnowledgeDocumentService.class), mock(KnowledgeSourceDocumentMapper.class),
                mock(MarketJdDataMapper.class));

        List<PostEvolutionKnowledgeRetrievalService.RetrievalResult> industry =
                service.retrieveIndustryTrends("人工智能", List.of("算法工程师"), 20);
        List<PostEvolutionKnowledgeRetrievalService.RetrievalResult> internal =
                service.retrieveBusinessChanges("智能制造", List.of("岗位变化"), 20);

        ArgumentCaptor<RagRetrievalRequest> captor = ArgumentCaptor.forClass(RagRetrievalRequest.class);
        verify(rag, org.mockito.Mockito.times(2)).retrieve(captor.capture());
        assertThat(captor.getAllValues().get(0).getSourceTypes()).containsExactly("INDUSTRY_WHITEPAPER");
        assertThat(captor.getAllValues().get(1).getSourceTypes()).containsExactly("CLOUD_KNOWLEDGE_INTERNAL");
        assertThat(industry).singleElement().extracting(PostEvolutionKnowledgeRetrievalService.RetrievalResult::chunkType)
                .isEqualTo("ABILITY_REQUIREMENT");
        assertThat(internal).singleElement().extracting(PostEvolutionKnowledgeRetrievalService.RetrievalResult::chunkType)
                .isEqualTo("BUSINESS_CHANGE");
    }

    @Test
    void marketCluesOnlyReturnJdsBackedByOriginalTextAndModelOverlap() {
        MarketJdData matching = marketJd(12L, "[101, 202]", "需要 Java 和 Spring Boot", "负责服务端开发");
        MarketJdData noText = marketJd(13L, "[101]", null, null);
        MarketJdData unrelated = marketJd(14L, "[303]", "需要 Python", "负责算法开发");
        MarketJdDataMapper mapper = mock(MarketJdDataMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(matching, noText, unrelated));

        PostEvolutionKnowledgeRetrievalServiceImpl service = new PostEvolutionKnowledgeRetrievalServiceImpl(
                mock(RagRetrievalService.class), mock(KnowledgeDocumentService.class),
                mock(KnowledgeSourceDocumentMapper.class), mapper);

        List<PostEvolutionKnowledgeRetrievalService.RetrievalResult> results =
                service.retrieveMarketEvolutionClues(7L, List.of(101L), 20);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.sourceRef()).isEqualTo("source:MARKET_JD:12");
            assertThat(result.chunkText()).contains("需要 Java 和 Spring Boot");
            assertThat(result.chunkType()).isEqualTo("MARKET_ABILITY_REQUIREMENT");
        });
    }

    @Test
    void genericPostRetrievalPassesSourceScopeAndClassifiesRecruitmentJd() {
        RagRetrievalService rag = mock(RagRetrievalService.class);
        when(rag.retrieve(any(RagRetrievalRequest.class))).thenReturn(RagRetrievalResult.builder()
                .hits(List.of(RagRetrievalResult.RagHit.builder()
                        .chunkId(18L).documentId(4L).title("招聘 JD")
                        .content("负责 Java 服务开发").score(0.8D).build()))
                .build());
        PostEvolutionKnowledgeRetrievalServiceImpl service = new PostEvolutionKnowledgeRetrievalServiceImpl(
                rag, mock(KnowledgeDocumentService.class), mock(KnowledgeSourceDocumentMapper.class),
                mock(MarketJdDataMapper.class));

        List<PostEvolutionKnowledgeRetrievalService.RetrievalResult> results = service.retrieveForPost(
                new PostEvolutionKnowledgeRetrievalService.RetrievalRequest(
                        null, "Java 开发工程师", null, null,
                        List.of("Java"), List.of("RECRUITMENT_JD"), 10));

        ArgumentCaptor<RagRetrievalRequest> captor = ArgumentCaptor.forClass(RagRetrievalRequest.class);
        verify(rag).retrieve(captor.capture());
        assertThat(captor.getValue().getSourceTypes()).containsExactly("RECRUITMENT_JD");
        assertThat(results).singleElement().extracting(PostEvolutionKnowledgeRetrievalService.RetrievalResult::chunkType)
                .isEqualTo("POST_REQUIREMENT");
    }

    private MarketJdData marketJd(Long id, String tags, String requirements, String description) {
        MarketJdData data = new MarketJdData();
        data.setId(id);
        data.setAnalysisStatus(1);
        data.setIsDuplicate(0);
        data.setSkillTags(tags);
        data.setRequirements(requirements);
        data.setJobDescription(description);
        data.setPostName("Java 开发工程师");
        data.setCompanyName("测试公司");
        return data;
    }
}
