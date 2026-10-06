package com.example.matching.service.assessment;

import com.example.matching.common.enums.DecisionStatusEnum;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.entity.workflow.PersonAbilityLevelDecision;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.mapper.workflow.PersonAbilityLevelDecisionMapper;
import com.example.matching.service.assessment.impl.AbilityLevelConfirmationServiceImpl;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.service.system.SourceWeightResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 最终能力等级确认中心测试
 * <p>
 * 覆盖：单来源等级上限、冲突进入人工审核、人工确认参数校验与投影推进。
 */
class AbilityLevelConfirmationServiceImplTest {

    private PersonAbilityClaimGroupMapper claimGroupMapper;
    private PersonAbilityLevelDecisionMapper decisionMapper;
    private AbilityEvidenceCollectionService evidenceCollectionService;
    private SourceWeightResolver sourceWeightResolver;
    private AbilityLevelPolicyService policyService;
    private AbilityProfileProjectionService projectionService;
    private CapabilityAssessmentWorkflowService workflowService;
    private CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;
    private AbilityLevelConfirmationService service;

    @BeforeEach
    void setUp() {
        claimGroupMapper = mock(PersonAbilityClaimGroupMapper.class);
        decisionMapper = mock(PersonAbilityLevelDecisionMapper.class);
        evidenceCollectionService = mock(AbilityEvidenceCollectionService.class);
        sourceWeightResolver = mock(SourceWeightResolver.class);
        policyService = mock(AbilityLevelPolicyService.class);
        projectionService = mock(AbilityProfileProjectionService.class);
        workflowService = mock(CapabilityAssessmentWorkflowService.class);
        lifecycleEventPublisher = mock(CapabilityStageLifecycleEventPublisher.class);
        service = new AbilityLevelConfirmationServiceImpl(
                claimGroupMapper, decisionMapper, evidenceCollectionService,
                sourceWeightResolver, policyService, projectionService, workflowService,
                lifecycleEventPublisher);
        // 默认策略
        when(policyService.getActivePolicy()).thenReturn(new AbilityLevelPolicyService.LevelPolicy(
                "level-confirmation-v1", "默认等级确认策略", 2, 2, 0.20,
                new BigDecimal("0.30"), new BigDecimal("0.15"),
                java.util.Map.of("RESUME_PARSE", 2, "AI_TEST", 3, "AI_INTERVIEW", 3)));
    }

    private PersonAbilityClaimGroup group(Long id, Long tagId, String status) {
        PersonAbilityClaimGroup group = new PersonAbilityClaimGroup();
        group.setId(id);
        group.setWorkflowId(1L);
        group.setEmpId(1L);
        group.setNormalizedAbilityName("Java");
        group.setCanonicalTagId(tagId);
        group.setStatus(status);
        return group;
    }

    private PersonAbilityClaim claim(String sourceType, int level, BigDecimal confidence) {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setEmpId(1L);
        claim.setAbilityName("Java");
        claim.setNormalizedAbilityName("Java");
        claim.setClaimedLevel(level);
        claim.setSourceType(sourceType);
        claim.setEvidenceText("负责Java后端开发，完成高并发订单模块设计与实现");
        claim.setSourceRefsJson("[\"source:" + sourceType + ":100\"]");
        claim.setConfidenceScore(confidence);
        claim.setStatus("ACTIVE");
        return claim;
    }

