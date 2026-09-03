package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.common.enums.StageTypeEnum;
import com.example.matching.entity.workflow.AbilityHarnessBatchItem;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.mapper.workflow.AbilityHarnessBatchItemMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.service.assessment.AbilityLevelConfirmationService;
import com.example.matching.service.assessment.AbilityProfileProjectionService;
import com.example.matching.service.assessment.AggregateAbilityHarnessReviewService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Resolves manual aggregate-harness reviews without opening a second business stage.
 */
@Slf4j
@Service
public class AggregateAbilityHarnessReviewServiceImpl implements AggregateAbilityHarnessReviewService {

    private final AbilityHarnessBatchItemMapper batchItemMapper;
    private final PersonAbilityClaimGroupMapper claimGroupMapper;
    private final CapabilityAssessmentWorkflowService workflowService;
    private final AbilityLevelConfirmationService levelConfirmationService;
    private final AbilityProfileProjectionService projectionService;
    private final CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;

    public AggregateAbilityHarnessReviewServiceImpl(
            AbilityHarnessBatchItemMapper batchItemMapper,
            PersonAbilityClaimGroupMapper claimGroupMapper,
            CapabilityAssessmentWorkflowService workflowService,
            AbilityLevelConfirmationService levelConfirmationService,
            AbilityProfileProjectionService projectionService,
            CapabilityStageLifecycleEventPublisher lifecycleEventPublisher) {
        this.batchItemMapper = batchItemMapper;
        this.claimGroupMapper = claimGroupMapper;
        this.workflowService = workflowService;
        this.levelConfirmationService = levelConfirmationService;
        this.projectionService = projectionService;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
    }

    @Override
    public boolean isAggregateHarnessReview(Long harnessLogId) {
        if (harnessLogId == null) return false;
        Long count = batchItemMapper.selectCount(new LambdaQueryWrapper<AbilityHarnessBatchItem>()
                .eq(AbilityHarnessBatchItem::getHarnessLogId, harnessLogId));
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void acceptAndProject(Long harnessLogId, String reviewComment) {
        PersonAbilityClaimGroup group = loadGroup(harnessLogId);
        group.setStatus(EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode());
        group.setUpdatedTime(LocalDateTime.now());
        claimGroupMapper.updateById(group);
        // Each final Harness approval is independently projectable. Do not hold
        // an approved ability hostage to unrelated review items in the same batch.
        PersonCapabilityStageRun harnessRun = workflowService.getLatestStageRun(
                group.getWorkflowId(), StageTypeEnum.AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION.getCode());
        levelConfirmationService.confirmLevels(group.getWorkflowId(), harnessRun != null ? harnessRun.getId() : null);
        projectionService.projectConfirmed(group.getWorkflowId(), null);
        finalizeIfAllReviewsResolved(group, harnessLogId, "HUMAN_ACCEPTED");
    }

    @Override
    @Transactional
    public void rejectAndFinalize(Long harnessLogId, String reviewComment) {
        PersonAbilityClaimGroup group = loadGroup(harnessLogId);
        group.setStatus(EvidenceStatusEnum.BLOCKED.getCode());
        group.setUpdatedTime(LocalDateTime.now());
        claimGroupMapper.updateById(group);
        finalizeIfAllReviewsResolved(group, harnessLogId, "HUMAN_REJECTED");
    }

    private PersonAbilityClaimGroup loadGroup(Long harnessLogId) {
        AbilityHarnessBatchItem item = batchItemMapper.selectOne(new LambdaQueryWrapper<AbilityHarnessBatchItem>()
                .eq(AbilityHarnessBatchItem::getHarnessLogId, harnessLogId)
                .last("LIMIT 1"));
        if (item == null) {
            throw new IllegalStateException("聚合 Harness 审核项不存在: harnessLogId=" + harnessLogId);
        }
        PersonAbilityClaimGroup group = claimGroupMapper.selectById(item.getClaimGroupId());
        if (group == null) {
            throw new IllegalStateException("聚合能力组不存在: claimGroupId=" + item.getClaimGroupId());
        }
        return group;
    }

    private void finalizeIfAllReviewsResolved(PersonAbilityClaimGroup group, Long harnessLogId, String resolution) {
        Long pending = claimGroupMapper.selectCount(new LambdaQueryWrapper<PersonAbilityClaimGroup>()
                .eq(PersonAbilityClaimGroup::getWorkflowId, group.getWorkflowId())
                .eq(PersonAbilityClaimGroup::getStatus, EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode()));
        if (pending != null && pending > 0) {
            log.info("聚合 Harness 仍有待审核项，暂不收尾: workflowId={}, pending={}",
                    group.getWorkflowId(), pending);
            return;
        }

        PersonCapabilityStageRun harnessRun = workflowService.getLatestStageRun(
                group.getWorkflowId(), StageTypeEnum.AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION.getCode());
        if (harnessRun == null) {
            log.warn("聚合 Harness 没有可收尾的阶段运行: workflowId={}, claimGroupId={}, resolution={}",
                    group.getWorkflowId(), group.getId(), resolution);
            return;
        }

        // 人工审核属于已完成评估后的独立治理动作；通过项已经在 acceptAndProject
        // 中完成正式投影，不再尝试推进或重新打开原评估工作流。
        log.info("聚合 Harness 审核队列已收尾: harnessLogId={}, workflowId={}, claimGroupId={}, resolution={}",
                harnessLogId, group.getWorkflowId(), group.getId(), resolution);
    }
}
