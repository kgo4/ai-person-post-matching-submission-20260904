package com.example.matching.service.evolution.impl;

import com.example.matching.application.system.VerifiedAbilityTagAdmissionFacade;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.system.TagAdmissionResult;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.config.MarketJdCapabilityAdmissionProperties;
import com.example.matching.service.evolution.MarketJdCapabilityAdmissionService;
import com.example.matching.service.evolution.MarketJdCapabilityAdmissionService.AdmissionBatchRequest;
import com.example.matching.service.evolution.MarketJdCapabilityAdmissionService.AdmissionGateResult;
import com.example.matching.service.evolution.MarketJdCapabilityAdmissionService.AdmissionPlan;
import com.example.matching.service.evolution.MarketJdCapabilityAdmissionService.ExistingTagDeferredClaim;
import com.example.matching.service.evolution.MarketJdCapabilityAdmissionService.FormalTagPlan;
import com.example.matching.service.evolution.MarketJdCapabilityAdmissionService.GroupMember;
import com.example.matching.service.evolution.MarketJdCapabilityAdmissionService.JdExtraction;
import com.example.matching.service.evolution.MarketJdCapabilityAdmissionService.NewAbilityGroup;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.service.system.impl.AbilityTagAdmissionPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 市场JD能力自动准入确定性门禁 —— 决策表逐行测试
 */
@ExtendWith(MockitoExtension.class)
class MarketJdCapabilityAdmissionServiceImplTest {

    private MarketJdCapabilityAdmissionServiceImpl service;

    @Mock
    private TagQueryPort tagQueryPort;
    @Mock
    private AiTrustHarnessService harnessService;
    @Mock
    private VerifiedAbilityTagAdmissionFacade verifiedFacade;

    private MarketJdCapabilityAdmissionProperties properties;

    private AbilityTag javaTag;
    private AbilityTag sqlTag;

    @BeforeEach
    void setUp() {
        properties = new MarketJdCapabilityAdmissionProperties();
        service = new MarketJdCapabilityAdmissionServiceImpl(properties, tagQueryPort,
                harnessService, verifiedFacade);

        javaTag = enabledTag(10L, "Java");
        sqlTag = enabledTag(20L, "MySQL");
        when(tagQueryPort.listActiveTags(0)).thenReturn(
                List.of(TagQueryPort.TagDTO.from(javaTag), TagQueryPort.TagDTO.from(sqlTag)));
    }

