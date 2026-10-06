package com.example.matching.service.evolution;

import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.service.evolution.impl.PostEvolutionAgentPipeline;
import com.example.matching.service.evolution.support.EvolutionAbilityTagCatalog;
import com.example.matching.service.evolution.support.EvolutionAbilityTagResolver;
import com.example.matching.service.evolution.support.ResolvedEvolutionAbility;
import com.example.matching.service.evolution.PostEvolutionKnowledgeRetrievalService.RetrievalResult;
import com.example.matching.service.evolution.PostEvolutionSignalService.EvolutionSignal;
import com.example.matching.dto.evolution.PostEvolutionAgentRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostEvolutionAgentPipelineTest {

    @Mock private com.example.matching.mapper.evolution.PostEvolutionTaskMapper taskMapper;
    @Mock private com.example.matching.mapper.evolution.PostEvolutionChangeItemMapper changeItemMapper;
    @Mock private com.example.matching.mapper.evolution.PostEvolutionEvidenceMapper evidenceMapper;
    @Mock private com.example.matching.mapper.post.PostAbilityModelMapper postAbilityModelMapper;
    @Mock private PostEvolutionKnowledgeRetrievalService knowledgeRetrievalService;
    @Mock private PostEvolutionSignalService signalService;
    @Mock private EvolutionHarnessOrchestrator harnessOrchestrator;
    @Mock private EvolutionAbilityTagResolver abilityTagResolver;
    @Mock private EvolutionAbilityTagCatalog abilityTagCatalog;
    @Mock private com.example.matching.integration.zhihu.ZhihuSearchClient zhihuSearchClient;
    @Mock private com.example.matching.integration.zhihu.ZhihuApiProperties zhihuApiProperties;
    @Mock private com.example.matching.service.evolution.ExternalResourceCleaningService externalResourceCleaningService;

    @InjectMocks private PostEvolutionAgentPipeline pipeline;

    @Test
    void buildKeywordsIncludesPostContextAndCurrentAbilityNames() {
        AbilityTag java = new AbilityTag();
        java.setId(11L);
        java.setTagName("Java");
        when(abilityTagCatalog.activeTags()).thenReturn(List.of(java));
        PostAbilityModel ability = new PostAbilityModel();
        ability.setTagId(11L);
        PostEvolutionAgentRequest request = new PostEvolutionAgentRequest();
        request.setPostName("Java开发工程师");
        request.setIndustry("互联网");
        request.setBusinessDomain("企业平台");

        List<String> keywords = pipeline.buildKeywords(request, List.of(ability));

        assertThat(keywords).contains("Java开发工程师", "互联网", "企业平台", "Java");
    }

    @Test
    void compareSkipsProposalWhenExistingAbilityHasNoEffectiveChange() {
        PostAbilityModel current = new PostAbilityModel();
        current.setTagId(11L);
        current.setMinRequiredLevel(2);
        current.setWeight(new BigDecimal("20"));
        current.setIsCore(0);
        EvolutionSignal signal = new EvolutionSignal("ZHIHU_TREND", "Java", 11L,
                "UPDATE", "Java trend evidence", List.of("zhihu:1"), 0.8D, 0.5D);

        assertThat(pipeline.compareWithCurrentModel(List.of(signal), List.of(current))).isEmpty();
    }

    @Test
    void generateSignalsAggregatesRepeatedEvidenceForOneAbility() {
        when(abilityTagResolver.resolve("Java requires stronger delivery")).thenReturn(new ResolvedEvolutionAbility(11L, "Java", 1D));
        when(abilityTagResolver.resolve("Java ecosystem changes")).thenReturn(new ResolvedEvolutionAbility(11L, "Java", 1D));
        PostEvolutionAgentRequest request = new PostEvolutionAgentRequest();
        List<EvolutionSignal> signals = pipeline.generateSignals(request,
                List.of(new RetrievalResult("1", "Java requires stronger delivery", "whitepaper", "ABILITY_REQUIREMENT", "source:INDUSTRY_WHITEPAPER:1", 0.8D)),
                List.of(new RetrievalResult("2", "Java ecosystem changes", "internal", "BUSINESS_CHANGE", "source:CLOUD_KNOWLEDGE_INTERNAL:2", 0.7D)),
                List.of(), List.of(), List.of());

        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).sourceRefs()).containsExactlyInAnyOrder(
                "source:INDUSTRY_WHITEPAPER:1", "source:CLOUD_KNOWLEDGE_INTERNAL:2");
        assertThat(signals.get(0).supportScore()).isEqualTo(1.0D);
    }

    @Test
    void generateSignalsAcceptsZhihuTrendAfterBaseSignalsAreAggregated() {
        when(abilityTagResolver.resolve("Java trend from Zhihu"))
                .thenReturn(new ResolvedEvolutionAbility(11L, "Java", 1D));
        PostEvolutionAgentRequest request = new PostEvolutionAgentRequest();

        List<EvolutionSignal> signals = pipeline.generateSignals(request,
                List.of(), List.of(), List.of(),
                List.of(new RetrievalResult("zhihu-1", "Java trend from Zhihu", "Zhihu trend", "ZHIHU_TREND",
                        "https://example.test/zhihu-1", 0.6D)),
                List.of());

        assertThat(signals).singleElement().satisfies(signal -> {
            assertThat(signal.signalType()).isEqualTo("ZHIHU_TREND");
            assertThat(signal.abilityTagId()).isEqualTo(11L);
        });
    }

    @Test
    void compareConvertsRuleConfidenceToPercentBeforeRiskClassification() {
        PostAbilityModel current = new PostAbilityModel();
        current.setTagId(11L);
        current.setAbilityName("Java");
        current.setMinRequiredLevel(2);
        current.setWeight(new BigDecimal("20"));
        current.setIsCore(0);
        EvolutionSignal signal = new EvolutionSignal("ABILITY_LEVEL_UP", "Java", 11L,
                "UPDATE", "Java requires a stronger delivery capability.",
                List.of("source:INDUSTRY_WHITEPAPER:1"), 0.8D, 0.72D);

        var proposal = pipeline.compareWithCurrentModel(List.of(signal), List.of(current)).get(0);

        assertThat(proposal.getConfidenceScore()).isEqualTo(80D);
        assertThat(proposal.getSupportScore()).isEqualTo(72D);
        assertThat(proposal.getRiskLevel()).isEqualTo("LOW");
    }

    @Test
    void saveChangeItemsLinksAllMatchingEvidenceToOneAbilityReview() {
        var proposal = new com.example.matching.dto.evolution.PostEvolutionAgentResult.PostEvolutionChangeProposal();
        proposal.setAbilityName("Java");
        proposal.setChangeType("ADD");
        proposal.setEvidenceText("Java Spring Boot 微服务改造");
        proposal.setSourceRefs(List.of("source:INDUSTRY_WHITEPAPER:1", "source:MARKET_JD:2"));
        proposal.setHarnessDecision("REVIEW");

        var first = new com.example.matching.entity.evolution.PostEvolutionEvidence();
        first.setSourceRef("source:INDUSTRY_WHITEPAPER:1");
        first.setEvidenceText("Java Spring Boot 微服务改造");
        var second = new com.example.matching.entity.evolution.PostEvolutionEvidence();
        second.setSourceRef("source:MARKET_JD:2");
        second.setEvidenceText("要求具备 Java 微服务经验");

        doAnswer(invocation -> {
            ((com.example.matching.entity.evolution.PostEvolutionChangeItem) invocation.getArgument(0)).setId(88L);
            return 1;
        }).when(changeItemMapper).insert(org.mockito.ArgumentMatchers.<com.example.matching.entity.evolution.PostEvolutionChangeItem>any());

        assertThat(pipeline.saveChangeItems(9L, List.of(proposal), List.of(first, second))).isEqualTo(1);
        assertThat(first.getChangeItemId()).isEqualTo(88L);
        assertThat(second.getChangeItemId()).isEqualTo(88L);
        verify(evidenceMapper).updateById(first);
        verify(evidenceMapper).updateById(second);
    }
}