    @Test
    void confirmLevels_afterHarnessPassAppliesSingleSourceCeilingAndAutoConfirms() {
        PersonAbilityClaimGroup group = group(1L, 10L, EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode());
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group));
        // 仅简历来源声明 L4（单来源上限 2）
        when(evidenceCollectionService.listClaimsByGroup(1L))
                .thenReturn(List.of(claim("RESUME_PARSE", 4, BigDecimal.valueOf(80))));
        when(sourceWeightResolver.resolveEffectiveWeight("RESUME_PARSE")).thenReturn(new BigDecimal("0.15"));
        when(sourceWeightResolver.resolveCredibility("RESUME_PARSE")).thenReturn(0.15);
        when(decisionMapper.selectOne(any())).thenReturn(null);
        PersonAbilityLevelDecision[] captured = new PersonAbilityLevelDecision[1];
        when(decisionMapper.insert(any(PersonAbilityLevelDecision.class))).thenAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            captured[0].setId(900L);
            return 1;
        });
        when(claimGroupMapper.updateById((PersonAbilityClaimGroup) any())).thenReturn(1);

        service.confirmLevels(1L, 600L);

        PersonAbilityLevelDecision decision = captured[0];
        // Harness 是唯一人工审核闸门；进入最终等级中心后只计算等级并自动入库。
        assertThat(decision.getFinalLevel()).isLessThanOrEqualTo(2);
        assertThat(decision.getDecisionStatus()).isEqualTo(DecisionStatusEnum.AUTO_CONFIRMED.getCode());
        assertThat(decision.getDecisionReasonCodesJson()).contains("SINGLE_SOURCE_CEILING");
        assertThat(decision.getDecisionReasonCodesJson()).contains("HARNESS_APPROVED_FUSION");
        assertThat(decision.getPolicyVersion()).isEqualTo("level-confirmation-v1");
        assertThat(decision.getPolicySnapshotJson()).contains("singleSourceLevelCeiling");
    }

    @Test
    void confirmLevels_afterHarnessPassAutoConfirmsEvenWhenClaimsHaveConflictSignal() {
        PersonAbilityClaimGroup group = group(2L, 10L, EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode());
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group));
        // 简历 L2 vs 测试 L4：等级差 2 >= 冲突阈值
        when(evidenceCollectionService.listClaimsByGroup(2L)).thenReturn(List.of(
                claim("RESUME_PARSE", 2, BigDecimal.valueOf(80)),
                claim("AI_TEST", 4, BigDecimal.valueOf(85))));
        when(sourceWeightResolver.resolveEffectiveWeight("RESUME_PARSE")).thenReturn(new BigDecimal("0.15"));
        when(sourceWeightResolver.resolveEffectiveWeight("AI_TEST")).thenReturn(new BigDecimal("0.20"));
        when(sourceWeightResolver.resolveCredibility("RESUME_PARSE")).thenReturn(0.15);
        when(sourceWeightResolver.resolveCredibility("AI_TEST")).thenReturn(0.20);
        when(decisionMapper.selectOne(any())).thenReturn(null);
        PersonAbilityLevelDecision[] captured = new PersonAbilityLevelDecision[1];
        when(decisionMapper.insert(any(PersonAbilityLevelDecision.class))).thenAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            return 1;
        });
        when(claimGroupMapper.updateById((PersonAbilityClaimGroup) any())).thenReturn(1);

        service.confirmLevels(1L, 600L);

        assertThat(captured[0].getDecisionStatus())
                .isEqualTo(DecisionStatusEnum.AUTO_CONFIRMED.getCode());
        assertThat(captured[0].getConflictSignalsJson()).contains("等级冲突");
        assertThat(captured[0].getDecisionReasonCodesJson()).contains("CONFLICT_DETECTED");
        assertThat(captured[0].getDecisionReasonCodesJson()).contains("HARNESS_APPROVED_FUSION");
    }

    /**
     * 按**生产真实权重**装配权重解析：有效权重 = 可配置权重(统一 10) × 可信度。
     * <p>
     * 可信度取自 {@code AbilitySourceCredibility}：
     * 简历 0.70 / AI 测试 0.90 / AI 面试 0.88。
     * 除以 10 得到与生产同口径的有效权重（简历 0.70、测试 0.90、面试 0.88），
     * 这样断言里的手算就与线上一致，而不是测试自造的等权简化。
     */
    private void stubProductionWeights() {
        when(sourceWeightResolver.resolveEffectiveWeight("RESUME_PARSE"))
                .thenReturn(new BigDecimal("0.70"));
        when(sourceWeightResolver.resolveEffectiveWeight("AI_TEST"))
                .thenReturn(new BigDecimal("0.90"));
        when(sourceWeightResolver.resolveEffectiveWeight("AI_INTERVIEW"))
                .thenReturn(new BigDecimal("0.88"));
        when(sourceWeightResolver.resolveCredibility("RESUME_PARSE")).thenReturn(0.70);
        when(sourceWeightResolver.resolveCredibility("AI_TEST")).thenReturn(0.90);
        when(sourceWeightResolver.resolveCredibility("AI_INTERVIEW")).thenReturn(0.88);
    }

    /** 捕获 decisionMapper.insert 写入的决策对象 */
    private PersonAbilityLevelDecision captureDecision() {
        when(decisionMapper.selectOne(any())).thenReturn(null);
        PersonAbilityLevelDecision[] captured = new PersonAbilityLevelDecision[1];
        when(decisionMapper.insert(any(PersonAbilityLevelDecision.class))).thenAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            return 1;
        });
        when(claimGroupMapper.updateById((PersonAbilityClaimGroup) any())).thenReturn(1);
        service.confirmLevels(1L, 600L);
        return captured[0];
    }

    @Test
    void confirmLevels_unverifiedInterviewCountsAsZeroWeightInDenominator() {
        // 用户规则（真实权重）：简历 3 / 测试 2 / 面试未核验
        //   简历 0.70×3 = 2.10
        //   测试 0.90×2 = 1.80
        //   面试 0.88×0 = 0.00（未核验 → 按 0 计，但权重仍进分母）
        //   加权 = (2.10 + 1.80 + 0) / (0.70 + 0.90 + 0.88) = 3.90 / 2.48 = 1.5726 → 2 级
        PersonAbilityClaimGroup group = group(11L, null, EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode());
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group));
        when(evidenceCollectionService.listClaimsByGroup(11L)).thenReturn(List.of(
                claim("RESUME_PARSE", 3, BigDecimal.valueOf(80)),
                claim("AI_TEST", 2, BigDecimal.valueOf(80))));
        stubProductionWeights();

        PersonAbilityLevelDecision decision = captureDecision();

        assertThat(decision.getFinalLevel()).isEqualTo(2);
    }

    @Test
    void confirmLevels_higherClaimedLevelsWouldBeInflatedWithoutZeroWeighting() {
        // 这条用例专门证明「按 0 计入」不是可有可无的：等级越高，差异越明显。
        // 简历 5 / 测试 5 / 面试未核验，真实权重：
        //   新行为（面试按 0 计）：(0.70×5 + 0.90×5 + 0) / 2.48 = 8.00 / 2.48 = 3.2258 → 3 级
        //   旧行为（面试不进分母）：(0.70×5 + 0.90×5) / 1.60 = 8.00 / 1.60 = 5.0000 → 5 级（虚高）
        PersonAbilityClaimGroup group = group(14L, null, EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode());
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group));
        when(evidenceCollectionService.listClaimsByGroup(14L)).thenReturn(List.of(
                claim("RESUME_PARSE", 5, BigDecimal.valueOf(80)),
                claim("AI_TEST", 5, BigDecimal.valueOf(80))));
        stubProductionWeights();

        PersonAbilityLevelDecision decision = captureDecision();

        // 旧实现对同一输入会给出 5；修复后应被拉到 3
        assertThat(decision.getFinalLevel())
                .as("面试未核验必须按 0 计入，避免等级被高估")
                .isEqualTo(3);
    }

    @Test
    void confirmLevels_finalLevelNeverDropsBelowOne() {
        // 极端场景：简历 L1 + 测试未核验 + 面试未核验
        //   (0.70×1 + 0 + 0) / 2.48 = 0.2823 → 四舍五入 0，但下限钳制为 1
        PersonAbilityClaimGroup group = group(12L, null, EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode());
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group));
        when(evidenceCollectionService.listClaimsByGroup(12L))
                .thenReturn(List.of(claim("RESUME_PARSE", 1, BigDecimal.valueOf(80))));
        stubProductionWeights();

        PersonAbilityLevelDecision decision = captureDecision();

        assertThat(decision.getFinalLevel()).isEqualTo(1);
    }

    @Test
    void confirmLevels_withoutResumeBaselineKeepsOriginalBehaviour() {
        // 无简历基准时不做「按 0 补齐」：避免把"漏提取导致的全空"也当 0 分拉低。
        PersonAbilityClaimGroup group = group(13L, null, EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode());
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group));
        when(evidenceCollectionService.listClaimsByGroup(13L))
                .thenReturn(List.of(claim("AI_TEST", 3, BigDecimal.valueOf(80))));
        stubProductionWeights();

        PersonAbilityLevelDecision decision = captureDecision();

        // 只有测试来源：保持原算法结果（取该来源等级）
        assertThat(decision.getFinalLevel()).isEqualTo(3);
    }

    @Test
    void confirmLevels_allThreeSourcesVerifiedUsesFullFusion() {
        // 三方齐备（可自动入库的场景）：简历3 / 测试2 / 面试3
        //   (0.70×3 + 0.90×2 + 0.88×3) / 2.48 = (2.10 + 1.80 + 2.64) / 2.48 = 6.54/2.48 = 2.6371 → 3 级
        PersonAbilityClaimGroup group = group(15L, null, EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode());
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group));
        when(evidenceCollectionService.listClaimsByGroup(15L)).thenReturn(List.of(
                claim("RESUME_PARSE", 3, BigDecimal.valueOf(80)),
                claim("AI_TEST", 2, BigDecimal.valueOf(80)),
                claim("AI_INTERVIEW", 3, BigDecimal.valueOf(80))));
        stubProductionWeights();

        PersonAbilityLevelDecision decision = captureDecision();

        assertThat(decision.getFinalLevel()).isEqualTo(3);
    }

    @Test
    void confirmLevels_validTestAndInterviewVerificationAutoConfirmsUsingExistingFusion() {
        PersonAbilityClaimGroup group = group(3L, null, EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode());
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group));
        when(evidenceCollectionService.listClaimsByGroup(3L)).thenReturn(List.of(
                claim("RESUME_PARSE", 3, BigDecimal.valueOf(80)),
                claim("AI_TEST", 2, BigDecimal.valueOf(85)),
                claim("AI_INTERVIEW", 3, BigDecimal.valueOf(90))));
        when(sourceWeightResolver.resolveEffectiveWeight("RESUME_PARSE")).thenReturn(new BigDecimal("0.15"));
        when(sourceWeightResolver.resolveEffectiveWeight("AI_TEST")).thenReturn(new BigDecimal("0.20"));
        when(sourceWeightResolver.resolveEffectiveWeight("AI_INTERVIEW")).thenReturn(new BigDecimal("0.25"));
        when(sourceWeightResolver.resolveCredibility("RESUME_PARSE")).thenReturn(0.15);
        when(sourceWeightResolver.resolveCredibility("AI_TEST")).thenReturn(0.20);
        when(sourceWeightResolver.resolveCredibility("AI_INTERVIEW")).thenReturn(0.25);
        when(decisionMapper.selectOne(any())).thenReturn(null);
        PersonAbilityLevelDecision[] captured = new PersonAbilityLevelDecision[1];
        when(decisionMapper.insert(any(PersonAbilityLevelDecision.class))).thenAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return 1;
        });
        when(claimGroupMapper.updateById((PersonAbilityClaimGroup) any())).thenReturn(1);

        service.confirmLevels(1L, 600L);

        assertThat(captured[0].getDecisionStatus()).isEqualTo(DecisionStatusEnum.AUTO_CONFIRMED.getCode());
        assertThat(captured[0].getFinalLevel()).isEqualTo(3);
        assertThat(captured[0].getDecisionReasonCodesJson()).contains("HARNESS_APPROVED_FUSION");
    }

    @Test
    void confirmLevels_reliesOnEarlierHarnessGateAndAutoConfirmsEnteredClaims() {
        PersonAbilityClaimGroup group = group(4L, null, EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode());
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group));
        PersonAbilityClaim invalidResume = claim("RESUME_PARSE", 3, BigDecimal.valueOf(80));
        invalidResume.setEvidenceText(" ");
        invalidResume.setSourceRefsJson("[]");
        when(evidenceCollectionService.listClaimsByGroup(4L)).thenReturn(List.of(invalidResume));
        when(sourceWeightResolver.resolveEffectiveWeight("RESUME_PARSE")).thenReturn(new BigDecimal("0.15"));
        when(sourceWeightResolver.resolveCredibility("RESUME_PARSE")).thenReturn(0.15);
        when(decisionMapper.selectOne(any())).thenReturn(null);
        PersonAbilityLevelDecision[] captured = new PersonAbilityLevelDecision[1];
        when(decisionMapper.insert(any(PersonAbilityLevelDecision.class))).thenAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return 1;
        });
        when(claimGroupMapper.updateById((PersonAbilityClaimGroup) any())).thenReturn(1);

        service.confirmLevels(1L, 600L);

        assertThat(captured[0].getDecisionStatus()).isEqualTo(DecisionStatusEnum.AUTO_CONFIRMED.getCode());
        assertThat(captured[0].getFinalLevel()).isEqualTo(2);
        assertThat(captured[0].getDecisionReasonCodesJson()).contains("HARNESS_APPROVED_FUSION");
    }

    @Test
    void humanConfirm_rejectsInvalidLevelAndNonPendingDecision() {
        PersonAbilityLevelDecision decision = new PersonAbilityLevelDecision();
        decision.setId(1L);
        decision.setWorkflowId(1L);
        decision.setClaimGroupId(1L);
        decision.setDecisionStatus(DecisionStatusEnum.AUTO_CONFIRMED.getCode());
        when(decisionMapper.selectById(1L)).thenReturn(decision);

        // 非 PENDING_MANUAL_REVIEW 状态不可确认
        assertThatThrownBy(() -> service.humanConfirm(1L, 3, 70, "复核通过", 9L))
                .isInstanceOf(IllegalStateException.class);

        // finalLevel 越界校验
        PersonAbilityLevelDecision pending = new PersonAbilityLevelDecision();
        pending.setId(2L);
        pending.setWorkflowId(1L);
        pending.setClaimGroupId(1L);
        pending.setDecisionStatus(DecisionStatusEnum.PENDING_MANUAL_REVIEW.getCode());
        when(decisionMapper.selectById(2L)).thenReturn(pending);
        assertThatThrownBy(() -> service.humanConfirm(2L, 6, 70, "复核", 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1-5");
    }

    @Test
    void humanConfirm_projectsAndPublishesLifecycleEventWhenNoPendingLeft() {
        PersonAbilityLevelDecision pending = new PersonAbilityLevelDecision();
        pending.setId(3L);
        pending.setWorkflowId(1L);
        pending.setClaimGroupId(1L);
        pending.setDecisionStatus(DecisionStatusEnum.PENDING_MANUAL_REVIEW.getCode());
        when(decisionMapper.selectById(3L)).thenReturn(pending);
        when(decisionMapper.updateById((PersonAbilityLevelDecision) any())).thenReturn(1);
        PersonAbilityClaimGroup group = group(1L, 10L, EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode());
        when(claimGroupMapper.selectById(1L)).thenReturn(group);
        when(claimGroupMapper.updateById((PersonAbilityClaimGroup) any())).thenReturn(1);
        // 确认后无剩余待复核决策 -> 投影 + 发布生命周期事件（协调器推进 COMPLETED）
        when(decisionMapper.selectList(any())).thenReturn(List.of(pending));

        service.humanConfirm(3L, 3, 70, "复核通过", 9L);

        verify(projectionService).projectConfirmed(1L, 9L);
        verify(workflowService, never()).completeWorkflow(org.mockito.ArgumentMatchers.any());
        org.mockito.ArgumentCaptor<com.example.matching.event.CapabilityStageLifecycleEvent> captor =
                org.mockito.ArgumentCaptor.forClass(com.example.matching.event.CapabilityStageLifecycleEvent.class);
        verify(lifecycleEventPublisher).publish(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1L, captor.getValue().workflowId());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.example.matching.common.enums.StageLifecycleEventType.USER_ACTION_COMPLETED,
                captor.getValue().eventType());
        org.junit.jupiter.api.Assertions.assertEquals("AGGREGATE_HARNESS", captor.getValue().stageType());
    }

    /* ============ listDecisionsByEmp：按员工聚合最终等级结论（全方位报告 C1a） ============ */

    @Test
    void listDecisionsByEmp_keepsLatestDecisionPerAbilityTag() {
        // 同一 tagId 有两条历史决策（重审/按新策略重算），查询已按 updatedTime 倒序
        PersonAbilityLevelDecision newer = decision(11L, 7L, 101L, 5, LocalDateTime.of(2026, 9, 30, 10, 0));
        PersonAbilityLevelDecision older = decision(10L, 7L, 101L, 3, LocalDateTime.of(2026, 9, 1, 10, 0));
        when(decisionMapper.selectList(any())).thenReturn(List.of(newer, older));

        List<PersonAbilityLevelDecision> result = service.listDecisionsByEmp(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(11L);
        assertThat(result.get(0).getFinalLevel()).isEqualTo(5);
    }

    @Test
    void listDecisionsByEmp_keepsDecisionsWithoutTagSeparately() {
        // tagId 为空的聚合能力不参与去重，按 id 各自保留
        PersonAbilityLevelDecision first = decision(1L, 7L, null, 4, LocalDateTime.of(2026, 9, 30, 10, 0));
        PersonAbilityLevelDecision second = decision(2L, 7L, null, 3, LocalDateTime.of(2026, 9, 29, 10, 0));
        when(decisionMapper.selectList(any())).thenReturn(List.of(first, second));

        assertThat(service.listDecisionsByEmp(7L)).hasSize(2);
    }

    @Test
    void listDecisionsByEmp_nullEmpReturnsEmptyWithoutQuery() {
        assertThat(service.listDecisionsByEmp(null)).isEmpty();
        verify(decisionMapper, never()).selectList(any());
    }

    private PersonAbilityLevelDecision decision(Long id, Long empId, Long tagId,
                                                Integer finalLevel, LocalDateTime updatedAt) {
        PersonAbilityLevelDecision decision = new PersonAbilityLevelDecision();
        decision.setId(id);
        decision.setEmpId(empId);
        decision.setTagId(tagId);
        decision.setFinalLevel(finalLevel);
        decision.setUpdatedTime(updatedAt);
        return decision;
    }
}
