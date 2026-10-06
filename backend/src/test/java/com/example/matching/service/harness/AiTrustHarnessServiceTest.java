package com.example.matching.service.harness;

import com.example.matching.ai.context.service.AiContextSourceRefService;
import com.example.matching.ai.context.dto.AiContextSourceRefDTO;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.port.evolution.MarketJdQueryPort;
import com.example.matching.service.harness.impl.AiTrustHarnessServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTrustHarnessServiceTest {

    private AiTrustHarnessService service;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<AiContextSourceRefService> sourceRefProvider = mock(ObjectProvider.class);
        when(sourceRefProvider.getIfAvailable()).thenReturn(null);
        service = new AiTrustHarnessServiceImpl(null, null, sourceRefProvider, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void diagnosisClaimWithResolvableReferenceGoesToReviewInsteadOfPrefixPass() {
        // 安全回归：MATCH_GAP_DIAGNOSIS 不再按 fact: 前缀自动 PASS(80)。
        // 引用可解析 + 有证据但无匹配正式标签 -> REVIEW（人工复核），杜绝伪造引用直通
        ObjectProvider<AiContextSourceRefService> sourceRefProvider = mock(ObjectProvider.class);
        AiContextSourceRefService sourceRefService = mock(AiContextSourceRefService.class);
        when(sourceRefProvider.getIfAvailable()).thenReturn(sourceRefService);
        when(sourceRefService.resolveWithStatus("fact:EMP_ABILITY:55"))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                        com.example.matching.common.source.SourceRefValidationResult.VALID,
                        new AiContextSourceRefDTO()));
        AiTrustHarnessServiceImpl strictService = new AiTrustHarnessServiceImpl(null, null, sourceRefProvider, null);

        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("MATCH_GAP_DIAGNOSIS");
        claim.setClaimType("DIAGNOSIS_DIMENSION");
        claim.setClaimText("Java ability gap is the primary risk.");
        claim.setSourceType("MATCHING_RECORD");
        claim.setSourceRefId(55L);
        claim.setEvidenceText("Java L2 -> required L4");
        claim.setSourceRefs(List.of("fact:EMP_ABILITY:55"));

        AiHarnessDecisionDTO decision = strictService.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("REVIEW");
        assertThat(decision.getAcceptedSourceRefs()).containsExactly("fact:EMP_ABILITY:55");
    }

    @Test
    void diagnosisClaimWithUnresolvableReferenceIsBlockedOrRetried() {
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("MATCH_GAP_DIAGNOSIS");
        claim.setClaimType("DIAGNOSIS_DIMENSION");
        claim.setClaimText("The candidate has strong architecture ownership.");
        claim.setSourceType("MATCHING_RECORD");
        claim.setSourceRefId(55L);
        claim.setEvidenceText("architecture ownership");
        claim.setSourceRefs(List.of("fact:EMP_ABILITY:999"));

        AiHarnessDecisionDTO decision = service.verify(claim);

        // resolver 不可用 -> 引用无法验证 -> fail-closed，不得 PASS
        assertThat(decision.getDecision()).isEqualTo("RETRY");
        assertThat(decision.getUnverifiableSourceRefs()).isNotEmpty();
    }

    @Test
    void diagnosisClaimWithoutReferenceIsBlocked() {
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("MATCH_GAP_DIAGNOSIS");
        claim.setClaimType("DIAGNOSIS_DIMENSION");
        claim.setClaimText("The candidate has strong architecture ownership.");
        claim.setSourceType("MATCHING_RECORD");
        claim.setSourceRefId(55L);

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("BLOCK");
        assertThat(decision.getMissingEvidence()).contains("sourceRefs");
    }

    @Test
    @SuppressWarnings("unchecked")
    void newAbilityWithOriginalEvidenceGoesToReviewInsteadOfPass() {
        ObjectProvider<AiContextSourceRefService> sourceRefProvider = mock(ObjectProvider.class);
        AiContextSourceRefService sourceRefService = mock(AiContextSourceRefService.class);
        when(sourceRefProvider.getIfAvailable()).thenReturn(sourceRefService);
        when(sourceRefService.resolveWithStatus("source:JD_IMPORT:101"))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                        com.example.matching.common.source.SourceRefValidationResult.VALID,
                        new AiContextSourceRefDTO()));
        AiTrustHarnessServiceImpl reviewService = new AiTrustHarnessServiceImpl(null, null, sourceRefProvider, null);

        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("JD_ABILITY_EXTRACT");
        claim.setClaimType("ABILITY_TAG");
        claim.setClaimText("Cloud native security governance");
        claim.setSourceType("JD_IMPORT");
        claim.setSourceRefId(101L);
        claim.setEvidenceText("Responsible for container access control and cloud native security policy.");
        claim.setSourceRefs(List.of("source:JD_IMPORT:101"));

        AiHarnessDecisionDTO decision = reviewService.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("REVIEW");
    }

    @Test
    void personAbilityWithoutGroundedSourceSnippetRequiresReview() {
        @SuppressWarnings("unchecked")
        ObjectProvider<AiContextSourceRefService> sourceRefProvider = mock(ObjectProvider.class);
        AiContextSourceRefService sourceRefService = mock(AiContextSourceRefService.class);
        when(sourceRefProvider.getIfAvailable()).thenReturn(sourceRefService);
        when(sourceRefService.resolveWithStatus("source:RESUME:101"))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                        com.example.matching.common.source.SourceRefValidationResult.VALID,
                        new AiContextSourceRefDTO()));
        AiTrustHarnessService strictService = new AiTrustHarnessServiceImpl(null, null, sourceRefProvider, null);

        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("PERSON_ABILITY");
        claim.setClaimType("EMP_ABILITY");
        claim.setClaimText("Distributed tracing");
        claim.setSourceType("RESUME");
        claim.setSourceRefId(101L);
        claim.setEvidenceText("Implemented distributed tracing across production services.");
        claim.setSourceRefs(List.of("source:RESUME:101"));
        claim.setSimilarTagId(42L);
        claim.setRagChunkIds(List.of(9L));

        AiHarnessDecisionDTO decision = strictService.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("REVIEW");
        assertThat(decision.getReasons()).anyMatch(reason -> reason.contains("binding requires human review"));
    }

    @Test
    void abilityClaimBackedOnlyByGeneratedTagDocumentIsBlockedAsSelfEvidence() {
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("JD_ABILITY_EXTRACT");
        claim.setClaimType("ABILITY_TAG");
        claim.setClaimText("Cloud native security governance");
        claim.setSourceType("ABILITY_TAG");
        claim.setSourceRefId(200L);
        claim.setSourceRefs(List.of("rag:ABILITY_TAG:200"));

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("BLOCK");
        assertThat(decision.isSelfEvidence()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void postEvolutionRemovalRequiresReviewEvenWithMultipleSources() {
        ObjectProvider<AiContextSourceRefService> sourceRefProvider = mock(ObjectProvider.class);
        AiContextSourceRefService sourceRefService = mock(AiContextSourceRefService.class);
        when(sourceRefProvider.getIfAvailable()).thenReturn(sourceRefService);
        when(sourceRefService.resolveWithStatus(any()))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                        com.example.matching.common.source.SourceRefValidationResult.VALID,
                        new AiContextSourceRefDTO()));
        AiTrustHarnessServiceImpl evolutionService = new AiTrustHarnessServiceImpl(null, null, sourceRefProvider, null);

        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("POST_EVOLUTION");
        claim.setClaimType("ABILITY_CHANGE");
        claim.setChangeType("REMOVE_ABILITY");
        claim.setClaimText("Remove legacy monolith deployment ability");
        claim.setMatchedTagId(42L);
        claim.setEvidenceText("Two approved market sources no longer list this requirement.");
        claim.setSourceRefs(List.of("source:MARKET_JD:1", "source:MARKET_JD:2"));

        AiHarnessDecisionDTO decision = evolutionService.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("REVIEW");
        assertThat(decision.getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistedLogGetsDerivedManualReviewStatus() {
        @SuppressWarnings("unchecked")
        ObjectProvider<AiHarnessCheckLogMapper> mapperProvider = mock(ObjectProvider.class);
        AiHarnessCheckLogMapper mapper = mock(AiHarnessCheckLogMapper.class);
        when(mapperProvider.getIfAvailable()).thenReturn(mapper);
        when(mapper.insert(any(AiHarnessCheckLog.class))).thenReturn(1);

        @SuppressWarnings("unchecked")
        ObjectProvider<AiContextSourceRefService> sourceRefProvider2 = mock(ObjectProvider.class);
        AiContextSourceRefService sourceRefService2 = mock(AiContextSourceRefService.class);
        when(sourceRefProvider2.getIfAvailable()).thenReturn(sourceRefService2);
        when(sourceRefService2.resolveWithStatus("source:JD_IMPORT:101"))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                        com.example.matching.common.source.SourceRefValidationResult.VALID,
                        new AiContextSourceRefDTO()));
        when(sourceRefService2.resolveWithStatus("fact:EMP_ABILITY:55"))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                        com.example.matching.common.source.SourceRefValidationResult.VALID,
                        new AiContextSourceRefDTO()));
        AiTrustHarnessServiceImpl persistedService = new AiTrustHarnessServiceImpl(mapperProvider, null, sourceRefProvider2, null);

        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("JD_ABILITY_EXTRACT");
        claim.setClaimType("ABILITY_TAG");
        claim.setClaimText("Cloud native security governance");
        claim.setSourceType("JD_IMPORT");
        claim.setSourceRefId(101L);
        claim.setEvidenceText("Responsible for container access control and cloud native security policy.");
        claim.setSourceRefs(List.of("source:JD_IMPORT:101"));

        persistedService.verify(claim);

        ArgumentCaptor<AiHarnessCheckLog> captor = ArgumentCaptor.forClass(AiHarnessCheckLog.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getReviewStatus()).isEqualTo("PENDING");

        clearInvocations(mapper);

        // 达到 PASS 门槛：匹配正式标签(+45) + 原始证据(+30) + 可解析引用(+20) = 95 >= 70
        AiHarnessClaimDTO passClaim = new AiHarnessClaimDTO();
        passClaim.setScenario("MATCH_GAP_DIAGNOSIS");
        passClaim.setClaimType("DIAGNOSIS_DIMENSION");
        passClaim.setClaimText("Java ability gap is the primary risk.");
        passClaim.setSourceType("MATCHING_RECORD");
        passClaim.setSourceRefId(55L);
        passClaim.setEvidenceText("Java L2 -> required L4");
        passClaim.setMatchedTagId(1L);
        passClaim.setSourceRefs(List.of("fact:EMP_ABILITY:55"));

        persistedService.verify(passClaim);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getAllValues().get(1).getReviewStatus()).isEqualTo("AUTO_PASSED");
    }

    // ==================== P0.1 regression tests ====================

    @Test
    void scoreIsClampedTo0100_onNegativeScore() {
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("UNKNOWN_SCENARIO");
        claim.setClaimText("test clamp");

        AiHarnessDecisionDTO decision = service.verify(claim);

        // Score should be clamped to [0,100], never negative
        assertThat(decision.getSupportScore()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
    }

    @Test
    void scoreIsClampedTo0100_onHighScore() {
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("MATCH_GAP_DIAGNOSIS");
        claim.setClaimType("DIAGNOSIS_DIMENSION");
        claim.setClaimText("Java ability gap is the primary risk.");
        claim.setMatchedTagId(1L);
        claim.setEvidenceText("Java L2 -> required L4");
        claim.setSourceRefs(List.of("fact:EMP_ABILITY:1", "source:JD:1"));

        AiHarnessDecisionDTO decision = service.verify(claim);

        // Score should never exceed 100
        assertThat(decision.getSupportScore().doubleValue()).isLessThanOrEqualTo(100.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveException_rejectsRefAsUnverifiable() {
        AiContextSourceRefService throwingService = mock(AiContextSourceRefService.class);
        when(throwingService.resolveWithStatus(any()))
                .thenThrow(new RuntimeException("DB down"));
        ObjectProvider<AiContextSourceRefService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(throwingService);

        AiTrustHarnessServiceImpl svc = new AiTrustHarnessServiceImpl(null, null, provider, null);

        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("JD_ABILITY_EXTRACT");
        claim.setClaimType("ABILITY_TAG");
        claim.setClaimText("Test");
        claim.setEvidenceText("Evidence");
        claim.setSourceRefs(List.of("fact:EMP_ABILITY:1"));

        AiHarnessDecisionDTO decision = svc.verify(claim);

        // Resolve exception → ref goes to unverifiable, not accepted
        assertThat(decision.getUnverifiableSourceRefs()).isNotEmpty();
        assertThat(decision.getAcceptedSourceRefs()).isEmpty();
        // 依赖故障 fail-closed：RETRY，不再以 REVIEW 放行
        assertThat(decision.getDecision()).isEqualTo("RETRY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveReturnsNull_rejectsRef() {
        AiContextSourceRefService nullService = mock(AiContextSourceRefService.class);
        when(nullService.resolveWithStatus(any()))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                        com.example.matching.common.source.SourceRefValidationResult.NOT_FOUND, null));
        ObjectProvider<AiContextSourceRefService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(nullService);

        AiTrustHarnessServiceImpl svc = new AiTrustHarnessServiceImpl(null, null, provider, null);

        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("JD_ABILITY_EXTRACT");
        claim.setClaimType("ABILITY_TAG");
        claim.setClaimText("Test");
        claim.setEvidenceText("Evidence");
        claim.setSourceRefs(List.of("fact:EMP_ABILITY:1"));

        AiHarnessDecisionDTO decision = svc.verify(claim);

        // 记录不存在 → 永久 BLOCK（NOT_FOUND），不进入 REVIEW
        assertThat(decision.getAcceptedSourceRefs()).isEmpty();
        assertThat(decision.getInvalidSourceRefs()).isNotEmpty();
        assertThat(decision.getDecision()).isEqualTo("BLOCK");
    }

    @Test
    @SuppressWarnings("unchecked")
    void serviceNotAvailable_allRefsBecomeUnverifiable() {
        ObjectProvider<AiContextSourceRefService> nullProvider = mock(ObjectProvider.class);
        when(nullProvider.getIfAvailable()).thenReturn(null);

        AiTrustHarnessServiceImpl svc = new AiTrustHarnessServiceImpl(null, null, nullProvider, null);

        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("JD_ABILITY_EXTRACT");
        claim.setClaimType("ABILITY_TAG");
        claim.setClaimText("Test");
        claim.setEvidenceText("Evidence");
        claim.setSourceRefs(List.of("fact:EMP_ABILITY:1", "source:JD_IMPORT:1"));

        AiHarnessDecisionDTO decision = svc.verify(claim);

        // Service unavailable → all refs unverifiable
        assertThat(decision.getUnverifiableSourceRefs()).hasSize(2);
        assertThat(decision.getAcceptedSourceRefs()).isEmpty();
        // fail-closed：RETRY，不写候选、不写正式事实
        assertThat(decision.getDecision()).isEqualTo("RETRY");
    }

    // ==================== Task 5b: grouped market JD new ability PASS ====================

    /**
     * 构造一个能解析 source:MARKET_JD refs、且返回不同 companyDiversityKey 的 Harness 实例。
     */
    @SuppressWarnings("unchecked")
    private AiTrustHarnessServiceImpl marketHarness(Long jd1, String key1, Long jd2, String key2) {
        return marketHarness(jd1, key1, jd2, key2, List.of());
    }

    @SuppressWarnings("unchecked")
    private AiTrustHarnessServiceImpl marketHarness(Long jd1, String key1, Long jd2, String key2,
                                                    List<Long> missingIds) {
        AiContextSourceRefService sourceRefService = mock(AiContextSourceRefService.class);
        ObjectProvider<AiContextSourceRefService> sourceRefProvider = mock(ObjectProvider.class);
        when(sourceRefProvider.getIfAvailable()).thenReturn(sourceRefService);
        for (Long jdId : List.of(jd1, jd2)) {
            when(sourceRefService.resolveWithStatus("source:MARKET_JD:" + jdId))
                    .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                            com.example.matching.common.source.SourceRefValidationResult.VALID,
                            new AiContextSourceRefDTO()));
        }
        for (Long missingId : missingIds) {
            when(sourceRefService.resolveWithStatus("source:MARKET_JD:" + missingId))
                    .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                            com.example.matching.common.source.SourceRefValidationResult.NOT_FOUND, null));
        }
        MarketJdQueryPort marketJdQueryPort = mock(MarketJdQueryPort.class);
        when(marketJdQueryPort.getCompanyDiversityKey(jd1)).thenReturn(key1);
        when(marketJdQueryPort.getCompanyDiversityKey(jd2)).thenReturn(key2);
        ObjectProvider<MarketJdQueryPort> portProvider = mock(ObjectProvider.class);
        when(portProvider.getIfAvailable()).thenReturn(marketJdQueryPort);
        return new AiTrustHarnessServiceImpl(null, null, sourceRefProvider, portProvider);
    }

    private AiHarnessClaimDTO marketNewAbilityClaim(String scenario, List<String> refs) {
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario(scenario);
        claim.setClaimType("ABILITY_TAG");
        claim.setClaimText("向量数据库");
        claim.setSourceType("MARKET_JD");
        claim.setSourceRefId(1L);
        claim.setEvidenceText("负责向量数据库开发");
        claim.setSourceRefs(refs);
        return claim;
    }

    @Test
    void groupedMarketNewAbilityWithTwoCompaniesCanPass() {
        AiTrustHarnessServiceImpl service = marketHarness(1L, "A公司", 2L, "B公司");

        AiHarnessDecisionDTO decision = service.verify(marketNewAbilityClaim(
                "MARKET_JD_ABILITY_ADMISSION", List.of("source:MARKET_JD:1", "source:MARKET_JD:2")));

        assertThat(decision.getDecision()).isEqualTo("PASS");
        // 支持分对齐准入阈值 80，供 AbilityTagAdmissionPipeline.admitVerifiedNewTag 直接建正式标签
        assertThat(decision.getSupportScore()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(decision.getDecisionRule()).isEqualTo("MARKET_JD_GROUPED_MULTI_COMPANY");
        assertThat(decision.getAcceptedSourceRefs()).containsExactlyInAnyOrder(
                "source:MARKET_JD:1", "source:MARKET_JD:2");
    }

    @Test
    void singleSourceMarketNewAbilityRemainsReview() {
        AiTrustHarnessServiceImpl service = marketHarness(1L, "A公司", 2L, "B公司");

        AiHarnessDecisionDTO decision = service.verify(marketNewAbilityClaim(
                "MARKET_JD_ABILITY_ADMISSION", List.of("source:MARKET_JD:1")));

        assertThat(decision.getDecision()).isEqualTo("REVIEW");
        assertThat(decision.getSupportScore()).isNotEqualByComparingTo(new BigDecimal("80"));
    }

    @Test
    void marketNewAbilityWithInvalidRefRemainsReviewNotPass() {
        AiTrustHarnessServiceImpl service = marketHarness(1L, "A公司", 2L, "B公司", List.of(999L));

        AiHarnessDecisionDTO decision = service.verify(marketNewAbilityClaim(
                "MARKET_JD_ABILITY_ADMISSION", List.of("source:MARKET_JD:1", "source:MARKET_JD:999")));

        // 999 解析为 NOT_FOUND → invalid ref 存在 → 不允许 PASS
        assertThat(decision.getDecision()).isEqualTo("REVIEW");
        assertThat(decision.getInvalidSourceRefs()).contains("source:MARKET_JD:999");
    }

    @Test
    void equivalentNonMarketClaimStillForcedToReview() {
        AiTrustHarnessServiceImpl service = marketHarness(1L, "A公司", 2L, "B公司");

        // 同样的 claim 但场景不是 MARKET_JD_ABILITY_ADMISSION：无标签新能力仍强制 REVIEW
        AiHarnessDecisionDTO decision = service.verify(marketNewAbilityClaim(
                "JD_ABILITY_EXTRACT", List.of("source:MARKET_JD:1", "source:MARKET_JD:2")));

        assertThat(decision.getDecision()).isEqualTo("REVIEW");
        assertThat(decision.getDecisionRule()).isNotEqualTo("MARKET_JD_GROUPED_MULTI_COMPANY");
    }

    @Test
    void marketNewAbilityWithoutEvidenceIsBlockedNotPassed() {
        AiTrustHarnessServiceImpl service = marketHarness(1L, "A公司", 2L, "B公司");
        AiHarnessClaimDTO claim = marketNewAbilityClaim(
                "MARKET_JD_ABILITY_ADMISSION", List.of("source:MARKET_JD:1", "source:MARKET_JD:2"));
        claim.setEvidenceText(null);

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("BLOCK");
    }

    // ==================== 防幻觉加固回归测试 ====================

    @Test
    void matchedTagPlusEvidenceWithoutSourceRefsCannotAutoPass() {
        // 修复回归：matchedTag + evidenceText 但无任何 sourceRefs，不得绕过引用校验直通 PASS。
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("MATCH_GAP_DIAGNOSIS");
        claim.setClaimType("DIAGNOSIS_DIMENSION");
        claim.setClaimText("Java ability gap is the primary risk.");
        claim.setSourceType("MATCHING_RECORD");
        claim.setSourceRefId(55L);
        claim.setMatchedTagId(1L);
        claim.setEvidenceText("Java L2 -> required L4");
        // 不设置 sourceRefs

        AiHarnessDecisionDTO decision = service.verify(claim);

        assertThat(decision.getDecision()).isEqualTo("REVIEW");
        assertThat(decision.getReasons()).anyMatch(r -> r.contains("sourceRef"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void factAbilityTagReferenceIsNotSelfEvidence() {
        // 修复回归：fact:ABILITY_TAG:{id} 指向正式标签表的标准事实引用，不得被误判为 AI 自证而 BLOCK。
        ObjectProvider<AiContextSourceRefService> sourceRefProvider = mock(ObjectProvider.class);
        AiContextSourceRefService sourceRefService = mock(AiContextSourceRefService.class);
        when(sourceRefProvider.getIfAvailable()).thenReturn(sourceRefService);
        when(sourceRefService.resolveWithStatus("fact:ABILITY_TAG:123"))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                        com.example.matching.common.source.SourceRefValidationResult.VALID,
                        new AiContextSourceRefDTO()));
        AiTrustHarnessServiceImpl svc = new AiTrustHarnessServiceImpl(null, null, sourceRefProvider, null);

        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("PERSON_ABILITY");
        claim.setClaimType("EMP_ABILITY");
        claim.setClaimText("Java");
        claim.setSourceType("RESUME_PARSE");
        claim.setSourceRefs(List.of("fact:ABILITY_TAG:123"));

        AiHarnessDecisionDTO decision = svc.verify(claim);

        assertThat(decision.isSelfEvidence()).isFalse();
        assertThat(decision.getAcceptedSourceRefs()).contains("fact:ABILITY_TAG:123");
    }

    @Test
    @SuppressWarnings("unchecked")
    void personnelAbilityCanUseCanonicalTagNameToBindEvidence() {
        ObjectProvider<AiContextSourceRefService> sourceRefProvider = mock(ObjectProvider.class);
        AiContextSourceRefService sourceRefService = mock(AiContextSourceRefService.class);
        when(sourceRefProvider.getIfAvailable()).thenReturn(sourceRefService);
        AiContextSourceRefDTO source = new AiContextSourceRefDTO();
        source.setSourceType("RESUME_PARSE");
        source.setSnippet("负责 Kubernetes 集群发布与运维");
        when(sourceRefService.resolveWithStatus("source:RESUME_PARSE:1"))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                        com.example.matching.common.source.SourceRefValidationResult.VALID, source));

        AbilityTagMapper tagMapper = mock(AbilityTagMapper.class);
        AbilityTag alias = new AbilityTag();
        alias.setId(10L);
        alias.setTagName("K8S");
        alias.setCanonicalTagId(11L);
        AbilityTag canonical = new AbilityTag();
        canonical.setId(11L);
        canonical.setTagName("Kubernetes");
        when(tagMapper.selectById(10L)).thenReturn(alias);
        when(tagMapper.selectById(11L)).thenReturn(canonical);
        ObjectProvider<AbilityTagMapper> tagMapperProvider = mock(ObjectProvider.class);
        when(tagMapperProvider.getIfAvailable()).thenReturn(tagMapper);

        AiTrustHarnessServiceImpl harness = new AiTrustHarnessServiceImpl(null, null, sourceRefProvider, null);
        ReflectionTestUtils.setField(harness, "abilityTagMapperProvider", tagMapperProvider);
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("PERSON_ABILITY");
        claim.setClaimType("EMP_ABILITY");
        claim.setClaimText("K8S");
        claim.setMatchedTagId(10L);
        claim.setEvidenceText("候选人有云原生平台交付经历");
        claim.setSourceRefs(List.of("source:RESUME_PARSE:1"));

        assertThat(harness.verify(claim).getDecision()).isEqualTo("PASS");
    }

    @Test
    @SuppressWarnings("unchecked")
    void personnelEvidencePackageUsesSourceQualityInsteadOfHarnessScore() {
        ObjectProvider<AiContextSourceRefService> sourceRefProvider = mock(ObjectProvider.class);
        AiContextSourceRefService sourceRefService = mock(AiContextSourceRefService.class);
        when(sourceRefProvider.getIfAvailable()).thenReturn(sourceRefService);

        AiContextSourceRefDTO strongRef = new AiContextSourceRefDTO();
        strongRef.setSourceType("RESUME_PARSE");
        strongRef.setSnippet("负责支付系统重构并落地分布式追踪");
        when(sourceRefService.resolveWithStatus("fact:EMP_ABILITY:11"))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                        com.example.matching.common.source.SourceRefValidationResult.VALID, strongRef));

        AiTrustHarnessServiceImpl svc = new AiTrustHarnessServiceImpl(null, null, sourceRefProvider, null);
        AiHarnessClaimDTO strong = new AiHarnessClaimDTO();
        strong.setScenario("PERSON_ABILITY");
        strong.setClaimType("EMP_ABILITY");
        strong.setClaimText("分布式追踪");
        strong.setMatchedTagId(1L);
        strong.setEvidenceText("负责支付系统重构并落地分布式追踪");
        strong.setSourceRefs(List.of("fact:EMP_ABILITY:11"));

        assertThat(svc.verify(strong).getDecision()).isEqualTo("PASS");

        AiContextSourceRefDTO weakRef = new AiContextSourceRefDTO();
        weakRef.setSourceType("UNKNOWN");
        weakRef.setSnippet("相关经验");
        when(sourceRefService.resolveWithStatus("fact:EMP_ABILITY:12"))
                .thenReturn(new AiContextSourceRefService.ResolveOutcome(
                        com.example.matching.common.source.SourceRefValidationResult.VALID, weakRef));
        AiHarnessClaimDTO weak = new AiHarnessClaimDTO();
        weak.setScenario("PERSON_ABILITY");
        weak.setClaimType("EMP_ABILITY");
        weak.setClaimText("分布式追踪");
        weak.setMatchedTagId(1L);
        weak.setEvidenceText("相关经验");
        weak.setSourceRefs(List.of("fact:EMP_ABILITY:12"));

        assertThat(svc.verify(weak).getDecision()).isEqualTo("REVIEW");
    }
}
