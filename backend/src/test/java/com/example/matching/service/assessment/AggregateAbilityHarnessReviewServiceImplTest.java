package com.example.matching.service.assessment;

import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.AbilityHarnessBatchItem;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.workflow.AbilityHarnessBatchItemMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.service.assessment.impl.AggregateAbilityHarnessReviewServiceImpl;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AggregateAbilityHarnessReviewServiceImplTest {

    @Test
    void acceptAndProject_returnsAggregateGroupToFusionAndProjectsIt() {
        AbilityHarnessBatchItemMapper batchItemMapper = mock(AbilityHarnessBatchItemMapper.class);
        PersonAbilityClaimGroupMapper groupMapper = mock(PersonAbilityClaimGroupMapper.class);
        CapabilityAssessmentWorkflowService workflowService = mock(CapabilityAssessmentWorkflowService.class);
        AbilityLevelConfirmationService levelConfirmationService = mock(AbilityLevelConfirmationService.class);
        AbilityProfileProjectionService projectionService = mock(AbilityProfileProjectionService.class);
        com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher publisher =
                mock(com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher =
                mock(org.springframework.context.ApplicationEventPublisher.class);
        AggregateAbilityHarnessReviewService service = new AggregateAbilityHarnessReviewServiceImpl(
                batchItemMapper, groupMapper, workflowService, levelConfirmationService, projectionService, publisher,
                eventPublisher);

        AbilityHarnessBatchItem item = new AbilityHarnessBatchItem();
        item.setClaimGroupId(265L);
        item.setDecision("REVIEW");
        when(batchItemMapper.selectOne(any())).thenReturn(item);
        PersonAbilityClaimGroup group = new PersonAbilityClaimGroup();
        group.setId(265L);
        group.setWorkflowId(20L);
        group.setStatus(EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode());
        when(groupMapper.selectById(265L)).thenReturn(group);
        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setId(88L);
        stageRun.setWorkflowId(20L);
        stageRun.setStageType("AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION");
        when(workflowService.getLatestStageRun(20L, "AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION")).thenReturn(stageRun);

        service.acceptAndProject(101L, "approved by HR");

        verify(groupMapper).updateById(group);
        org.junit.jupiter.api.Assertions.assertEquals(EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode(), group.getStatus());
        verify(levelConfirmationService).confirmLevels(20L, 88L);
        verify(projectionService).projectConfirmed(20L, null);
        // 最后一处待审项已被处理（selectCount 默认 0），但工作流尚未收口
        // → 不发「报告定稿」事件：报告此时本就不该对员工放行。
        when(workflowService.getWorkflow(20L)).thenReturn(null);
        verify(publisher, never()).publish(any());
    }

    /**
     * 审核队列清空 + 工作流已收口 → 补发 {@code USER_ACTION_COMPLETED}，
     * 让报告定稿（最终能力等级 / AI 洞察）在不经阶段运行器的人工审核链路上也能触发。
     *
     * <p>回归背景：HR 在治理队列逐条点「通过」不经过阶段运行器，此前不发任何生命周期事件，
     * 导致「最终能力等级」与「AI 洞察」两块内容永远是空的。
     */
    @Test
    void acceptAndProject_publishesReportFinalizationEvent_whenWorkflowAlreadyCompleted() {
        AbilityHarnessBatchItemMapper batchItemMapper = mock(AbilityHarnessBatchItemMapper.class);
        PersonAbilityClaimGroupMapper groupMapper = mock(PersonAbilityClaimGroupMapper.class);
        CapabilityAssessmentWorkflowService workflowService = mock(CapabilityAssessmentWorkflowService.class);
        AbilityLevelConfirmationService levelConfirmationService = mock(AbilityLevelConfirmationService.class);
        AbilityProfileProjectionService projectionService = mock(AbilityProfileProjectionService.class);
        com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher publisher =
                mock(com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher =
                mock(org.springframework.context.ApplicationEventPublisher.class);
        AggregateAbilityHarnessReviewService service = new AggregateAbilityHarnessReviewServiceImpl(
                batchItemMapper, groupMapper, workflowService, levelConfirmationService, projectionService, publisher,
                eventPublisher);

        AbilityHarnessBatchItem item = new AbilityHarnessBatchItem();
        item.setClaimGroupId(265L);
        when(batchItemMapper.selectOne(any())).thenReturn(item);
        PersonAbilityClaimGroup group = new PersonAbilityClaimGroup();
        group.setId(265L);
        group.setWorkflowId(20L);
        group.setStatus(EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode());
        when(groupMapper.selectById(265L)).thenReturn(group);
        // 队列已清空（最后一处待审项刚被处理）
        when(groupMapper.selectCount(any())).thenReturn(0L);
        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setId(88L);
        stageRun.setWorkflowId(20L);
        stageRun.setStageType("AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION");
        when(workflowService.getLatestStageRun(20L, "AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION")).thenReturn(stageRun);
        com.example.matching.entity.workflow.PersonCapabilityWorkflow workflow =
                new com.example.matching.entity.workflow.PersonCapabilityWorkflow();
        workflow.setId(20L);
        workflow.setStatus(com.example.matching.common.enums.WorkflowStatusEnum.COMPLETED.getCode());
        when(workflowService.getWorkflow(20L)).thenReturn(workflow);

        service.acceptAndProject(101L, "approved by HR");

        org.mockito.ArgumentCaptor<com.example.matching.event.CapabilityStageLifecycleEvent> captor =
                org.mockito.ArgumentCaptor.forClass(com.example.matching.event.CapabilityStageLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(20L, captor.getValue().workflowId());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.example.matching.common.enums.StageLifecycleEventType.USER_ACTION_COMPLETED,
                captor.getValue().eventType(),
                "报告定稿靠 USER_ACTION_COMPLETED 触发（最终能力等级 + AI 洞察）");
    }

    @Test
    void acceptAndProject_projectsApprovedAbilityBeforeOtherHarnessReviewsFinish() {
        AbilityHarnessBatchItemMapper batchItemMapper = mock(AbilityHarnessBatchItemMapper.class);
        PersonAbilityClaimGroupMapper groupMapper = mock(PersonAbilityClaimGroupMapper.class);
        CapabilityAssessmentWorkflowService workflowService = mock(CapabilityAssessmentWorkflowService.class);
        AbilityLevelConfirmationService levelConfirmationService = mock(AbilityLevelConfirmationService.class);
        AbilityProfileProjectionService projectionService = mock(AbilityProfileProjectionService.class);
        com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher publisher =
                mock(com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher =
                mock(org.springframework.context.ApplicationEventPublisher.class);
        AggregateAbilityHarnessReviewService service = new AggregateAbilityHarnessReviewServiceImpl(
                batchItemMapper, groupMapper, workflowService, levelConfirmationService, projectionService, publisher,
                eventPublisher);
        AbilityHarnessBatchItem item = new AbilityHarnessBatchItem();
        item.setClaimGroupId(265L);
        when(batchItemMapper.selectOne(any())).thenReturn(item);
        PersonAbilityClaimGroup group = new PersonAbilityClaimGroup();
        group.setId(265L);
        group.setWorkflowId(20L);
        when(groupMapper.selectById(265L)).thenReturn(group);
        when(groupMapper.selectCount(any())).thenReturn(1L);
        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setId(88L);
        when(workflowService.getLatestStageRun(20L, "AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION")).thenReturn(stageRun);

        service.acceptAndProject(101L, "approved by HR");

        verify(groupMapper).updateById(group);
        verify(levelConfirmationService).confirmLevels(20L, 88L);
        verify(projectionService).projectConfirmed(20L, null);
        verify(publisher, never()).publish(any());
    }

    /**
     * 聚合审核采纳必须发布能力变更事件，否则「全面能力分析报告」不会自动生成
     * （CapabilityAnalysisReportListener 依赖该事件；单条 claim 准入链路本来就会发）。
     */
    @Test
    void acceptAndProject_publishesAbilityChangeEventSoReportCanAutoGenerate() {
        AbilityHarnessBatchItemMapper batchItemMapper = mock(AbilityHarnessBatchItemMapper.class);
        PersonAbilityClaimGroupMapper groupMapper = mock(PersonAbilityClaimGroupMapper.class);
        CapabilityAssessmentWorkflowService workflowService = mock(CapabilityAssessmentWorkflowService.class);
        AbilityLevelConfirmationService levelConfirmationService = mock(AbilityLevelConfirmationService.class);
        AbilityProfileProjectionService projectionService = mock(AbilityProfileProjectionService.class);
        com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher publisher =
                mock(com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher =
                mock(org.springframework.context.ApplicationEventPublisher.class);
        AggregateAbilityHarnessReviewService service = new AggregateAbilityHarnessReviewServiceImpl(
                batchItemMapper, groupMapper, workflowService, levelConfirmationService, projectionService, publisher,
                eventPublisher);

        AbilityHarnessBatchItem item = new AbilityHarnessBatchItem();
        item.setClaimGroupId(265L);
        when(batchItemMapper.selectOne(any())).thenReturn(item);
        PersonAbilityClaimGroup group = new PersonAbilityClaimGroup();
        group.setId(265L);
        group.setWorkflowId(20L);
        group.setEmpId(7L);
        when(groupMapper.selectById(265L)).thenReturn(group);
        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setId(88L);
        when(workflowService.getLatestStageRun(20L, "AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION")).thenReturn(stageRun);

        service.acceptAndProject(101L, "approved by HR");

        // publishEvent 有 ApplicationEvent / Object 两个重载，AbilityChangeEvent 更精确匹配前者，
        // 捕获器必须按 ApplicationEvent 声明，否则 Mockito 认为「未被调用」。
        org.mockito.ArgumentCaptor<org.springframework.context.ApplicationEvent> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.context.ApplicationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                captor.getValue() instanceof com.example.matching.event.AbilityChangeEvent,
                "采纳后应发布 AbilityChangeEvent 以触发报告生成");
        com.example.matching.event.AbilityChangeEvent event =
                (com.example.matching.event.AbilityChangeEvent) captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("EMP_ABILITY", event.getChangeType());
        org.junit.jupiter.api.Assertions.assertEquals(7L, event.getEntityId());
    }

}
