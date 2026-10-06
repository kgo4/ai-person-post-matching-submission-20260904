package com.example.matching.service.post.impl;

import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityExtractRequest;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;
import com.example.matching.agent.service.PostAbilityAgentService;
import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.example.matching.service.system.AbilityTagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PostCapabilityExtractionSupport 单元测试。
 * <p>
 * 验证 extractAbilitiesViaAgent 是唯一应用门面：恰好一次委托给
 * PostAbilityAgentService.extractAbilities，请求字段完整透传。
 */
@ExtendWith(MockitoExtension.class)
class PostCapabilityExtractionSupportTest {

    @Mock private PostAbilityAgentService postAbilityAgentService;
    @Mock private VectorEmbeddingService vectorEmbeddingService;
    @Mock private AbilityTagService abilityTagService;
    @Mock private LlmResponseParser llmResponseParser;

    private PostCapabilityExtractionSupport support() {
        return new PostCapabilityExtractionSupport(
                postAbilityAgentService, new ObjectMapper(), vectorEmbeddingService,
                abilityTagService, llmResponseParser);
    }

    @Test
    @DisplayName("extractAbilitiesViaAgent 恰好一次委托 Agent 并透传请求字段")
    void extractAbilitiesViaAgentDelegatesExactlyOnce() {
        PostAbilityExtractionResult agentResult = new PostAbilityExtractionResult();
        agentResult.setSummary("摘要");
        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setAbilityName("Java");
        claim.setRequiredLevel(4);
        agentResult.setClaims(List.of(claim));
        when(postAbilityAgentService.extractAbilities(any())).thenReturn(agentResult);

        PostAbilityExtractionResult result = support().extractAbilitiesViaAgent(
                "Java Developer", "负责Java模块设计", "JD_IMPORT", 42L,
                List.of("source:JD_IMPORT:42"));

        ArgumentCaptor<PostAbilityExtractRequest> captor = ArgumentCaptor.forClass(PostAbilityExtractRequest.class);
        verify(postAbilityAgentService, times(1)).extractAbilities(captor.capture());
        PostAbilityExtractRequest request = captor.getValue();
        assertThat(request.getSourceType()).isEqualTo("JD_IMPORT");
        assertThat(request.getSourceRefId()).isEqualTo(42L);
        assertThat(request.getPostName()).isEqualTo("Java Developer");
        assertThat(request.getSourceText()).isEqualTo("负责Java模块设计");
        assertThat(request.getEvidenceText()).isEqualTo("负责Java模块设计");
        assertThat(request.getSourceRefs()).containsExactly("source:JD_IMPORT:42");
        assertThat(result.getSummary()).isEqualTo("摘要");
        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getAbilityName()).isEqualTo("Java");
    }

    @Test
    @DisplayName("Agent 返回空结果时门面原样透传，不自行解析第二套 schema")
    void extractAbilitiesViaAgentPassesThroughEmptyResult() {
        PostAbilityExtractionResult empty = new PostAbilityExtractionResult();
        empty.setClaims(List.of());
        when(postAbilityAgentService.extractAbilities(any())).thenReturn(empty);

        PostAbilityExtractionResult result = support().extractAbilitiesViaAgent(
                "Java Developer", "text", "JD_IMPORT", null, List.of());

        assertThat(result.getClaims()).isEmpty();
        verify(postAbilityAgentService, times(1)).extractAbilities(any());
    }

    // ==================== Task6：候选/拒绝分流回归 ====================

    private com.example.matching.dto.post.JdAbilityItemDTO item(String name, String matchStatus, String reasoning) {
        com.example.matching.dto.post.JdAbilityItemDTO item = new com.example.matching.dto.post.JdAbilityItemDTO();
        item.setSuggestedName(name);
        item.setMatchStatus(matchStatus);
        item.setMinRequiredLevel(3);
        item.setWeight(new java.math.BigDecimal("0.5"));
        item.setIsCore(1);
        item.setIsRequired(1);
        item.setReasoning(reasoning);
        item.setEvidenceText(reasoning);
        return item;
    }

    @Test
    @DisplayName("未知可信能力进入岗位画像和独立标签候选池，不创建正式标签")
    void unknownTrustedAbilityEntersProfileAndCandidatePool() {
        com.example.matching.dto.post.JdAbilityItemDTO it =
                item("Rust高性能并发编程", "NEW", "使用 Rust 编写了高并发网关服务");
        it.setTechStack("Rust");

        PostAbilityExtractionResult result = support().convertItemsToExtractionResult(
                7L, List.of(it), null, "岗位文本：使用 Rust 编写了高并发网关服务");

        assertThat(result.getFormalCount()).isEqualTo(1);
        assertThat(result.getPendingCount()).isEqualTo(1);
        assertThat(result.getRejectedCount()).isZero();
        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getAbilityTagId()).isNull();
        assertThat(result.getClaims().get(0).getAbilityName()).isEqualTo("Rust高性能并发编程");
        assertThat(result.getClaims().get(0).getTechStack()).isEqualTo("Rust");
    }

    @Test
    @DisplayName("未知待审标签不阻塞有原文证据的岗位能力画像")
    void pendingTagDoesNotBlockProfileClaim() {
        com.example.matching.dto.post.JdAbilityItemDTO it =
                item("模糊新能力", "NEW", "原文中的相关描述");

        com.example.matching.entity.system.AbilityTagCandidate candidate =
                new com.example.matching.entity.system.AbilityTagCandidate();
        candidate.setId(9001L);
        candidate.setCandidateName("模糊新能力");
        PostAbilityExtractionResult result = support().convertItemsToExtractionResult(
                7L, List.of(it), null, "岗位文本：原文中的相关描述");

        assertThat(result.getFormalCount()).isEqualTo(1);
        assertThat(result.getPendingCount()).isEqualTo(1);
        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getAbilityTagId()).isNull();
        assertThat(result.getDeferredClaims()).hasSize(1);
        assertThat(result.getDeferredClaims().get(0).getAbilityName()).isEqualTo("模糊新能力");
        assertThat(result.getDeferredClaims().get(0).getCandidateId()).isNull();
        assertThat(result.getDeferredClaims().get(0).getAbilityTagId()).isNull();
        assertThat(result.getDeferredClaims().get(0).getExtractReason()).contains("SOURCE_VALIDATED_UNTAGGED_ABILITY");
    }

    @Test
    @DisplayName("标签候选不再通过 Harness，原文可信能力仍进入岗位画像")
    void sourceValidatedAbilityDoesNotEnterHarness() {
        com.example.matching.dto.post.JdAbilityItemDTO it =
                item("编造能力", "NEW", "原文不存在的编造证据");
        it.setEvidenceText("其他内容");
        PostAbilityExtractionResult result = support().convertItemsToExtractionResult(
                7L, List.of(it), null, "岗位文本：其他内容");

        assertThat(result.getRejectedCount()).isZero();
        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getAbilityTagId()).isNull();
        assertThat(result.getDeferredClaims()).hasSize(1);
    }

    @Test
    @DisplayName("原文无法定位的项进入 rejectedClaims，不静默跳过")
    void untrustedItemGoesToRejectedNotSilentlySkipped() {
        com.example.matching.dto.post.JdAbilityItemDTO it =
                item("高危能力", "NEW", "AI 推理，不是原文证据");

        PostAbilityExtractionResult result = support().convertItemsToExtractionResult(
                7L, List.of(it), null, "岗位文本");

        assertThat(result.getRejectedCount()).isEqualTo(1);
        assertThat(result.getClaims()).isEmpty();
        assertThat(result.getRejectedClaims()).hasSize(1);
        assertThat(result.getRejectedClaims().get(0).getAbilityName()).isEqualTo("高危能力");
    }

    @Test
    @DisplayName("缺少原文证据的能力不得进入标签候选或岗位画像")
    void missingEvidenceIsRejectedBeforeCandidateCreation() {
        com.example.matching.dto.post.JdAbilityItemDTO it =
                item("编造能力", "NEW", "AI 推理，不是原文证据");
        it.setEvidenceText(null);

        PostAbilityExtractionResult result = support().convertItemsToExtractionResult(
                7L, List.of(it), null, "岗位文本：负责 Java 服务开发");

        assertThat(result.getClaims()).isEmpty();
        assertThat(result.getDeferredClaims()).isEmpty();
        assertThat(result.getRejectedClaims()).hasSize(1);
        assertThat(result.getRejectedClaims().get(0).getExtractReason()).contains("原文证据");
    }

    @Test
    @DisplayName("有原文证据的能力不因旧幻觉防护提示而被拒绝")
    void evidenceBackedAbilityIsNotRejectedByLegacyHallucinationMarker() {
        com.example.matching.dto.post.JdAbilityItemDTO it =
                item("Java", "MATCHED", "岗位原文证据");
        it.setEvidenceText("岗位原文证据");
        it.setMatchedTagId(10L);
        AbilityTag tag = new AbilityTag();
        tag.setId(10L);
        tag.setTagName("Java");
        tag.setIsDeleted(0);
        when(abilityTagService.getById(10L)).thenReturn(tag);

        PostAbilityExtractionResult result = support().convertItemsToExtractionResult(
                7L, List.of(it), null, "岗位原文证据");

        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getRejectedClaims()).isEmpty();
    }

    // ==================== Task1：证据端到端保留 + 标签ID真实性校验 ====================

    @Test
    @DisplayName("Task1: 证据字段与启用标签ID完整复制到 DTO，且 MATCHED 仅当标签真实存在")
    void evidenceFieldsAndEnabledTagIdReachDto() {
        AbilityTag enabledTag = new AbilityTag();
        enabledTag.setId(10L);
        enabledTag.setTagName("Java并发编程");
        enabledTag.setStatus(1);

        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setAbilityName("Java并发编程");
        claim.setConfidenceScore(new BigDecimal("87.50"));
        claim.setEvidenceText("负责高并发模块设计");
        claim.setEvidenceAnchor("高并发");
        claim.setAbilityType("TECHNICAL");
        claim.setEvidenceStart(12);
        claim.setEvidenceEnd(30);
        claim.setSourceRefs(List.of("source:MARKET_JD:42", "platform:BOSS"));
        claim.setAbilityTagId(10L);
        PostAbilityExtractionResult result = new PostAbilityExtractionResult();
        result.setClaims(List.of(claim));

        List<JdAbilityItemDTO> items = support().convertClaimsToItems(result, List.of(enabledTag));

        assertThat(items).hasSize(1);
        JdAbilityItemDTO item = items.get(0);
        assertThat(item.getConfidenceScore()).isEqualByComparingTo("87.50");
        assertThat(item.getEvidenceText()).isEqualTo("负责高并发模块设计");
        assertThat(item.getEvidenceAnchor()).isEqualTo("高并发");
        assertThat(item.getAbilityType()).isEqualTo("TECHNICAL");
        assertThat(item.getEvidenceStart()).isEqualTo(12);
        assertThat(item.getEvidenceEnd()).isEqualTo(30);
        assertThat(item.getSourceRefs()).containsExactly("source:MARKET_JD:42", "platform:BOSS");
        assertThat(item.getMatchStatus()).isEqualTo("MATCHED");
        assertThat(item.getMatchedTagId()).isEqualTo(10L);
        assertThat(item.getMatchedTagName()).isEqualTo("Java并发编程");
    }

    @Test
    @DisplayName("Task1: 不存在的标签ID不得成为正式标签，回退为 NEW 且 matchedTagId 为空")
    void nonexistentTagIdFallsBackToNew() {
        AbilityTag enabledTag = new AbilityTag();
        enabledTag.setId(10L);
        enabledTag.setTagName("Java并发编程");
        enabledTag.setStatus(1);

        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setAbilityName("编造能力");
        claim.setEvidenceText("证据");
        claim.setSourceRefs(List.of("source:MARKET_JD:42"));
        claim.setAbilityTagId(999L); // 不存在于启用标签列表
        PostAbilityExtractionResult result = new PostAbilityExtractionResult();
        result.setClaims(List.of(claim));

        List<JdAbilityItemDTO> items = support().convertClaimsToItems(result, List.of(enabledTag));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getMatchStatus()).isEqualTo("NEW");
        assertThat(items.get(0).getMatchedTagId()).isNull();
        assertThat(items.get(0).getSimilarityScore()).isNull();
        // 证据仍应保留，供后续准入门核验
        assertThat(items.get(0).getEvidenceText()).isEqualTo("证据");
        assertThat(items.get(0).getSourceRefs()).containsExactly("source:MARKET_JD:42");
    }
}
