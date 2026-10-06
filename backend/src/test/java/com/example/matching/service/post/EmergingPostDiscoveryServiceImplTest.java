package com.example.matching.service.post;

import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.impl.EmergingPostDiscoveryServiceImpl;
import com.example.matching.service.post.support.PmiCommunityDetector;
import com.example.matching.service.rag.KnowledgeDocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmergingPostDiscoveryServiceImplTest {

    private static final String GOVERNED_ABILITY_A = "需求洞察";
    private static final String GOVERNED_ABILITY_B = "供应链协同";

    /**
     * 能力词表必须来自已治理的岗位能力模型，而不是硬编码技术词白名单。
     *
     * 回归背景：原先资料解析只认约 30 个硬编码技术词（Java / Python / Kubernetes…），
     * 上传一篇写得再好的行业白皮书，只要正文没出现这些词就整篇被丢弃，
     * 页面表现为「上传成功、索引完成，但自动发现候选数 0」。
     * 这里刻意使用**不在**硬编码词表里的中文能力名，证明词表确实换源了。
     */
    @Test
    void oneIndexedDocumentProducesObservationCandidateUsingGovernedVocabulary() {
        PostAbilityModelMapper postAbilityMapper = mock(PostAbilityModelMapper.class);
        EmergingPostDiscoveryServiceImpl service = service(
                List.of(), List.of(),
                List.of(abilityModel(1L, GOVERNED_ABILITY_A), abilityModel(1L, GOVERNED_ABILITY_B)),
                List.of(document(11L, "OFFICIAL_POLICY", "智能系统工程师职业目录",
                        "岗位需要" + GOVERNED_ABILITY_A + "与" + GOVERNED_ABILITY_B + "能力。")));

        var candidates = service.discoverEmergingPosts(10);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getDiscoveryMode()).isEqualTo("OBSERVATION");
        assertThat(candidates.get(0).getCoreAbilities())
                .containsExactlyInAnyOrder(GOVERNED_ABILITY_A, GOVERNED_ABILITY_B);
        assertThat(candidates.get(0).getSourceTypes()).contains("OFFICIAL_POLICY");
        assertThat(candidates.get(0).getPolicyValidated()).isTrue();
        assertThat(candidates.get(0).getRiskFlags()).contains("MARKET_VALIDATION_MISSING");
    }

    /** 岗位能力模型为空时，词表应回退到能力标签库。 */
    @Test
    void vocabularyFallsBackToAbilityTagCatalog() {
        var tag = new com.example.matching.entity.system.AbilityTag();
        tag.setId(7L);
        tag.setTagName(GOVERNED_ABILITY_A);
        tag.setStatus(1);
        var tag2 = new com.example.matching.entity.system.AbilityTag();
        tag2.setId(8L);
        tag2.setTagName(GOVERNED_ABILITY_B);
        tag2.setStatus(1);

        EmergingPostDiscoveryServiceImpl service = service(
                List.of(), List.of(tag, tag2), List.of(),
                List.of(document(11L, "OFFICIAL_POLICY", "职业目录",
                        GOVERNED_ABILITY_A + "与" + GOVERNED_ABILITY_B + "是核心要求。")));

        var insight = service.getMarketInsight();

        assertThat(insight.getVocabularySource()).isEqualTo("ABILITY_TAG");
        assertThat(insight.getCandidateCount()).isEqualTo(1);
    }

    /**
     * 无候选时必须给出可读的诊断原因，而不是静默返回空数组。
     *
     * 这是本功能最容易让使用者误判的地方：接口 200 + 空列表，看起来像「功能没数据」，
     * 实际上前面每一步（资料未索引 / 正文未命中能力词 / 词表为空）都可能卡住。
     */
    @Test
    void marketInsightExplainsWhyNothingWasFound() {
        EmergingPostDiscoveryServiceImpl service = service(
                List.of(), List.of(),
                List.of(abilityModel(1L, GOVERNED_ABILITY_A), abilityModel(1L, GOVERNED_ABILITY_B)),
                List.of(document(11L, "INDUSTRY_WHITEPAPER", "宏观经济展望",
                        "本报告讨论宏观经济形势与产业政策走向。")));

        var insight = service.getMarketInsight();

        assertThat(insight.getCandidateCount()).isZero();
        assertThat(insight.getIndexedDocumentCount()).isEqualTo(1);
        assertThat(insight.getVocabularySize()).isEqualTo(2);
        assertThat(insight.getVocabularySource()).isEqualTo("POST_ABILITY_MODEL");
        assertThat(insight.getDiagnosticMessage()).contains("未在正文中匹配到任何岗位能力词");
    }

    /** 词表与资料都为空时，诊断信息要指向「先补数据源」而不是含糊的「暂无数据」。 */
    @Test
    void emptyVocabularyAndNoSourcesProduceActionableMessage() {
        EmergingPostDiscoveryServiceImpl service = service(List.of(), List.of(), List.of(), List.of());

        var insight = service.getMarketInsight();

        assertThat(insight.getCandidateCount()).isZero();
        assertThat(insight.getDiagnosticMessage()).contains("暂无可用数据源");
    }

    /**
     * 冷启动兜底：岗位能力模型与标签库都为空时，仍使用内置引导词表识别资料，
     * 并把来源标记为 SEED —— 让使用者知道当前识别能力来自引导词而非治理数据。
     */
    @Test
    void seedVocabularyIsUsedOnlyWhenGovernedCatalogsAreEmpty() {
        EmergingPostDiscoveryServiceImpl service = service(
                List.of(), List.of(), List.of(),
                List.of(document(11L, "OFFICIAL_POLICY", "职业目录", "岗位需要 Java 与 Python。")));

        var candidates = service.discoverEmergingPosts(10);

        assertThat(candidates).hasSize(1);
        assertThat(service.getMarketInsight().getVocabularySource()).isEqualTo("SEED");
    }

    @Test
    void noSourcesReturnsEmptyWithoutBlockingTheCaller() {
        EmergingPostDiscoveryServiceImpl service = service(List.of(), List.of(), List.of(), List.of());

        assertThat(service.discoverEmergingPosts(10)).isEmpty();
    }

    /* ------------------------------ helpers ------------------------------ */

    private EmergingPostDiscoveryServiceImpl service(List<MarketJdData> marketJds,
                                                     List<com.example.matching.entity.system.AbilityTag> tags,
                                                     List<PostAbilityModel> abilityModels,
                                                     List<RagKnowledgeDocument> documents) {
        MarketJdDataMapper marketJdMapper = mock(MarketJdDataMapper.class);
        AbilityTagMapper tagMapper = mock(AbilityTagMapper.class);
        PostAbilityModelMapper postAbilityMapper = mock(PostAbilityModelMapper.class);
        KnowledgeDocumentService knowledgeService = mock(KnowledgeDocumentService.class);
        when(marketJdMapper.selectList(any())).thenReturn(marketJds);
        when(tagMapper.selectList(any())).thenReturn(tags);
        when(postAbilityMapper.selectList(any())).thenReturn(abilityModels);
        when(knowledgeService.listActiveDocumentsByLayers(any(), any(Integer.class))).thenReturn(documents);
        return new EmergingPostDiscoveryServiceImpl(marketJdMapper, tagMapper, postAbilityMapper,
                new ObjectMapper(), new PmiCommunityDetector(), knowledgeService);
    }

    private PostAbilityModel abilityModel(Long postId, String abilityName) {
        PostAbilityModel model = new PostAbilityModel();
        model.setPostId(postId);
        model.setAbilityName(abilityName);
        model.setIsDeleted(0);
        return model;
    }

    private RagKnowledgeDocument document(Long id, String sourceType, String title, String content) {
        RagKnowledgeDocument document = new RagKnowledgeDocument();
        document.setId(id);
        document.setDocStatus("ACTIVE");
        document.setIsDeleted(0);
        document.setLastIndexedTime(LocalDateTime.now());
        document.setUpdatedTime(LocalDateTime.now());
        document.setSourceType(sourceType);
        document.setTitle(title);
        document.setContent(content);
        return document;
    }
}
