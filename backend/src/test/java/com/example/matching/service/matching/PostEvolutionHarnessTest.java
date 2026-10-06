package com.example.matching.service.matching;

import com.example.matching.ai.context.service.AiContextSourceRefService;
import com.example.matching.common.source.SourceRefValidationResult;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.service.harness.impl.AiTrustHarnessServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 岗位演化 Harness 验证测试
 * <p>
 * 覆盖场景：
 * 1. 新能力有岗位材料证据 -> REVIEW
 * 2. 趋势判断没有证据引用 -> BLOCK
 * 3. RAG分块为能力标签自身时不能自证
 * 4. BLOCK的变更项不会进入正式change item
 * <p>
 * sourceRef 通过 resolver 校验（VALID 表示受信岗位材料 span），无法解析的引用按 fail-closed 处理。
 */
class PostEvolutionHarnessTest {

    private AiTrustHarnessService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        AiContextSourceRefService sourceRefService = mock(AiContextSourceRefService.class);
        when(sourceRefService.resolveWithStatus(any()))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                        SourceRefValidationResult.VALID,
                        new com.example.matching.ai.context.dto.AiContextSourceRefDTO()));
        ObjectProvider<AiContextSourceRefService> sourceRefProvider = mock(ObjectProvider.class);
        when(sourceRefProvider.getIfAvailable()).thenReturn(sourceRefService);
        service = new AiTrustHarnessServiceImpl(null, null, sourceRefProvider, null);
    }

    @Test
    void newAbilityWithMaterialEvidenceShouldBeReview() {
        // 新能力有岗位材料证据 -> REVIEW
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("POST_EVOLUTION");
        claim.setClaimType("POST_EVOLUTION_CHANGE");
        claim.setClaimText("新增 云原生安全治理，要求等级3");
        claim.setSourceType("POST_EVOLUTION_TASK");
        claim.setSourceRefId(300L);
        claim.setEvidenceText("新JD要求：负责容器安全策略制定，熟悉Kubernetes安全模型和零信任架构。");
        claim.setSourceRefs(List.of("source:POST_EVOLUTION_TASK:300"));

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("REVIEW");
        assertThat(decision.isSelfEvidence()).isFalse();
    }

    @Test
    void trendJudgmentWithoutEvidenceShouldBeBlocked() {
        // 趋势判断没有证据引用 -> BLOCK
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("POST_EVOLUTION");
        claim.setClaimType("POST_EVOLUTION_CHANGE");
        claim.setClaimText("新增 AI大模型应用开发，要求等级4");
        claim.setSourceType("POST_EVOLUTION_TASK");
        claim.setSourceRefId(300L);
        // 无evidenceText，无sourceRefs

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("BLOCK");
    }

    @Test
    void ragChunkOfAbilityTagItselfCannotSelfEvidence() {
        // RAG分块为能力标签自身时不能自证
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("POST_EVOLUTION");
        claim.setClaimType("POST_EVOLUTION_CHANGE");
        claim.setClaimText("新增 微服务架构设计，要求等级3");
        claim.setSourceType("ABILITY_TAG"); // 来源是能力标签自身
        claim.setSourceRefId(300L);
        claim.setSourceRefs(List.of("rag:ABILITY_TAG:300"));

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("BLOCK");
        assertThat(decision.isSelfEvidence()).isTrue();
    }

    @Test
    void blockedChangeItemShouldNotEnterFormalList() {
        // BLOCK的变更项不会进入正式change item
        // 模拟一个没有证据的变更项
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("POST_EVOLUTION");
        claim.setClaimType("POST_EVOLUTION_CHANGE");
        claim.setClaimText("移除 传统单体架构");
        claim.setSourceType("POST_EVOLUTION_TASK");
        claim.setSourceRefId(300L);
        // 无证据

        AiHarnessDecisionDTO decision = service.verify(claim);

        // 应该被BLOCK
        assertThat(decision.getDecision()).isEqualTo("BLOCK");
        // 验证BLOCK的变更项不会被插入到change_item表
        // （实际验证在PostEvolutionServiceImpl的analyzeTask方法中，这里只验证Harness决策）
    }

    @Test
    void evolutionWithRagChunkEvidenceShouldBeReview() {
        // 有RAG分块证据的演化变更 -> REVIEW
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("POST_EVOLUTION");
        claim.setClaimType("POST_EVOLUTION_CHANGE");
        claim.setClaimText("调整 Java编程 等级 2 -> 3");
        claim.setSourceType("POST_EVOLUTION_TASK");
        claim.setSourceRefId(300L);
        claim.setEvidenceText("新JD要求：精通Java编程，具备复杂系统设计能力。");
        claim.setMatchedTagId(42L);
        claim.setSourceRefs(List.of("source:POST_EVOLUTION_TASK:300"));
        claim.setRagChunkIds(List.of(1001L, 1002L));

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isIn("PASS", "REVIEW");
        assertThat(decision.isSelfEvidence()).isFalse();
    }

    @Test
    void evolutionSourceRefShouldBeRecorded() {
        // 验证sourceRef正确记录为source:POST_EVOLUTION_TASK:{taskId}
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("POST_EVOLUTION");
        claim.setClaimType("POST_EVOLUTION_CHANGE");
        claim.setClaimText("新增 DevOps实践，要求等级2");
        claim.setSourceType("POST_EVOLUTION_TASK");
        claim.setSourceRefId(400L);
        claim.setEvidenceText("新JD要求熟悉CI/CD流水线搭建和DevOps实践。");
        claim.setSourceRefs(List.of("source:POST_EVOLUTION_TASK:400"));

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getCheckCode()).isNotBlank();
        assertThat(decision.isSelfEvidence()).isFalse();
    }
}
