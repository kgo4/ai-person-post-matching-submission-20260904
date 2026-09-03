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
    void confirmLevels_onlyResumeEvidenceRequiresManualReview() {
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
        // 仅简历证据虽经聚合 Harness 通过，但缺少测试和面试双核验，必须进入同一轮人工审核。
        assertThat(decision.getFinalLevel()).isLessThanOrEqualTo(2);
        assertThat(decision.getDecisionStatus()).isEqualTo(DecisionStatusEnum.PENDING_MANUAL_REVIEW.getCode());
        assertThat(decision.getDecisionReasonCodesJson()).contains("REVIEW_MISSING_TEST_VERIFICATION");
        assertThat(decision.getDecisionReasonCodesJson()).contains("REVIEW_MISSING_INTERVIEW_VERIFICATION");
        assertThat(decision.getPolicyVersion()).isEqualTo("level-confirmation-v1");
        assertThat(decision.getPolicySnapshotJson()).contains("singleSourceLevelCeiling");
    }

    @Test
    void confirmLevels_onlyTestVerificationRequiresManualReview() {
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
                .isEqualTo(DecisionStatusEnum.PENDING_MANUAL_REVIEW.getCode());
        assertThat(captured[0].getConflictSignalsJson()).contains("等级冲突");
        assertThat(captured[0].getDecisionReasonCodesJson()).contains("REVIEW_MISSING_INTERVIEW_VERIFICATION");
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
        assertThat(captured[0].getDecisionReasonCodesJson()).contains("AUTO_PASS_DUAL_VERIFICATION");
    }

    @Test
    void confirmLevels_withoutResumeEvidenceOrVerificationBlocksDecision() {
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

        assertThat(captured[0].getDecisionStatus()).isEqualTo(DecisionStatusEnum.REJECTED.getCode());
        assertThat(captured[0].getFinalLevel()).isNull();
        assertThat(captured[0].getDecisionReasonCodesJson()).contains("BLOCK_NO_VALID_EVIDENCE_OR_VERIFICATION");
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
}
