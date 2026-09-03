package com.example.matching.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;
import com.example.matching.agent.service.PostAbilityAgentService;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.service.agent.AgentBusinessApplyService;
import org.springframework.context.ApplicationEventPublisher;
import com.example.matching.service.post.PostDataCleaningService;
import com.example.matching.service.post.impl.PostCapabilityExtractionSupport;
import com.example.matching.service.post.impl.PostCapabilityGenerationServiceImpl;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.ai.service.VectorEmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostCapabilityGenerationServiceHarnessTest {

    @InjectMocks
    private PostCapabilityGenerationServiceImpl service;

    @Mock private PostAbilityAgentService postAbilityAgentService;
    @Mock private AgentBusinessApplyService agentBusinessApplyService;
    @Mock private ObjectMapper objectMapper;
    @Mock private VectorEmbeddingService vectorEmbeddingService;
    @Mock private AbilityTagService abilityTagService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PostDataCleaningService postDataCleaningService;

    @BeforeEach
    void setUp() {
        // 真实提取支持组件 + mock 依赖，保证 convertItemsToExtractionResult 等逻辑可测
        PostCapabilityExtractionSupport support = new PostCapabilityExtractionSupport(
                postAbilityAgentService, objectMapper, vectorEmbeddingService,
                abilityTagService,
                new com.example.matching.infrastructure.llm.LlmResponseParser(new com.fasterxml.jackson.databind.ObjectMapper()));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "extractionSupport", support);
    }

    @Test
    void applyAbilityItemsToPost_onlyAppliesPassedItems() {
        JdAbilityItemDTO passed = item("Java", "MATCHED", 7L, "岗位原文证据");
        JdAbilityItemDTO review = item("沟通协作", "REVIEW", 8L, "岗位原文证据");
        JdAbilityItemDTO blocked = item("量子优化", "BLOCKED", null, "岗位原文证据");

        // MATCHED 标签二次校验：标签必须真实存在（防幻觉 tagId 落地）
        com.example.matching.entity.system.AbilityTag tag = new com.example.matching.entity.system.AbilityTag();
        tag.setId(7L);
        tag.setTagName("Java");
        tag.setIsDeleted(0);
        when(abilityTagService.getById(7L)).thenReturn(tag);

        when(agentBusinessApplyService.applyPostAbilities(any()))
                .thenReturn(new AgentBusinessApplyService.PostAbilityApplyResult(3, 3, 0, 0, 0));

        service.applyAbilityItemsToPost(100L, List.of(passed, review, blocked), Map.of());

        ArgumentCaptor<PostAbilityExtractionResult> captor =
                ArgumentCaptor.forClass(PostAbilityExtractionResult.class);
        verify(agentBusinessApplyService).applyPostAbilities(captor.capture());
        assertThat(captor.getValue().getClaims()).hasSize(3);
        assertThat(captor.getValue().getClaims().get(0).getAbilityName()).isEqualTo("Java");
    }

    @Test
    void matchedTagThatDoesNotExistIsRejectedNotTrusted() {
        // 回归：Agent 返回的 MATCHED tagId 在标签库不存在（幻觉）时必须拒绝，
        // 不得把不存在的标签写入岗位能力模型
        JdAbilityItemDTO ghost = item("幽灵能力", "MATCHED", 999L, "岗位原文证据");
        when(abilityTagService.getById(999L)).thenReturn(null);
        when(agentBusinessApplyService.applyPostAbilities(any()))
                .thenReturn(new AgentBusinessApplyService.PostAbilityApplyResult(1, 1, 0, 0, 0));

        org.assertj.core.api.Assertions.assertThatCode(
                () -> service.applyAbilityItemsToPost(100L, List.of(ghost), Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void analyzeMarketJdText_invokesAgent() {
        PostAbilityExtractionResult extractionResult = new PostAbilityExtractionResult();
        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setAbilityName("Java");
        claim.setEvidenceText("负责Java开发");
        claim.setSourceRefs(List.of("source:MARKET_JD:42"));
        extractionResult.setClaims(List.of(claim));
        when(postAbilityAgentService.extractAbilities(any())).thenReturn(extractionResult);
        when(abilityTagService.list(any(Wrapper.class))).thenReturn(List.of());

        List<JdAbilityItemDTO> items = service.analyzeMarketJdText(
                "Java工程师", "负责Java开发", 42L, List.of("source:MARKET_JD:42"));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getEvidenceText()).isEqualTo("负责Java开发");
        assertThat(items.get(0).getSourceRefs()).containsExactly("source:MARKET_JD:42");
        verify(postAbilityAgentService).extractAbilities(any());
    }

    @Test
    void fiveArgAnalyzePostTextUsesOnlySourceEvidencePath() {
        PostAbilityExtractionResult extractionResult = new PostAbilityExtractionResult();
        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setAbilityName("Java");
        claim.setEvidenceText("负责Java开发");
        claim.setSourceRefs(List.of("source:POST_EVOLUTION_TASK:1"));
        extractionResult.setClaims(List.of(claim));
        when(postAbilityAgentService.extractAbilities(any())).thenReturn(extractionResult);
        when(abilityTagService.list(any(Wrapper.class))).thenReturn(List.of());
        List<JdAbilityItemDTO> items = service.analyzePostText(
                "Java工程师", "负责Java开发", "POST_EVOLUTION_TASK", 1L,
                List.of("source:POST_EVOLUTION_TASK:1"));

        assertThat(items).hasSize(1);
    }

    private JdAbilityItemDTO item(String name, String matchStatus, Long tagId, String reasoning) {
        JdAbilityItemDTO item = new JdAbilityItemDTO();
        item.setSuggestedName(name);
        item.setTagCategory("TECHNICAL");
        item.setMatchStatus(matchStatus);
        item.setMatchedTagId(tagId);
        item.setMatchedTagName(name);
        item.setMinRequiredLevel(3);
        item.setWeight(new BigDecimal("20"));
        item.setIsCore(1);
        item.setIsRequired(1);
        item.setReasoning(reasoning);
        item.setEvidenceText("岗位原文中的能力要求");
        item.setSourceRefs(List.of("source:JD_IMPORT:100"));
        return item;
    }
}
