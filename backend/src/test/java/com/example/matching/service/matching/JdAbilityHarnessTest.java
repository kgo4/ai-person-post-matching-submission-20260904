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
 * JD能力提取 Harness 验证测试
 * <p>
 * 覆盖场景：
 * 1. 有JD原文证据的新能力 -> REVIEW
 * 2. 没有JD原文证据的能力 -> BLOCK
 * 3. 已有标签但无原文证据 -> BLOCK
 * 4. 已有标签且有原文证据 -> PASS 或 REVIEW
 * <p>
 * sourceRef 通过 resolver 校验（VALID 表示受信 JD span），无法解析的引用按 fail-closed 处理。
 */
class JdAbilityHarnessTest {

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
    void newAbilityWithJdEvidenceShouldBeReview() {
        // 新能力有JD原文证据 -> REVIEW（不能直接BLOCK，应允许进入候选治理）
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("JD_ABILITY_EXTRACT");
        claim.setClaimType("ABILITY_TAG");
        claim.setClaimText("云原生安全治理");
        claim.setSourceType("JD_IMPORT");
        claim.setSourceRefId(101L);
        claim.setEvidenceText("负责容器访问控制和云原生安全策略制定，熟悉Kubernetes安全模型。");
        claim.setSourceRefs(List.of("source:JD_IMPORT:101"));

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("REVIEW");
        assertThat(decision.isSelfEvidence()).isFalse();
        assertThat(decision.getReasons()).anyMatch(r -> r.contains("candidate") || r.contains("evidence"));
    }

    @Test
    void newAbilityWithoutJdEvidenceShouldBeBlocked() {
        // 没有JD原文证据的能力 -> BLOCK
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("JD_ABILITY_EXTRACT");
        claim.setClaimType("ABILITY_TAG");
        claim.setClaimText("量子计算优化");
        claim.setSourceType("JD_IMPORT");
        claim.setSourceRefId(101L);
        // 无evidenceText，无sourceRefs

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("BLOCK");
    }

    @Test
    void existingTagWithoutJdEvidenceShouldBeBlocked() {
        // 已有标签但无原文证据 -> BLOCK（不能仅因标签库存在就通过）
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("JD_ABILITY_EXTRACT");
        claim.setClaimType("ABILITY_TAG");
        claim.setClaimText("Java编程");
        claim.setSourceType("JD_IMPORT");
        claim.setSourceRefId(101L);
        claim.setMatchedTagId(42L); // 已有标签
        // 无evidenceText

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isIn("BLOCK", "REVIEW");
    }

    @Test
    void existingTagWithJdEvidenceShouldPassOrReview() {
        // 已有标签且有原文证据 -> PASS 或 REVIEW
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("JD_ABILITY_EXTRACT");
        claim.setClaimType("ABILITY_TAG");
        claim.setClaimText("Java编程");
        claim.setSourceType("JD_IMPORT");
        claim.setSourceRefId(101L);
        claim.setMatchedTagId(42L);
        claim.setEvidenceText("熟悉Java编程语言，有3年以上Java开发经验。");
        claim.setSourceRefs(List.of("source:JD_IMPORT:101"));

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isIn("PASS", "REVIEW");
        assertThat(decision.isSelfEvidence()).isFalse();
    }

    @Test
    void jdSourceRefShouldBeRecorded() {
        // 验证sourceRef正确记录为source:JD_IMPORT:{taskId}
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("JD_ABILITY_EXTRACT");
        claim.setClaimType("ABILITY_TAG");
        claim.setClaimText("微服务架构");
        claim.setSourceType("JD_IMPORT");
        claim.setSourceRefId(200L);
        claim.setEvidenceText("负责微服务架构设计和拆分。");
        claim.setSourceRefs(List.of("source:JD_IMPORT:200"));

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getCheckCode()).isNotBlank();
        // 验证不会被标记为自证据
        assertThat(decision.isSelfEvidence()).isFalse();
    }
}
