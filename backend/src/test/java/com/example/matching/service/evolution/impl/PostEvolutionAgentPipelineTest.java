package com.example.matching.service.evolution.impl;

import com.example.matching.agent.dto.PostEvolutionAiResult;
import com.example.matching.agent.lc4j.PostEvolutionAiService;
import com.example.matching.dto.evolution.ExternalTrendResourceDTO;
import com.example.matching.dto.evolution.PostEvolutionAgentRequest;
import com.example.matching.dto.evolution.PostEvolutionAgentResult;
import com.example.matching.dto.evolution.PostEvolutionAgentResult.HarnessSummary;
import com.example.matching.dto.evolution.PostEvolutionAgentResult.PostEvolutionChangeProposal;
import com.example.matching.entity.evolution.PostEvolutionChangeItem;
import com.example.matching.entity.evolution.PostEvolutionEvidence;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.infrastructure.llm.EnterpriseChatLanguageModel;
import com.example.matching.integration.zhihu.ZhihuApiProperties;
import com.example.matching.integration.zhihu.ZhihuSearchClient;
import com.example.matching.integration.zhihu.ZhihuSearchItem;
import com.example.matching.integration.zhihu.ZhihuSearchResponse;
import com.example.matching.mapper.evolution.PostEvolutionChangeItemMapper;
import com.example.matching.mapper.evolution.PostEvolutionEvidenceMapper;
import com.example.matching.mapper.evolution.PostEvolutionTaskMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.service.evolution.EvolutionHarnessOrchestrator;
import com.example.matching.service.evolution.ExternalResourceCleaningService;
import com.example.matching.service.evolution.PostEvolutionKnowledgeRetrievalService;
import com.example.matching.service.evolution.PostEvolutionKnowledgeRetrievalService.RetrievalResult;
import com.example.matching.service.evolution.PostEvolutionSignalService;
import com.example.matching.service.evolution.PostEvolutionSignalService.EvolutionSignal;
import com.example.matching.service.evolution.support.EvolutionAbilityTagCatalog;
import com.example.matching.service.evolution.support.EvolutionAbilityTagResolver;
import com.example.matching.service.evolution.support.ResolvedEvolutionAbility;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * {@link PostEvolutionAgentPipeline} 单元测试。
 *
 * <p>覆盖检索、关键词、信号生成与聚合、AI 建议校验（防幻觉）/规则回退、
 * 证据落库等 public 入口的正常路径与降级分支。LLM 与外部客户端全部 mock。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostEvolutionAgentPipelineTest {

    @Mock private PostEvolutionTaskMapper taskMapper;
    @Mock private PostEvolutionChangeItemMapper changeItemMapper;
    @Mock private PostEvolutionEvidenceMapper evidenceMapper;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;
    @Mock private PostEvolutionKnowledgeRetrievalService knowledgeRetrievalService;
    @Mock private PostEvolutionSignalService signalService;
    @Mock private EvolutionHarnessOrchestrator harnessOrchestrator;
    @Mock private EvolutionAbilityTagResolver abilityTagResolver;
    @Mock private ZhihuSearchClient zhihuSearchClient;
    @Mock private ZhihuApiProperties zhihuApiProperties;
    @Mock private ExternalResourceCleaningService externalResourceCleaningService;
    @Mock private EvolutionAbilityTagCatalog abilityTagCatalog;
    @Mock private EnterpriseChatLanguageModel enterpriseChatLanguageModel;
    @SuppressWarnings("unchecked")
    @Mock private ObjectProvider<PostEvolutionAiService> aiServiceProvider;
    @Mock private PostEvolutionAiService aiService;

    private PostEvolutionAgentPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new PostEvolutionAgentPipeline(
                taskMapper, changeItemMapper, evidenceMapper, postAbilityModelMapper,
                knowledgeRetrievalService, signalService, harnessOrchestrator, abilityTagResolver,
                new ObjectMapper(), zhihuSearchClient, zhihuApiProperties, externalResourceCleaningService,
                abilityTagCatalog, enterpriseChatLanguageModel, aiServiceProvider);
        when(aiServiceProvider.getIfAvailable()).thenReturn(null);
        when(abilityTagCatalog.activeTags()).thenReturn(List.of());
        when(externalResourceCleaningService.clean(anyList()))
                .thenAnswer(inv -> new ExternalResourceCleaningService.CleaningResult(inv.getArgument(0), 0, 0, 0));
    }

    // ==================== 工具方法 ====================

    private static PostEvolutionAgentRequest request() {
        PostEvolutionAgentRequest r = new PostEvolutionAgentRequest();
        r.setPostId(100L);
        r.setPostName("Java后端工程师");
        r.setIndustry("信息技术");
        r.setBusinessDomain("软件开发");
        return r;
    }

    private static PostAbilityModel ability(Long id, Long tagId, String name) {
        PostAbilityModel m = new PostAbilityModel();
        m.setId(id);
        m.setPostId(100L);
        m.setTagId(tagId);
        m.setAbilityName(name);
        m.setMinRequiredLevel(3);
        m.setWeight(new BigDecimal("20"));
        m.setIsCore(0);
        return m;
    }

    private static RetrievalResult evidence(String text, String chunkType, String sourceRef) {
        return new RetrievalResult("C1", text, "章节", chunkType, sourceRef, 0.9D, "http://x");
    }

    private static AbilityTag tag(Long id, String name) {
        AbilityTag t = new AbilityTag();
        t.setId(id);
        t.setTagName(name);
        return t;
    }

    // ==================== 检索 ====================

    @Test
    @DisplayName("retrieveIndustryEvidence：行业为空时回退「信息技术」并透传关键词")
    void retrieveIndustryEvidence_defaultIndustry() {
        PostEvolutionAgentRequest r = request();
        r.setIndustry(null);
        when(knowledgeRetrievalService.retrieveIndustryTrends(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(evidence("AI 趋势", "ABILITY_REQUIREMENT", "SRC1")));

        List<RetrievalResult> out = pipeline.retrieveIndustryEvidence(r,
                List.of(ability(1L, 10L, "Java")));

        assertEquals(1, out.size());
    }

    @Test
    @DisplayName("retrieveInternalEvidence：业务域为空时回退「软件开发」")
    void retrieveInternalEvidence_defaultDomain() {
        PostEvolutionAgentRequest r = request();
        r.setBusinessDomain(null);
        when(knowledgeRetrievalService.retrieveBusinessChanges(anyString(), anyList(), anyInt()))
                .thenReturn(Collections.emptyList());

        assertTrue(pipeline.retrieveInternalEvidence(r, List.of()).isEmpty());
    }

    @Test
    @DisplayName("retrieveMarketEvidence：只透传非空 tagId")
    void retrieveMarketEvidence_filtersNullTagIds() {
        when(knowledgeRetrievalService.retrieveMarketEvolutionClues(any(), anyList(), anyInt()))
                .thenReturn(Collections.emptyList());

        assertTrue(pipeline.retrieveMarketEvidence(request(),
                List.of(ability(1L, 10L, "Java"), ability(2L, null, "无标签"))).isEmpty());
    }

    // ==================== 知乎检索分支 ====================

    @Test
    @DisplayName("retrieveZhihuEvidence：includeZhihu=false 时直接返回空")
    void retrieveZhihu_disabled() {
        PostEvolutionAgentRequest r = request();
        r.setIncludeZhihu(Boolean.FALSE);

        assertTrue(pipeline.retrieveZhihuEvidence(r, List.of()).isEmpty());
    }

    @Test
    @DisplayName("retrieveZhihuEvidence：查询为空 / API 不可用 → 返回空")
    void retrieveZhihu_blankQueryAndUnusable() {
        assertTrue(pipeline.retrieveZhihuEvidence(new PostEvolutionAgentRequest(), List.of()).isEmpty());

        when(zhihuApiProperties.isUsable()).thenReturn(false);
        assertTrue(pipeline.retrieveZhihuEvidence(request(), List.of()).isEmpty());
    }

    @Test
    @DisplayName("retrieveZhihuEvidence：客户端抛异常 → 静默降级为空")
    void retrieveZhihu_clientThrows() {
        when(zhihuApiProperties.isUsable()).thenReturn(true);
        when(zhihuSearchClient.search(anyString(), anyInt())).thenThrow(new RuntimeException("timeout"));

        assertTrue(pipeline.retrieveZhihuEvidence(request(), List.of()).isEmpty());
    }

    @Test
    @DisplayName("retrieveZhihuEvidence：正常响应 → 转统一检索结果，跳过空摘要项")
    void retrieveZhihu_success() {
        when(zhihuApiProperties.isUsable()).thenReturn(true);
        ZhihuSearchItem good = new ZhihuSearchItem("标题", "article", "cid-1", "摘要内容",
                "http://z/1", 3, 10);
        ZhihuSearchItem blankSummary = new ZhihuSearchItem("标题2", "article", null, "  ",
                "http://z/2", 0, 0);
        when(zhihuSearchClient.search(anyString(), anyInt())).thenReturn(
                new ZhihuSearchResponse(true, "h", List.of(good, blankSummary), null));

        List<RetrievalResult> out = pipeline.retrieveZhihuEvidence(request(), List.of());

        assertEquals(1, out.size());
        assertEquals("ZHIHU_TREND", out.get(0).chunkType());
        assertTrue(out.get(0).chunkCode().startsWith("ZHIHU:cid-1"));
    }

    @Test
    @DisplayName("retrieveZhihuEvidence：响应为 null 或 items 为 null → 返回空")
    void retrieveZhihu_nullResponse() {
        when(zhihuApiProperties.isUsable()).thenReturn(true);
        when(zhihuSearchClient.search(anyString(), anyInt()))
                .thenReturn(new ZhihuSearchResponse(true, null, null, null));
        assertTrue(pipeline.retrieveZhihuEvidence(request(), List.of()).isEmpty());

        when(zhihuSearchClient.search(anyString(), anyInt())).thenReturn(null);
        assertTrue(pipeline.retrieveZhihuEvidence(request(), List.of()).isEmpty());
    }

    @Test
    @DisplayName("retrieveZhihuEvidence：contentId 为空时用 url 生成稳定 ID")
    void retrieveZhihu_stableIdFromUrl() {
        when(zhihuApiProperties.isUsable()).thenReturn(true);
        ExternalTrendResourceDTO raw = new ExternalTrendResourceDTO("标题", "article", null, "摘要",
                "http://z/9", 1, 1, "ZHIHU_TREND", false, false);
        when(zhihuSearchClient.search(anyString(), anyInt())).thenReturn(
                new ZhihuSearchResponse(true, "h", List.of(new ZhihuSearchItem("标题", "article", null, "摘要",
                        "http://z/9", 1, 1)), null));
        when(externalResourceCleaningService.clean(anyList()))
                .thenReturn(new ExternalResourceCleaningService.CleaningResult(List.of(raw), 0, 0, 0));

        List<RetrievalResult> out = pipeline.retrieveZhihuEvidence(request(), List.of());

        assertEquals(1, out.size());
        assertTrue(out.get(0).chunkCode().startsWith("ZHIHU:"));
        assertFalse(out.get(0).chunkCode().contains("http"));
        assertEquals("ZHIHU_TREND", out.get(0).chunkType());
    }

    // ==================== 关键词 ====================

    @Test
    @DisplayName("buildKeywords：拼接岗位/行业/业务域 + 命中标签名 + 固定词")
    void buildKeywords_full() {
        when(abilityTagCatalog.activeTags()).thenReturn(List.of(
                tag(10L, "Java"), tag(11L, "MySQL"), tag(999L, "未命中")));

        List<String> keywords = pipeline.buildKeywords(request(), List.of(
                ability(1L, 10L, "Java"), ability(2L, 11L, "MySQL")));

        assertTrue(keywords.contains("Java后端工程师"));
        assertTrue(keywords.contains("信息技术"));
        assertTrue(keywords.contains("软件开发"));
        assertTrue(keywords.contains("Java"));
        assertTrue(keywords.contains("MySQL"));
        assertTrue(keywords.contains("岗位要求"));
        assertFalse(keywords.contains("未命中"));
    }

    @Test
    @DisplayName("buildKeywords：currentAbilities=null 且字段为空 → 只保留固定词")
    void buildKeywords_nullAbilities() {
        List<String> keywords = pipeline.buildKeywords(new PostEvolutionAgentRequest(), null);

        assertEquals(List.of("岗位要求", "能力要求", "技能要求"), keywords);
    }

    // ==================== 信号生成 ====================

    @Test
    @DisplayName("generateSignals：三类证据都解析到同一 tagId → 聚合为一条信号")
    void generateSignals_byChunkType() {
        when(abilityTagResolver.resolve(anyString()))
                .thenReturn(new ResolvedEvolutionAbility(10L, "Java", 1.0D));

        List<EvolutionSignal> signals = pipeline.generateSignals(request(),
                List.of(evidence("需要 Java", "ABILITY_REQUIREMENT", "SRC1")),
                List.of(evidence("业务变化", "BUSINESS_CHANGE", "SRC2")),
                List.of(evidence("市场需求", "MARKET_ABILITY_REQUIREMENT", "SRC3")));

        // 三条信号同属 tagId=10，聚合后只剩一条，但 sourceRefs 应累计 3 个
        assertEquals(1, signals.size());
        assertEquals(3, signals.get(0).sourceRefs().size());
    }

    @Test
    @DisplayName("generateSignals：不同 tagId / 无 tagId 的信号各自独立")
    void generateSignals_distinctKeys() {
        when(abilityTagResolver.resolve(anyString()))
                .thenReturn(new ResolvedEvolutionAbility(10L, "Java", 1.0D),
                        new ResolvedEvolutionAbility(20L, "Kafka", 1.0D),
                        new ResolvedEvolutionAbility(null, "Docker", 1.0D));

        List<EvolutionSignal> signals = pipeline.generateSignals(request(),
                List.of(evidence("需要 Java", "ABILITY_REQUIREMENT", "SRC1")),
                List.of(evidence("业务变化", "BUSINESS_CHANGE", "SRC2")),
                List.of(evidence("市场需求", "MARKET_ABILITY_REQUIREMENT", "SRC3")));

        assertEquals(3, signals.size());
    }

    @Test
    @DisplayName("generateSignals：chunkType 不匹配 → 不产生信号")
    void generateSignals_noMatch() {
        List<EvolutionSignal> signals = pipeline.generateSignals(request(),
                List.of(evidence("x", "OTHER", "SRC1")),
                Collections.emptyList(), Collections.emptyList());

        assertTrue(signals.isEmpty());
    }

    @Test
    @DisplayName("generateSignals：同一 tagId 的多条信号被聚合成一条")
    void generateSignals_aggregatesSameTag() {
        when(abilityTagResolver.resolve(anyString()))
                .thenReturn(new ResolvedEvolutionAbility(10L, "Java", 1.0D));

        List<EvolutionSignal> signals = pipeline.generateSignals(request(),
                List.of(evidence("需要 Java A", "ABILITY_REQUIREMENT", "SRC1"),
                        evidence("需要 Java B", "ABILITY_REQUIREMENT", "SRC2")),
                Collections.emptyList(), Collections.emptyList());

        assertEquals(1, signals.size());
        assertEquals(2, signals.get(0).sourceRefs().size());
    }

    @Test
    @DisplayName("generateSignals(5参)：知乎证据命中解析器 → 追加信号")
    void generateSignals_withZhihu_resolved() {
        when(abilityTagResolver.resolve(anyString()))
                .thenReturn(new ResolvedEvolutionAbility(10L, "Java", 1.0D));

        List<EvolutionSignal> signals = pipeline.generateSignals(request(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                List.of(evidence("知乎趋势", "ZHIHU_TREND", "SRC9")),
                List.of(ability(1L, 10L, "Java")));

        assertEquals(1, signals.size());
        assertEquals("ZHIHU_TREND", signals.get(0).signalType());
    }

    @Test
    @DisplayName("generateSignals(5参)：解析器未命中但证据含岗位能力名 → 回退匹配")
    void generateSignals_withZhihu_fallbackByAbilityName() {
        when(abilityTagResolver.resolve(anyString())).thenReturn(null);

        List<EvolutionSignal> signals = pipeline.generateSignals(request(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                List.of(evidence("本文讨论 Java 生态的演进", "ZHIHU_TREND", "SRC9")),
                List.of(ability(1L, 10L, "Java")));

        assertEquals(1, signals.size());
        assertEquals("Java", signals.get(0).abilityName());
    }

    @Test
    @DisplayName("generateSignals(5参)：解析器未命中且无岗位能力匹配 → 丢弃该条")
    void generateSignals_withZhihu_unmatched() {
        when(abilityTagResolver.resolve(anyString())).thenReturn(null);

        List<EvolutionSignal> signals = pipeline.generateSignals(request(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                List.of(evidence("完全无关内容", "ZHIHU_TREND", "SRC9")),
                List.of(ability(1L, 10L, "Java")));

        assertTrue(signals.isEmpty());
    }

    // ==================== createResolvedSignal ====================

    @Test
    @DisplayName("createResolvedSignal：解析命中 → 生成信号；未命中 → empty")
    void createResolvedSignal_bothPaths() {
        RetrievalResult r = evidence("需要 Kafka", "ABILITY_REQUIREMENT", "SRC1");
        when(abilityTagResolver.resolve(anyString())).thenReturn(null);
        assertTrue(pipeline.createResolvedSignal("ABILITY_ADD", "ADD", r, 0.8D).isEmpty());

        when(abilityTagResolver.resolve(anyString()))
                .thenReturn(new ResolvedEvolutionAbility(20L, "Kafka", 1.0D));
        EvolutionSignal signal = pipeline.createResolvedSignal("ABILITY_ADD", "ADD", r, 0.8D).orElseThrow();
        assertEquals("Kafka", signal.abilityName());
        assertEquals(0.9D * 0.8D, signal.supportScore(), 0.0001D);
    }

    // ==================== AI 建议：降级分支 ====================

    @Test
    @DisplayName("generateAiProposals：AI 不可用 → 规则回退 AI_UNAVAILABLE")
    void generateAiProposals_aiUnavailable() {
        PostEvolutionAgentPipeline.ProposalGenerationOutcome outcome = pipeline.generateAiProposals(
                request(), List.of(ability(1L, 10L, "Java")),
                List.of(evidence("e", "ABILITY_REQUIREMENT", "SRC1")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertTrue(outcome.ruleFallback());
        assertEquals("AI_UNAVAILABLE", outcome.fallbackReason());
    }

    @Test
    @DisplayName("generateAiProposals：证据全空 → 规则回退 NO_EVIDENCE")
    void generateAiProposals_noEvidence() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);

        PostEvolutionAgentPipeline.ProposalGenerationOutcome outcome = pipeline.generateAiProposals(
                request(), List.of(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());

        assertTrue(outcome.ruleFallback());
        assertEquals("NO_EVIDENCE", outcome.fallbackReason());
    }

    @Test
    @DisplayName("generateAiProposals：AI 调用抛异常 → 规则回退 AI_CALL_FAILED")
    void generateAiProposals_aiThrows() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(enterpriseChatLanguageModel.getCurrentModelName()).thenReturn("m1");
        when(aiService.analyze(anyString())).thenThrow(new RuntimeException("LLM down"));

        PostEvolutionAgentPipeline.ProposalGenerationOutcome outcome = pipeline.generateAiProposals(
                request(), List.of(ability(1L, 10L, "Java")),
                List.of(evidence("需要 Java", "ABILITY_REQUIREMENT", "SRC1")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertTrue(outcome.ruleFallback());
        assertEquals("AI_CALL_FAILED", outcome.fallbackReason());
    }

    @Test
    @DisplayName("generateAiProposals：AI 返回空建议 → 规则回退 AI_EMPTY_OR_REJECTED")
    void generateAiProposals_aiEmpty() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(aiService.analyze(anyString())).thenReturn(new PostEvolutionAiResult());

        PostEvolutionAgentPipeline.ProposalGenerationOutcome outcome = pipeline.generateAiProposals(
                request(), List.of(ability(1L, 10L, "Java")),
                List.of(evidence("需要 Java", "ABILITY_REQUIREMENT", "SRC1")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertTrue(outcome.ruleFallback());
        assertEquals("AI_EMPTY_OR_REJECTED", outcome.fallbackReason());
    }

    @Test
    @DisplayName("generateAiProposals：AI 返回 null → 规则回退 AI_EMPTY_OR_REJECTED")
    void generateAiProposals_nullResult() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(aiService.analyze(anyString())).thenReturn(null);

        PostEvolutionAgentPipeline.ProposalGenerationOutcome outcome = pipeline.generateAiProposals(
                request(), List.of(ability(1L, 10L, "Java")),
                List.of(evidence("需要 Java", "ABILITY_REQUIREMENT", "SRC1")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertTrue(outcome.ruleFallback());
        assertEquals("AI_EMPTY_OR_REJECTED", outcome.fallbackReason());
    }

    // ==================== AI 建议：校验分支 ====================

    private static PostEvolutionAiResult aiResult(PostEvolutionAiResult.ChangeSuggestion... suggestions) {
        PostEvolutionAiResult result = new PostEvolutionAiResult();
        result.setSuggestions(new ArrayList<>(List.of(suggestions)));
        return result;
    }

    private static PostEvolutionAiResult.ChangeSuggestion suggestion(String ability, String action, Integer ref) {
        PostEvolutionAiResult.ChangeSuggestion s = new PostEvolutionAiResult.ChangeSuggestion();
        s.setAbilityName(ability);
        s.setAction(action);
        s.setEvidenceRef(ref);
        s.setReason("理由");
        return s;
    }

    @Test
    @DisplayName("generateAiProposals：AI 新增建议通过校验 → 走 withAi 合并")
    void generateAiProposals_aiAddAccepted() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(aiService.analyze(anyString())).thenReturn(aiResult(suggestion("Kubernetes", "ADD", 0)));

        PostEvolutionAgentPipeline.ProposalGenerationOutcome outcome = pipeline.generateAiProposals(
                request(), List.of(ability(1L, 10L, "Java")),
                List.of(evidence("需要 Kubernetes 编排能力", "ABILITY_REQUIREMENT", "SRC1")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertFalse(outcome.ruleFallback());
        assertEquals(1, outcome.aiAcceptedSuggestionCount());
        assertEquals("ADD", outcome.proposals().get(0).getChangeType());
    }

    @Test
    @DisplayName("generateAiProposals：未知动作 / 空能力名 / 无效证据编号的建议被丢弃")
    void generateAiProposals_rejectsInvalidSuggestions() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(aiService.analyze(anyString())).thenReturn(aiResult(
                suggestion("Kubernetes", "UNKNOWN_ACTION", 0),
                suggestion(" ", "ADD", 0),
                suggestion(null, "ADD", 0),
                suggestion("Kubernetes", "ADD", 99),
                suggestion("Kubernetes", "ADD", null),
                suggestion("Kubernetes", "", 0)));

        PostEvolutionAgentPipeline.ProposalGenerationOutcome outcome = pipeline.generateAiProposals(
                request(), List.of(ability(1L, 10L, "Java")),
                List.of(evidence("需要 Kubernetes", "ABILITY_REQUIREMENT", "SRC1")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertTrue(outcome.ruleFallback());
        assertEquals(0, outcome.aiAcceptedSuggestionCount());
    }

    @Test
    @DisplayName("AI 建议：ADD 已有能力 / UPDATE_* REMOVE 未知能力 → 全部丢弃")
    void generateAiProposals_actionGuards() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(aiService.analyze(anyString())).thenReturn(aiResult(
                suggestion("Java", "ADD", 0),
                suggestion("NotExisting", "UPDATE_LEVEL", 0),
                suggestion("NotExisting", "UPDATE_WEIGHT", 0),
                suggestion("NotExisting", "UPDATE_CORE", 0),
                suggestion("NotExisting", "REMOVE", 0)));

        PostEvolutionAgentPipeline.ProposalGenerationOutcome outcome = pipeline.generateAiProposals(
                request(), List.of(ability(1L, 10L, "Java")),
                List.of(evidence("需要 Java 与框架", "ABILITY_REQUIREMENT", "SRC1")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertEquals(0, outcome.aiAcceptedSuggestionCount());
    }

    @Test
    @DisplayName("AI 建议：UPDATE_LEVEL 有实际变化 → 接受；无变化 → 跳过")
    void generateAiProposals_updateLevel() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        PostEvolutionAiResult.ChangeSuggestion changed = suggestion("Java", "UPDATE_LEVEL", 0);
        changed.setNewLevel(5);
        when(aiService.analyze(anyString())).thenReturn(aiResult(changed));

        PostEvolutionAgentPipeline.ProposalGenerationOutcome outcome = pipeline.generateAiProposals(
                request(), List.of(ability(1L, 10L, "Java")),
                List.of(evidence("Java 要求提升", "ABILITY_REQUIREMENT", "SRC1")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertEquals(1, outcome.aiAcceptedSuggestionCount());
        assertEquals("UPDATE", outcome.proposals().get(0).getChangeType());
        assertEquals(5, outcome.proposals().get(0).getNewLevel());

        PostEvolutionAiResult.ChangeSuggestion noChange = suggestion("Java", "UPDATE_LEVEL", 0);
        noChange.setNewLevel(3);
        when(aiService.analyze(anyString())).thenReturn(aiResult(noChange));

        assertEquals(0, pipeline.generateAiProposals(request(), List.of(ability(1L, 10L, "Java")),
                List.of(evidence("Java 要求提升", "ABILITY_REQUIREMENT", "SRC1")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList())
                .aiAcceptedSuggestionCount());
    }

    @Test
    @DisplayName("AI 建议：UPDATE_WEIGHT / UPDATE_CORE / REMOVE 在有实际变化时被接受")
    void generateAiProposals_weightCoreRemove() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);

        PostEvolutionAiResult.ChangeSuggestion weight = suggestion("Java", "UPDATE_WEIGHT", 0);
        weight.setNewWeight(new BigDecimal("80"));
        when(aiService.analyze(anyString())).thenReturn(aiResult(weight));
        assertEquals(1, pipeline.generateAiProposals(request(), List.of(ability(1L, 10L, "Java")),
                List.of(evidence("Java 权重上升", "ABILITY_REQUIREMENT", "SRC1")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList())
                .aiAcceptedSuggestionCount());

        PostEvolutionAiResult.ChangeSuggestion core = suggestion("Java", "UPDATE_CORE", 0);
        core.setNewIsCore(1);
        when(aiService.analyze(anyString())).thenReturn(aiResult(core));
        assertEquals(1, pipeline.generateAiProposals(request(), List.of(ability(1L, 10L, "Java")),
                List.of(evidence("Java 成为核心", "ABILITY_REQUIREMENT", "SRC1")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList())
                .aiAcceptedSuggestionCount());

        PostEvolutionAiResult.ChangeSuggestion remove = suggestion("Java", "REMOVE", 0);
        when(aiService.analyze(anyString())).thenReturn(aiResult(remove));
        assertEquals(1, pipeline.generateAiProposals(request(), List.of(ability(1L, 10L, "Java")),
                List.of(evidence("Java 已淘汰", "ABILITY_REQUIREMENT", "SRC1")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList())
                .aiAcceptedSuggestionCount());
    }

    @Test
    @DisplayName("AI 建议：引用片段为空文本 → 丢弃")
    void generateAiProposals_blankEvidence() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(aiService.analyze(anyString())).thenReturn(aiResult(suggestion("Kubernetes", "ADD", 0)));
        RetrievalResult blank = new RetrievalResult("C1", "   ", "章节",
                "ABILITY_REQUIREMENT", "SRC1", 0.9D, null);

        PostEvolutionAgentPipeline.ProposalGenerationOutcome outcome = pipeline.generateAiProposals(
                request(), List.of(ability(1L, 10L, "Java")), List.of(blank),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertEquals(0, outcome.aiAcceptedSuggestionCount());
    }

    @Test
    @DisplayName("AI 建议：1-based 编号误用时可回退到 ref-1 的片段")
    void generateAiProposals_oneBasedRefFallback() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(aiService.analyze(anyString())).thenReturn(aiResult(suggestion("Kubernetes", "ADD", 1)));

        PostEvolutionAgentPipeline.ProposalGenerationOutcome outcome = pipeline.generateAiProposals(
                request(), List.of(),
                List.of(evidence("Kubernetes 编排能力要求", "ABILITY_REQUIREMENT", "SRC1")),
                List.of(evidence("无关内容", "OTHER", "SRC2")),
                Collections.emptyList(), Collections.emptyList());

        assertEquals(1, outcome.aiAcceptedSuggestionCount());
    }

    @Test
    @DisplayName("AI 建议：无现有能力时旧值取默认（等级3/权重0/非核心）")
    void generateAiProposals_defaultsWhenAbilityMissing() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        PostEvolutionAiResult.ChangeSuggestion s = suggestion("Kubernetes", "ADD", 0);
        s.setNewLevel(99);
        s.setNewWeight(new BigDecimal("-5"));
        when(aiService.analyze(anyString())).thenReturn(aiResult(s));

        PostEvolutionAgentPipeline.ProposalGenerationOutcome outcome = pipeline.generateAiProposals(
                request(), Collections.emptyList(),
                List.of(evidence("需要 Kubernetes", "ABILITY_REQUIREMENT", "SRC1")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertEquals(1, outcome.aiAcceptedSuggestionCount());
        PostEvolutionChangeProposal p = outcome.proposals().get(0);
        assertEquals(5, p.getNewLevel());
        assertEquals(0, BigDecimal.ZERO.compareTo(p.getNewWeight()));
    }

    // ==================== compareWithCurrentModel / 风险 ====================

    @Test
    @DisplayName("compareWithCurrentModel：命中现有能力 → UPDATE；未命中 → ADD")
    void compareWithCurrentModel_bothPaths() {
        EvolutionSignal levelUp = new EvolutionSignal("ABILITY_LEVEL_UP", "Java", 10L, "UPDATE",
                "证据", List.of("SRC1"), 0.9D, 0.9D);
        EvolutionSignal newAbility = new EvolutionSignal("ABILITY_ADD", "Kafka", null, "ADD",
                "证据", List.of("SRC2"), 0.8D, 0.8D);

        List<PostEvolutionChangeProposal> proposals = pipeline.compareWithCurrentModel(
                List.of(levelUp, newAbility), List.of(ability(1L, 10L, "Java")));

        assertEquals(2, proposals.size());
        assertEquals("UPDATE", proposals.get(0).getChangeType());
        assertEquals(4, proposals.get(0).getNewLevel());
        assertEquals("ADD", proposals.get(1).getChangeType());
    }

    @Test
    @DisplayName("compareWithCurrentModel：ABILITY_WEIGHT_UP / ABILITY_CORE_CHANGE 分支")
    void compareWithCurrentModel_weightAndCore() {
        EvolutionSignal weightUp = new EvolutionSignal("ABILITY_WEIGHT_UP", "Java", 10L, "UPDATE",
                "证据", List.of("SRC1"), 0.9D, 0.9D);
        EvolutionSignal coreChange = new EvolutionSignal("ABILITY_CORE_CHANGE", "Java", 10L, "UPDATE",
                "证据", List.of("SRC1"), 0.9D, 0.9D);

        List<PostEvolutionChangeProposal> proposals = pipeline.compareWithCurrentModel(
                List.of(weightUp, coreChange), List.of(ability(1L, 10L, "Java")));

        assertEquals(2, proposals.size());
        assertEquals(1, proposals.get(1).getNewIsCore());
    }

    @Test
    @DisplayName("compareWithCurrentModel：signalType 未知且无有效变化 → 跳过")
    void compareWithCurrentModel_noEffectiveChange() {
        EvolutionSignal noChange = new EvolutionSignal("OTHER", "Java", 10L, "UPDATE",
                "证据", List.of("SRC1"), 0.9D, 0.9D);

        assertTrue(pipeline.compareWithCurrentModel(List.of(noChange),
                List.of(ability(1L, 10L, "Java"))).isEmpty());
    }

    @Test
    @DisplayName("compareWithCurrentModel：tagId=null 的能力不进入映射（信号视为新增）")
    void compareWithCurrentModel_nullTagId() {
        EvolutionSignal signal = new EvolutionSignal("ABILITY_LEVEL_UP", "Java", null, "UPDATE",
                "证据", List.of("SRC1"), 0.9D, 0.9D);

        List<PostEvolutionChangeProposal> proposals = pipeline.compareWithCurrentModel(
                List.of(signal), List.of(ability(1L, null, "Java")));

        assertEquals(1, proposals.size());
        assertEquals("ADD", proposals.get(0).getChangeType());
    }

    @Test
    @DisplayName("calculateRiskLevel：null→HIGH，≥80→LOW，≥60→MEDIUM，其余→HIGH")
    void calculateRiskLevel_bands() {
        PostEvolutionChangeProposal p = new PostEvolutionChangeProposal();
        assertEquals("HIGH", pipeline.calculateRiskLevel(p));
        p.setConfidenceScore(90D);
        assertEquals("LOW", pipeline.calculateRiskLevel(p));
        p.setConfidenceScore(70D);
        assertEquals("MEDIUM", pipeline.calculateRiskLevel(p));
        p.setConfidenceScore(10D);
        assertEquals("HIGH", pipeline.calculateRiskLevel(p));
    }

    // ==================== 证据构建 ====================

    @Test
    @DisplayName("createEvidences：三类证据分别打标来源")
    void createEvidences_allSources() {
        List<PostEvolutionEvidence> list = pipeline.createEvidences(
                List.of(evidence("行业", "ABILITY_REQUIREMENT", "SRC1")),
                List.of(evidence("内部", "BUSINESS_CHANGE", "SRC2")),
                List.of(evidence("市场", "MARKET_ABILITY_REQUIREMENT", "SRC3")));

        assertEquals(3, list.size());
        assertEquals("INDUSTRY_WHITEPAPER", list.get(0).getSourceType());
        assertEquals("CLOUD_KNOWLEDGE_INTERNAL", list.get(1).getSourceType());
        assertEquals("MARKET_JD", list.get(2).getSourceType());
    }

    @Test
    @DisplayName("createEvidencesWithZhihu：知乎证据使用 url，缺失时回退 sourceRef")
    void createEvidencesWithZhihu_urlFallback() {
        RetrievalResult withUrl = evidence("知乎1", "ZHIHU_TREND", "SRC1");
        RetrievalResult noUrl = new RetrievalResult("C2", "知乎2", "标题", "ZHIHU_TREND", "SRC2", null);

        List<PostEvolutionEvidence> list = pipeline.createEvidencesWithZhihu(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                List.of(withUrl, noUrl));

        assertEquals(2, list.size());
        assertEquals("http://x", list.get(0).getSourceUrl());
        assertEquals("SRC2", list.get(1).getSourceUrl());
        assertEquals("ZHIHU_TREND", list.get(0).getSourceType());
        assertEquals(0, BigDecimal.ZERO.compareTo(list.get(1).getSimilarityScore()));
    }

    // ==================== 保存变更项 ====================

    @Test
    @DisplayName("saveChangeItems：proposals 为空或 null → 返回 0")
    void saveChangeItems_empty() {
        assertEquals(0, pipeline.saveChangeItems(1L, Collections.emptyList(), Collections.emptyList()));
        assertEquals(0, pipeline.saveChangeItems(1L, null, null));
    }

    @Test
    @DisplayName("saveChangeItems：无关联证据 → 跳过不落库")
    void saveChangeItems_noRelatedEvidence() {
        PostEvolutionChangeProposal p = new PostEvolutionChangeProposal();
        p.setAbilityName("Java");
        p.setChangeType("ADD");
        p.setSourceRefs(List.of("SRC_NOT_EXIST"));
        p.setEvidenceText("不存在的证据");

        assertEquals(0, pipeline.saveChangeItems(1L, List.of(p), Collections.emptyList()));
    }

    @Test
    @DisplayName("saveChangeItems：sourceRef 命中 → 落库并回填 evidence.changeItemId")
    void saveChangeItems_savesByApiSourceRef() {
        doAnswer(inv -> {
            PostEvolutionChangeItem item = inv.getArgument(0);
            if (item.getId() == null) item.setId(77L);
            return 1;
        }).when(changeItemMapper).insert(any(PostEvolutionChangeItem.class));

        PostEvolutionEvidence persisted = new PostEvolutionEvidence();
        persisted.setId(5L);
        persisted.setSourceRef("SRC1");
        persisted.setEvidenceText("需要 Java");

        PostEvolutionChangeProposal p = new PostEvolutionChangeProposal();
        p.setAbilityName("Java");
        p.setChangeType("ADD");
        p.setSourceRefs(List.of("SRC1"));
        p.setEvidenceText("需要 Java");
        p.setConfidenceScore(80D);
        p.setSupportScore(70D);
        p.setNewLevel(2);

        int count = pipeline.saveChangeItems(1L, List.of(p), List.of(persisted));

        assertEquals(1, count);
        assertEquals(77L, persisted.getChangeItemId());
    }

    @Test
    @DisplayName("saveChangeItems：harnessDecision=BLOCK → 跳过")
    void saveChangeItems_blockSkipped() {
        PostEvolutionChangeProposal p = new PostEvolutionChangeProposal();
        p.setAbilityName("Java");
        p.setChangeType("ADD");
        p.setSourceRefs(List.of("SRC1"));
        p.setHarnessDecision("BLOCK");

        PostEvolutionEvidence persisted = new PostEvolutionEvidence();
        persisted.setSourceRef("SRC1");
        persisted.setEvidenceText("需要 Java");

        assertEquals(0, pipeline.saveChangeItems(1L, List.of(p), List.of(persisted)));
    }

    @Test
    @DisplayName("saveChangeItems：证据文本互相包含 → 视为关联")
    void saveChangeItems_overlappingEvidence() {
        doAnswer(inv -> {
            PostEvolutionChangeItem item = inv.getArgument(0);
            if (item.getId() == null) item.setId(88L);
            return 1;
        }).when(changeItemMapper).insert(any(PostEvolutionChangeItem.class));

        PostEvolutionEvidence persisted = new PostEvolutionEvidence();
        persisted.setSourceRef("OTHER");
        persisted.setEvidenceText("需要 Java 与 Spring");

        PostEvolutionChangeProposal p = new PostEvolutionChangeProposal();
        p.setAbilityName("Java");
        p.setChangeType("UPDATE");
        p.setOldLevel(3);
        p.setNewLevel(4);
        p.setSourceRefs(null);
        p.setEvidenceText("需要 Java 与 Spring 能力");

        assertEquals(1, pipeline.saveChangeItems(1L, List.of(p), List.of(persisted)));
    }

    // ==================== mapChangeType ====================

    @Test
    @DisplayName("mapChangeType：ADD/REMOVE/UPDATE 各变体与 null 的映射")
    void mapChangeType_allBranches() {
        assertEquals("ADDED", pipeline.mapChangeType(ofType("ADD")));
        assertEquals("REMOVED", pipeline.mapChangeType(ofType("REMOVE")));
        assertEquals("ADDED", pipeline.mapChangeType(ofType(null)));

        // 等级变化优先
        assertEquals("UPDATED_LEVEL", pipeline.mapChangeType(
                proposal("UPDATE", 3, 4, new BigDecimal("20"), new BigDecimal("30"), 0, 1)));
        // 权重变化
        assertEquals("UPDATED_WEIGHT", pipeline.mapChangeType(
                proposal("UPDATE", 3, 3, new BigDecimal("20"), new BigDecimal("30"), 0, 0)));
        // 核心变化
        assertEquals("UPDATED_CORE", pipeline.mapChangeType(
                proposal("UPDATE", 3, 3, new BigDecimal("20"), new BigDecimal("20"), 0, 1)));
        // 无变化兜底
        assertEquals("UPDATED_LEVEL", pipeline.mapChangeType(
                proposal("UPDATE", 3, 3, new BigDecimal("20"), new BigDecimal("20"), 0, 0)));
        // 未知类型原样返回
        assertEquals("CUSTOM", pipeline.mapChangeType(ofType("CUSTOM")));
    }

    private static PostEvolutionChangeProposal proposal(String changeType, Integer oldLevel, Integer newLevel,
                                                        BigDecimal oldWeight, BigDecimal newWeight,
                                                        Integer oldCore, Integer newCore) {
        PostEvolutionChangeProposal p = new PostEvolutionChangeProposal();
        p.setChangeType(changeType);
        p.setOldLevel(oldLevel);
        p.setNewLevel(newLevel);
        p.setOldWeight(oldWeight);
        p.setNewWeight(newWeight);
        p.setOldIsCore(oldCore);
        p.setNewIsCore(newCore);
        return p;
    }

    /** 仅关注 changeType 的便捷重载 */
    private static PostEvolutionChangeProposal ofType(String changeType) {
        return proposal(changeType, null, null, null, null, null, null);
    }

    // ==================== generateSummary ====================

    @Test
    @DisplayName("generateSummary：带 Harness 摘要 / 不带 Harness 摘要 两种输出")
    void generateSummary_bothPaths() {
        PostEvolutionAgentResult result = new PostEvolutionAgentResult();
        result.setSignals(List.of());
        result.setProposals(List.of());
        String plain = pipeline.generateSummary(result);
        assertTrue(plain.contains("生成信号 0 个"));
        assertFalse(plain.contains("Harness"));

        HarnessSummary summary = new HarnessSummary();
        summary.setPass(1);
        summary.setReview(2);
        summary.setBlock(3);
        result.setHarnessSummary(summary);
        String withHarness = pipeline.generateSummary(result);
        assertTrue(withHarness.contains("通过 1"));
        assertTrue(withHarness.contains("拒绝 3"));
    }

    @Test
    @DisplayName("generateSummary：signals/proposals 为 null 时按 0 处理")
    void generateSummary_nullCollections() {
        assertTrue(pipeline.generateSummary(new PostEvolutionAgentResult()).contains("生成信号 0 个"));
    }
}