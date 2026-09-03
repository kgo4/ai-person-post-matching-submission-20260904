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
        AggregateAbilityHarnessReviewService service = new AggregateAbilityHarnessReviewServiceImpl(
                batchItemMapper, groupMapper, workflowService, levelConfirmationService, projectionService, publisher);

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
        verify(publisher, never()).publish(any());
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
        AggregateAbilityHarnessReviewService service = new AggregateAbilityHarnessReviewServiceImpl(
                batchItemMapper, groupMapper, workflowService, levelConfirmationService, projectionService, publisher);
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

}
