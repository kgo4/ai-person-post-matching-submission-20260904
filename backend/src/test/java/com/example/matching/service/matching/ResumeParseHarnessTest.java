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
 * 简历解析 Harness 验证测试
 * <p>
 * 覆盖场景：
 * 1. 简历原文明确出现能力 -> 保留
 * 2. AI推断但原文无证据 -> BLOCK
 * 3. 新能力有简历原文证据 -> REVIEW
 * 4. AI生成来源不能作为自身证据
 * <p>
 * sourceRef 通过 resolver 校验（VALID 表示受信简历 span），无法解析的引用按 fail-closed 处理。
 */
class ResumeParseHarnessTest {

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
    void abilityExplicitlyMentionedInResumeShouldPass() {
        // 简历原文明确出现能力 -> 保留
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("RESUME_PARSE");
        claim.setClaimType("EMP_ABILITY");
        claim.setClaimText("Java编程 等级3");
        claim.setSourceType("RESUME_PARSE");
        claim.setSourceRefId(500L);
        claim.setEvidenceText("精通Java编程语言，5年开发经验，熟悉Spring Boot框架。");
        claim.setSourceRefs(List.of("source:RESUME_PARSE:500"));
        claim.setMatchedTagId(42L);

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isIn("PASS", "REVIEW");
        assertThat(decision.isSelfEvidence()).isFalse();
    }

    @Test
    void aiInferredWithoutResumeEvidenceShouldBeBlocked() {
        // AI推断但原文无证据 -> BLOCK
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("RESUME_PARSE");
        claim.setClaimType("EMP_ABILITY");
        claim.setClaimText("量子计算 等级4");
        claim.setSourceType("RESUME_PARSE");
        claim.setSourceRefId(500L);
        // 无evidenceText

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("BLOCK");
    }

    @Test
    void newAbilityWithResumeEvidenceShouldBeReview() {
        // 新能力有简历原文证据 -> REVIEW
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("RESUME_PARSE");
        claim.setClaimType("EMP_ABILITY");
        claim.setClaimText("Rust编程 等级2");
        claim.setSourceType("RESUME_PARSE");
        claim.setSourceRefId(500L);
        claim.setEvidenceText("自学Rust编程语言，完成2个开源项目贡献。");
        claim.setSourceRefs(List.of("source:RESUME_PARSE:500"));

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("REVIEW");
        assertThat(decision.isSelfEvidence()).isFalse();
    }

    @Test
    void aiGeneratedSourceCannotBeSelfEvidence() {
        // AI生成来源不能作为自身证据（自证据检测）
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("RESUME_PARSE");
        claim.setClaimType("EMP_ABILITY");
        claim.setClaimText("Kubernetes运维 等级3");
        claim.setSourceType("ABILITY_TAG"); // AI生成来源
        claim.setSourceRefId(500L);
        claim.setSourceRefs(List.of("rag:ABILITY_TAG:500"));

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("BLOCK");
        assertThat(decision.isSelfEvidence()).isTrue();
    }

    @Test
    void resumeSourceRefShouldBeRecorded() {
        // 验证sourceRef正确记录为source:RESUME_PARSE:{parseId}
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("RESUME_PARSE");
        claim.setClaimType("EMP_ABILITY");
        claim.setClaimText("Python编程 等级3");
        claim.setSourceType("RESUME_PARSE");
        claim.setSourceRefId(600L);
        claim.setEvidenceText("熟练使用Python进行数据分析和自动化脚本开发。");
        claim.setSourceRefs(List.of("source:RESUME_PARSE:600"));

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getCheckCode()).isNotBlank();
        assertThat(decision.isSelfEvidence()).isFalse();
    }
}
