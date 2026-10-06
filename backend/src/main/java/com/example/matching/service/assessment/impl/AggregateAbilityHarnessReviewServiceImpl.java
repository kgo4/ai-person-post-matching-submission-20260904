package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.common.enums.StageTypeEnum;
import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.entity.workflow.AbilityHarnessBatchItem;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.event.AbilityChangeEvent;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.mapper.workflow.AbilityHarnessBatchItemMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.service.assessment.AbilityLevelConfirmationService;
import com.example.matching.service.assessment.AbilityProfileProjectionService;
import com.example.matching.service.assessment.AggregateAbilityHarnessReviewService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    public AggregateAbilityHarnessReviewServiceImpl(
            AbilityHarnessBatchItemMapper batchItemMapper,
            PersonAbilityClaimGroupMapper claimGroupMapper,
            CapabilityAssessmentWorkflowService workflowService,
            AbilityLevelConfirmationService levelConfirmationService,
            AbilityProfileProjectionService projectionService,
            CapabilityStageLifecycleEventPublisher lifecycleEventPublisher,
            ApplicationEventPublisher eventPublisher) {
        this.batchItemMapper = batchItemMapper;
        this.claimGroupMapper = claimGroupMapper;
        this.workflowService = workflowService;
        this.levelConfirmationService = levelConfirmationService;
        this.projectionService = projectionService;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
        this.eventPublisher = eventPublisher;
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
        // 能力已正式融合进画像，发布能力变更事件：
        // 除既有的图谱/向量同步（AbilityChangeListener）外，让「全面能力分析报告」
        // （CapabilityAnalysisReportListener）也能被触发。
        // 【2026-09-04 修复】此前聚合链路不发布任何事件（单条 claim 准入链路才有），
        // 导致 HR 把能力项全部审核通过后报告仍不会自动生成、只显示「暂无能力分析报告」。
        // 各监听器均为 AFTER_COMMIT，故在事务内发布即可。
        if (group.getEmpId() != null) {
            eventPublisher.publishEvent(new AbilityChangeEvent(this, "EMP_ABILITY", group.getEmpId()));
        }
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

        triggerReportFinalization(group.getWorkflowId(), harnessRun.getId(), harnessLogId);
    }

    /**
     * 审核队列清空 → 触发报告定稿（结论区块回填 + AI 洞察生成）。
     *
     * <p><b>【2026-09-04 修复】为什么必须在这里发</b>：报告的「最终能力等级」与「AI 洞察」
     * 两块内容此前一直是空的 ——
     * <ul>
     *   <li>{@code AssessmentReportService#refreshLevelConclusion} 没有任何生产调用点
     *       （只有单测调过），{@code level_summary_json} 恒为 null；</li>
     *   <li>AI 洞察的触发依赖 {@code USER_ACTION_COMPLETED} 生命周期事件，
     *       而 HR 在治理队列里逐条点「通过」并不经过阶段运行器，事件压根不会发。</li>
     * </ul>
     * 治理队列的收尾点正是「该次评估的所有人工复核都已解决」的唯一时刻，放在这里能一次兜住两者。
     *
     * <p><b>为什么只在工作流已 COMPLETED 时发</b>：该事件对终态工作流会被协调器直接拒收
     * （见 {@code CapabilityAssessmentLifecycleCoordinatorImpl}「终态工作流拒绝事件」分支），
     * 因此**不会改动任何流程状态**；若流程尚未收口，报告本就不该对员工可见，
     * 此时不发，避免留下一个"内容还不完整但已放行"的状态。
     */
    private void triggerReportFinalization(Long workflowId, Long harnessRunId, Long harnessLogId) {
        try {
            PersonCapabilityWorkflow workflow = workflowService.getWorkflow(workflowId);
            if (workflow == null
                    || !WorkflowStatusEnum.COMPLETED.getCode().equals(workflow.getStatus())) {
                log.info("工作流尚未收口，暂不触发报告定稿: workflowId={}, status={}",
                        workflowId, workflow == null ? null : workflow.getStatus());
                return;
            }
            lifecycleEventPublisher.publish(CapabilityStageLifecycleEvent.of(
                    workflowId,
                    harnessRunId,
                    StageTypeEnum.AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION.getCode(),
                    StageTypeEnum.AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION.getCode(),
                    harnessLogId,
                    StageLifecycleEventType.USER_ACTION_COMPLETED,
                    null, null));
        } catch (Exception e) {
            // 报告定稿失败不能反过来影响人工审核结果（审核本身已落库）
            log.warn("触发报告定稿失败: workflowId={}, error={}", workflowId, e.getMessage());
        }
    }
}