    @Test
    void directTagHit_autoAccepts() {
        JdExtraction jd = jd(1L, "负责Java开发", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("Java", "MATCHED", 10L, "负责Java开发", null, null, List.of("source:MARKET_JD:1"))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(result.autoAcceptedTagIdsByJd().get(1L)).containsExactly(10L);
        assertThat(result.deferredExistingTagClaims()).isEmpty();
        assertThat(result.deferredNewAbilityGroups()).isEmpty();
        assertThat(result.rejectedClaimCount()).isZero();
    }

    @Test
    void aliasHit_autoAccepts() {
        when(tagQueryPort.listAliases(10L)).thenReturn(List.of("J2SE"));
        JdExtraction jd = jd(1L, "熟悉J2SE开发与Spring", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("Java", "MATCHED", 10L, "熟悉J2SE开发与Spring", null, null, List.of("source:MARKET_JD:1"))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(result.autoAcceptedTagIdsByJd().get(1L)).containsExactly(10L);
        assertThat(result.deferredExistingTagClaims()).isEmpty();
    }

    @Test
    void semanticDefer_existingTagWithoutLiteralMention() {
        JdExtraction jd = jd(1L, "负责订单系统开发", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("Java", "MATCHED", 10L, "负责订单系统开发", null, null, List.of("source:MARKET_JD:1"))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(result.autoAcceptedTagIdsByJd()).isEmpty();
        assertThat(result.deferredExistingTagClaims()).hasSize(1);
        ExistingTagDeferredClaim claim = result.deferredExistingTagClaims().get(0);
        assertThat(claim.matchedTagId()).isEqualTo(10L);
        assertThat(claim.jdId()).isEqualTo(1L);
    }

    @Test
    void similarWithoutLiteralMention_defers() {
        JdExtraction jd = jd(1L, "负责数据存储设计", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("MySQL", "SIMILAR", 20L, "负责数据存储设计", null, null, List.of("source:MARKET_JD:1"))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(result.autoAcceptedTagIdsByJd()).isEmpty();
        assertThat(result.deferredExistingTagClaims()).hasSize(1);
        assertThat(result.deferredExistingTagClaims().get(0).matchStatus()).isEqualTo("SIMILAR");
    }

    @Test
    void offsetMismatch_rejected() {
        // 证据文本 "Java开发" 在 cleanedJdText "负责Java开发" 中的实际位置是 2..10，
        // 传入错误偏移 0..3 必须拒绝
        JdExtraction jd = jd(1L, "负责Java开发", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("Java", "MATCHED", 10L, "Java开发", 0, 3, List.of("source:MARKET_JD:1"))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(result.autoAcceptedTagIdsByJd()).isEmpty();
        assertThat(result.deferredExistingTagClaims()).isEmpty();
        assertThat(result.rejectedClaimCount()).isEqualTo(1);
    }

    @Test
    void evidenceAbsentFromJd_rejected() {
        JdExtraction jd = jd(1L, "负责订单系统开发", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("Java", "MATCHED", 10L, "完全不存在的证据文本", null, null, List.of("source:MARKET_JD:1"))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(result.autoAcceptedTagIdsByJd()).isEmpty();
        assertThat(result.rejectedClaimCount()).isEqualTo(1);
    }

    @Test
    void foreignSourceReference_rejected() {
        JdExtraction jd = jd(1L, "负责Java开发", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("Java", "MATCHED", 10L, "负责Java开发", null, null,
                        List.of("source:MARKET_JD:1", "source:OTHER:999"))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(result.autoAcceptedTagIdsByJd()).isEmpty();
        assertThat(result.rejectedClaimCount()).isEqualTo(1);
    }

    @Test
    void disabledTag_rejected() {
        // matchedTagId=999 不在启用标签列表（mock 只返回 javaTag/sqlTag）
        JdExtraction jd = jd(1L, "负责量子计算", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("量子计算", "MATCHED", 999L, "负责量子计算", null, null, List.of("source:MARKET_JD:1"))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(result.autoAcceptedTagIdsByJd()).isEmpty();
        assertThat(result.deferredExistingTagClaims()).isEmpty();
        assertThat(result.rejectedClaimCount()).isEqualTo(1);
    }

    @Test
    void newAbilityBelowThresholds_rejectedNotReviewed() {
        // 只出现在 1 个 JD / 1 家公司 -> 低于 minJdCount=3 -> 整体拒绝
        JdExtraction jd1 = jd(1L, "精通图数据库开发", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("图数据库", "NEW", null, "精通图数据库开发", null, null, List.of("source:MARKET_JD:1"))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", List.of(jd1)));

        assertThat(result.deferredNewAbilityGroups()).isEmpty();
        assertThat(result.deferredExistingTagClaims()).isEmpty();
        assertThat(result.rejectedClaimCount()).isEqualTo(1);
    }

    @Test
    void newAbilityMeetingBothThresholds_defersGroup() {
        // 3 个 JD、2 家公司 -> 达到双阈值，进入分组（不直接准入）
        List<JdExtraction> jds = List.of(
                jd(1L, "负责向量数据库开发", "A公司", List.of("source:MARKET_JD:1"),
                        List.of(item("向量数据库", "NEW", null, "负责向量数据库开发", null, null, List.of("source:MARKET_JD:1")))),
                jd(2L, "负责向量数据库开发", "A公司", List.of("source:MARKET_JD:2"),
                        List.of(item("向量数据库", "NEW", null, "负责向量数据库开发", null, null, List.of("source:MARKET_JD:2")))),
                jd(3L, "负责向量数据库开发", "B公司", List.of("source:MARKET_JD:3"),
                        List.of(item("向量数据库", "NEW", null, "负责向量数据库开发", null, null, List.of("source:MARKET_JD:3")))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", jds));

        assertThat(result.deferredExistingTagClaims()).isEmpty();
        assertThat(result.deferredNewAbilityGroups()).hasSize(1);
        NewAbilityGroup group = result.deferredNewAbilityGroups().values().iterator().next();
        assertThat(group.distinctJdCount()).isEqualTo(3);
        assertThat(group.distinctCompanyCount()).isEqualTo(2);
        assertThat(group.members()).hasSize(3);
    }

    @Test
    void newAbilityFromOnlyOneIndependentEmployer_isRejected() {
        // 3 条不同 JD 但都来自同一匿名招聘主体，不能伪造跨主体交叉验证。
        List<JdExtraction> jds = List.of(
                jd(1L, "负责向量数据库开发", "employer-a", List.of("source:MARKET_JD:1"),
                        List.of(item("向量数据库", "NEW", null, "负责向量数据库开发", null, null, List.of("source:MARKET_JD:1")))),
                jd(2L, "负责向量数据库开发", "employer-a", List.of("source:MARKET_JD:2"),
                        List.of(item("向量数据库", "NEW", null, "负责向量数据库开发", null, null, List.of("source:MARKET_JD:2")))),
                jd(3L, "负责向量数据库开发", "employer-a", List.of("source:MARKET_JD:3"),
                        List.of(item("向量数据库", "NEW", null, "负责向量数据库开发", null, null, List.of("source:MARKET_JD:3")))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", jds));

        assertThat(result.deferredNewAbilityGroups()).isEmpty();
        assertThat(result.rejectedClaimCount()).isEqualTo(3);
    }

    @Test
    void newAbilityWithoutIndependentEmployerKey_isRejected() {
        List<JdExtraction> jds = List.of(
                jd(1L, "负责向量数据库开发", " ", List.of("source:MARKET_JD:1"),
                        List.of(item("向量数据库", "NEW", null, "负责向量数据库开发", null, null, List.of("source:MARKET_JD:1")))),
                jd(2L, "负责向量数据库开发", null, List.of("source:MARKET_JD:2"),
                        List.of(item("向量数据库", "NEW", null, "负责向量数据库开发", null, null, List.of("source:MARKET_JD:2")))),
                jd(3L, "负责向量数据库开发", "", List.of("source:MARKET_JD:3"),
                        List.of(item("向量数据库", "NEW", null, "负责向量数据库开发", null, null, List.of("source:MARKET_JD:3")))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", jds));

        assertThat(result.deferredNewAbilityGroups()).isEmpty();
        assertThat(result.rejectedClaimCount()).isEqualTo(3);
    }

    @Test
    void newAbilityGroupMembersAreDeterministicallyOrdered() {
        // 乱序输入，成员必须按 companyDiversityKey -> jdId 排序
        List<JdExtraction> jds = List.of(
                jd(9L, "负责数据治理开发", "B公司", List.of("source:MARKET_JD:9"),
                        List.of(item("数据治理", "NEW", null, "负责数据治理开发", null, null, List.of("source:MARKET_JD:9")))),
                jd(3L, "负责数据治理开发", "A公司", List.of("source:MARKET_JD:3"),
                        List.of(item("数据治理", "NEW", null, "负责数据治理开发", null, null, List.of("source:MARKET_JD:3")))),
                jd(1L, "负责数据治理开发", "A公司", List.of("source:MARKET_JD:1"),
                        List.of(item("数据治理", "NEW", null, "负责数据治理开发", null, null, List.of("source:MARKET_JD:1")))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", jds));

        NewAbilityGroup group = result.deferredNewAbilityGroups().values().iterator().next();
        assertThat(group.members()).extracting(GroupMember::jdId).containsExactly(1L, 3L, 9L);
    }

    @Test
    void sameJdAndCompanyDeduplicatedInGroup() {
        // 同一 JD 同一公司重复主张 -> 成员去重（distinctJdCount 仍为 1）
        JdExtraction jd1 = jd(1L, "负责推荐算法开发", "A公司", List.of("source:MARKET_JD:1"),
                List.of(item("推荐算法", "NEW", null, "负责推荐算法开发", null, null, List.of("source:MARKET_JD:1")),
                        item("推荐算法", "NEW", null, "负责推荐算法开发", null, null, List.of("source:MARKET_JD:1"))));

        AdmissionGateResult result = service.evaluateGate(new AdmissionBatchRequest("B1", List.of(jd1)));

        // 低于阈值被整体拒绝
        assertThat(result.deferredNewAbilityGroups()).isEmpty();
        assertThat(result.rejectedClaimCount()).isEqualTo(1);
    }

    // ==================== Task 5: batch Harness decisions ====================

    @Test
    void existingTagPass_autoPersistsInPlan() {
        JdExtraction jd = jd(1L, "负责订单系统开发", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("Java", "MATCHED", 10L, "负责订单系统开发", null, null, List.of("source:MARKET_JD:1"))));
        when(harnessService.verifyBatch(any())).thenReturn(List.of(decision("PASS", new BigDecimal("85"))));

        AdmissionPlan plan = service.admitBatch(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(plan.acceptedTagIdsByJd().get(1L)).containsExactly(10L);
        assertThat(plan.harnessPassCount()).isEqualTo(1);
        assertThat(plan.autoAcceptedCount()).isZero();
        verify(harnessService).verifyBatch(any());
        verify(harnessService, never()).verify(any());
    }

    @Test
    void highConfidenceSemanticMatch_isRecommendedWithoutHarnessOrFormalAdmission() {
        JdExtraction jd = jd(1L, "负责订单系统开发", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("Java", "SIMILAR", 10L, "负责订单系统开发", null, null,
                        List.of("source:MARKET_JD:1"), 0.90)));

        AdmissionPlan plan = service.admitBatch(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(plan.acceptedTagIdsByJd()).doesNotContainKey(1L);
        assertThat(plan.recommendedTagIdsByJd().get(1L)).containsExactly(10L);
        verify(harnessService, never()).verifyBatch(any());
    }

    @Test
    void existingTagBlock_excludedFromPlan() {
        JdExtraction jd = jd(1L, "负责订单系统开发", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("Java", "MATCHED", 10L, "负责订单系统开发", null, null, List.of("source:MARKET_JD:1"))));
        when(harnessService.verifyBatch(any())).thenReturn(List.of(decision("BLOCK", null)));

        AdmissionPlan plan = service.admitBatch(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(plan.acceptedTagIdsByJd()).doesNotContainKey(1L);
        assertThat(plan.harnessBlockedCount()).isEqualTo(1);
    }

    @Test
    void existingTagRetryOnceThenPass_admitted() {
        JdExtraction jd = jd(1L, "负责订单系统开发", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("Java", "MATCHED", 10L, "负责订单系统开发", null, null, List.of("source:MARKET_JD:1"))));
        when(harnessService.verifyBatch(any()))
                .thenReturn(List.of(decision("RETRY", null)))
                .thenReturn(List.of(decision("PASS", new BigDecimal("85"))));

        AdmissionPlan plan = service.admitBatch(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(plan.acceptedTagIdsByJd().get(1L)).containsExactly(10L);
        assertThat(plan.harnessPassCount()).isEqualTo(1);
        verify(harnessService, times(2)).verifyBatch(any()); // 恰好一次重试
    }

    @Test
    void existingTagRetryTwiceStillRetry_dropped() {
        JdExtraction jd = jd(1L, "负责订单系统开发", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("Java", "MATCHED", 10L, "负责订单系统开发", null, null, List.of("source:MARKET_JD:1"))));
        when(harnessService.verifyBatch(any()))
                .thenReturn(List.of(decision("RETRY", null)))
                .thenReturn(List.of(decision("RETRY", null)));

        AdmissionPlan plan = service.admitBatch(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(plan.acceptedTagIdsByJd()).doesNotContainKey(1L);
        assertThat(plan.harnessRetryDroppedCount()).isEqualTo(1);
        assertThat(plan.harnessPassCount()).isZero();
    }

    @Test
    void malformedHarnessResponse_neverAdmits() {
        JdExtraction jd = jd(1L, "负责订单系统开发", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("Java", "MATCHED", 10L, "负责订单系统开发", null, null, List.of("source:MARKET_JD:1"))));
        when(harnessService.verifyBatch(any())).thenReturn(null);

        AdmissionPlan plan = service.admitBatch(new AdmissionBatchRequest("B1", List.of(jd)));

        assertThat(plan.acceptedTagIdsByJd()).doesNotContainKey(1L);
        assertThat(plan.harnessBlockedCount()).isEqualTo(1);
    }

    @Test
    void existingTagClaimCarriesMarketJdFields() {
        JdExtraction jd = jd(1L, "负责订单系统开发", "A公司",
                List.of("source:MARKET_JD:1"),
                List.of(item("Java", "MATCHED", 10L, "负责订单系统开发", null, null, List.of("source:MARKET_JD:1"))));
        when(harnessService.verifyBatch(any())).thenReturn(List.of(decision("PASS", new BigDecimal("85"))));

        service.admitBatch(new AdmissionBatchRequest("B1", List.of(jd)));

        org.mockito.ArgumentCaptor<List<AiHarnessClaimDTO>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(harnessService).verifyBatch(captor.capture());
        AiHarnessClaimDTO claim = captor.getValue().get(0);
        assertThat(claim.getScenario()).isEqualTo("MARKET_JD_ABILITY_ADMISSION");
        assertThat(claim.getClaimType()).isEqualTo("ABILITY_TAG");
        assertThat(claim.getClaimText()).isEqualTo("Java");
        assertThat(claim.getMatchedTagId()).isEqualTo(10L);
        assertThat(claim.getSourceType()).isEqualTo("MARKET_JD");
        assertThat(claim.getSourceRefId()).isEqualTo(1L);
        assertThat(claim.getEvidenceText()).isEqualTo("负责订单系统开发");
        assertThat(claim.getSourceRefs()).containsExactly("source:MARKET_JD:1");
    }

    @Test
    void newGroupPassAboveThreshold_createsFormalTagForAllMembers() {
        List<JdExtraction> jds = eligibleNewAbilityJds();
        AbilityTag formal = new AbilityTag();
        formal.setId(777L);
        formal.setTagName("向量数据库");
        when(verifiedFacade.admitVerifiedNewTag(any(), any()))
                .thenReturn(TagAdmissionResult.created(formal, "PASS", new BigDecimal("90")));
        when(harnessService.verifyBatch(any())).thenReturn(List.of(decision("PASS", new BigDecimal("90"))));

        AdmissionPlan plan = service.admitBatch(new AdmissionBatchRequest("B1", jds));

        assertThat(plan.formalTagCreations()).hasSize(1);
        FormalTagPlan creation = plan.formalTagCreations().get(0);
        assertThat(creation.tagId()).isEqualTo(777L);
        assertThat(creation.sourceType()).isEqualTo("MARKET_JD");
        assertThat(plan.acceptedTagIdsByJd().get(1L)).containsExactly(777L);
        assertThat(plan.acceptedTagIdsByJd().get(2L)).containsExactly(777L);
        assertThat(plan.acceptedTagIdsByJd().get(3L)).containsExactly(777L);
        assertThat(plan.harnessPassCount()).isEqualTo(1);
    }

    @Test
    void newGroupReview_createsOneCandidate() {
        List<JdExtraction> jds = eligibleNewAbilityJds();
        when(verifiedFacade.admitVerifiedNewTag(any(), any()))
                .thenReturn(TagAdmissionResult.candidate(new AbilityTagCandidate(), "REVIEW", null));
        when(harnessService.verifyBatch(any())).thenReturn(List.of(decision("REVIEW", new BigDecimal("60"))));

        AdmissionPlan plan = service.admitBatch(new AdmissionBatchRequest("B1", jds));

        assertThat(plan.reviewCandidateGroupCount()).isEqualTo(1);
        assertThat(plan.formalTagCreations()).isEmpty();
        assertThat(plan.acceptedTagIdsByJd()).doesNotContainKey(1L);
        verify(verifiedFacade).admitVerifiedNewTag(any(), any());
    }

    @Test
    void newGroupBlock_rejectedNoCandidate() {
        List<JdExtraction> jds = eligibleNewAbilityJds();
        when(harnessService.verifyBatch(any())).thenReturn(List.of(decision("BLOCK", null)));

        AdmissionPlan plan = service.admitBatch(new AdmissionBatchRequest("B1", jds));

        assertThat(plan.formalTagCreations()).isEmpty();
        assertThat(plan.reviewCandidateGroupCount()).isZero();
        assertThat(plan.harnessBlockedCount()).isEqualTo(1);
        assertThat(plan.rejectedClaimCount()).isEqualTo(3); // 组内 3 个成员全部拒绝
        verify(verifiedFacade, never()).admitVerifiedNewTag(any(), any());
    }

    @Test
    void reviewCapSuppressesExcessGroups() {
        // 两个不同的新能力组，cap=1：第一组建候选，第二组被抑制（不建候选、不自动通过）
        properties.setReviewMaxGroupsPerBatch(1);
        List<JdExtraction> groupA = eligibleNewAbilityJds();
        List<JdExtraction> groupB = List.of(
                jd(1L, "负责流式计算开发", "A公司", List.of("source:MARKET_JD:1"),
                        List.of(item("流式计算", "NEW", null, "负责流式计算开发", null, null, List.of("source:MARKET_JD:1")))),
                jd(2L, "负责流式计算开发", "A公司", List.of("source:MARKET_JD:2"),
                        List.of(item("流式计算", "NEW", null, "负责流式计算开发", null, null, List.of("source:MARKET_JD:2")))),
                jd(3L, "负责流式计算开发", "B公司", List.of("source:MARKET_JD:3"),
                        List.of(item("流式计算", "NEW", null, "负责流式计算开发", null, null, List.of("source:MARKET_JD:3")))));
        when(verifiedFacade.admitVerifiedNewTag(any(), any()))
                .thenReturn(TagAdmissionResult.candidate(new AbilityTagCandidate(), "REVIEW", null));
        when(harnessService.verifyBatch(any())).thenReturn(List.of(
                decision("REVIEW", new BigDecimal("60")),
                decision("REVIEW", new BigDecimal("60"))));

        List<JdExtraction> allJds = new java.util.ArrayList<>(groupA);
        allJds.addAll(groupB);
        AdmissionPlan plan = service.admitBatch(new AdmissionBatchRequest("B1", allJds));

        assertThat(plan.reviewCandidateGroupCount()).isEqualTo(1);
        assertThat(plan.rejectedClaimCount()).isEqualTo(3); // 第二组 3 成员被 cap 拒绝
        verify(verifiedFacade, times(1)).admitVerifiedNewTag(any(), any());
    }

    // ==================== helpers ====================

    private AiHarnessDecisionDTO decision(String decision, BigDecimal score) {
        AiHarnessDecisionDTO d = new AiHarnessDecisionDTO();
        d.setDecision(decision);
        d.setSupportScore(score);
        return d;
    }

    /** 3 个 JD / 2 家公司的新能力分组（达到双阈值） */
    private List<JdExtraction> eligibleNewAbilityJds() {
        return List.of(
                jd(1L, "负责向量数据库开发", "A公司", List.of("source:MARKET_JD:1"),
                        List.of(item("向量数据库", "NEW", null, "负责向量数据库开发", null, null, List.of("source:MARKET_JD:1")))),
                jd(2L, "负责向量数据库开发", "A公司", List.of("source:MARKET_JD:2"),
                        List.of(item("向量数据库", "NEW", null, "负责向量数据库开发", null, null, List.of("source:MARKET_JD:2")))),
                jd(3L, "负责向量数据库开发", "B公司", List.of("source:MARKET_JD:3"),
                        List.of(item("向量数据库", "NEW", null, "负责向量数据库开发", null, null, List.of("source:MARKET_JD:3")))));
    }

    private AbilityTag enabledTag(Long id, String name) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setTagName(name);
        tag.setStatus(1);
        return tag;
    }

    private JdAbilityItemDTO item(String name, String matchStatus, Long tagId, String evidence,
                                  Integer start, Integer end, List<String> sourceRefs) {
        return item(name, matchStatus, tagId, evidence, start, end, sourceRefs, 0.7);
    }

    private JdAbilityItemDTO item(String name, String matchStatus, Long tagId, String evidence,
                                  Integer start, Integer end, List<String> sourceRefs, double similarityScore) {
        JdAbilityItemDTO item = new JdAbilityItemDTO();
        item.setSuggestedName(name);
        item.setMatchStatus(matchStatus);
        item.setMatchedTagId(tagId);
        item.setEvidenceText(evidence);
        item.setEvidenceStart(start);
        item.setEvidenceEnd(end);
        item.setSourceRefs(sourceRefs);
        item.setConfidenceScore(new BigDecimal("0.9"));
        item.setSimilarityScore(similarityScore);
        return item;
    }

    private JdExtraction jd(Long id, String cleanedText, String companyKey,
                            List<String> serverRefs, List<JdAbilityItemDTO> items) {
        return new JdExtraction(id, cleanedText, companyKey, serverRefs, items);
    }
}
