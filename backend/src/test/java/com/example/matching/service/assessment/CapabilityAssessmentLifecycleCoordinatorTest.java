package com.example.matching.service.assessment;

import com.example.matching.service.assessment.AbilityLevelConfirmationService;

import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.entity.workflow.CapabilityStageLifecycleEventLog;
import com.example.matching.entity.workflow.PersonAbilityLevelDecision;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.service.assessment.impl.CapabilityAssessmentLifecycleCoordinatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Capability assessment lifecycle coordinator unit tests.
 */
class CapabilityAssessmentLifecycleCoordinatorTest {

    private CapabilityAssessmentWorkflowService workflowService;
    private AbilityLevelConfirmationService levelConfirmationService;
    private AbilityProfileProjectionService projectionService;
    private CapabilityAssessmentLifecycleCoordinator coordinator;

    @BeforeEach
    void setUp() {
        workflowService = mock(CapabilityAssessmentWorkflowService.class);
        levelConfirmationService = mock(AbilityLevelConfirmationService.class);
        projectionService = mock(AbilityProfileProjectionService.class);
        coordinator = new CapabilityAssessmentLifecycleCoordinatorImpl(
                workflowService, levelConfirmationService, projectionService);
        when(workflowService.claimLifecycleEventLog(any())).thenReturn(true);
        when(projectionService.listProvisionalGroups(any())).thenReturn(List.of());
    }

    private PersonCapabilityWorkflow workflow(Long id, String status) {
        PersonCapabilityWorkflow w = new PersonCapabilityWorkflow();
        w.setId(id);
        w.setEmpId(1L);
        w.setStatus(status);
        return w;
    }

    private PersonCapabilityStageRun stageRun(Long id, Long workflowId, String stageType, String status) {
        PersonCapabilityStageRun s = new PersonCapabilityStageRun();
        s.setId(id);
        s.setWorkflowId(workflowId);
        s.setStageType(stageType);
        s.setStatus(status);
        return s;
    }

    private void stubCommon(String workflowStatus, PersonCapabilityStageRun stageRun) {
        when(workflowService.getWorkflow(stageRun.getWorkflowId()))
                .thenReturn(workflow(stageRun.getWorkflowId(), workflowStatus));
        when(workflowService.getStageRun(stageRun.getId())).thenReturn(stageRun);
        when(workflowService.casStageRunStatus(any(), any(), any(), any(), any())).thenReturn(true);
        when(workflowService.transition(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void duplicateEventId_isSkipped() {
        when(workflowService.claimLifecycleEventLog(any())).thenReturn(false);
        CapabilityStageLifecycleEvent event = new CapabilityStageLifecycleEvent(
                88L, 5L, "AI_TEST_GENERATION", "AI_TEST", 9L,
                StageLifecycleEventType.TASK_SUCCEEDED, null, null, LocalDateTime.now(), "dup-1");

        coordinator.handle(event);

        verify(workflowService, never()).casStageRunStatus(any(), any(), any(), any(), any());
        verify(workflowService, never()).transition(any(), any(), any(), any());
    }

    @Test
    void taskSucceeded_advancesStageRunAndWorkflow() {
        PersonCapabilityStageRun stageRun = stageRun(5L, 88L, "AI_TEST_GENERATION", "RUNNING");
        stubCommon(WorkflowStatusEnum.TEST_GENERATING.getCode(), stageRun);

        CapabilityStageLifecycleEvent event = CapabilityStageLifecycleEvent.succeeded(
                88L, 5L, "AI_TEST_GENERATION", "AI_TEST", 9L);

        coordinator.handle(event);

        verify(workflowService).casStageRunStatus(any(), any(), any(), any(), any());
        verify(workflowService).transition(any(), any(), any(), any());
        ArgumentCaptor<CapabilityStageLifecycleEventLog> captor = ArgumentCaptor.forClass(CapabilityStageLifecycleEventLog.class);
        verify(workflowService).completeLifecycleEventLog(captor.capture());
        assertEquals("HANDLED", captor.getValue().getHandledResult());
    }

    @Test
    void finalFailure_marksWorkflowFailed() {
        PersonCapabilityStageRun stageRun = stageRun(7L, 88L, "AI_TEST_GENERATION", "RUNNING");
        stubCommon(WorkflowStatusEnum.TEST_GENERATING.getCode(), stageRun);

        CapabilityStageLifecycleEvent event = CapabilityStageLifecycleEvent.failedFinal(
                88L, 7L, "AI_TEST_GENERATION", "AI_TEST", 9L, "GEN_FAILED", "题目生成最终失败");

        coordinator.handle(event);

        verify(workflowService).casStageRunStatus(eq(7L), eq("RUNNING"), eq("FAILED_FINAL"), any(), any());
        verify(workflowService).markWorkflowFinalFailed(eq(88L), anyString());
        verify(workflowService, never()).transition(any(), any(), any(), any());
    }

    @Test
    void levelConfirmationSucceeded_withPendingReview_completesAssessmentAndLeavesReviewToGovernanceQueue() {
        PersonCapabilityStageRun stageRun = stageRun(9L, 88L, "LEVEL_CONFIRMATION", "RUNNING");
        stubCommon(WorkflowStatusEnum.LEVEL_CONFIRMING.getCode(), stageRun);
        PersonAbilityLevelDecision pending = new PersonAbilityLevelDecision();
        pending.setWorkflowId(88L);
        pending.setDecisionStatus("PENDING_MANUAL_REVIEW");
        when(levelConfirmationService.listDecisions(88L)).thenReturn(List.of(pending));

        CapabilityStageLifecycleEvent event = CapabilityStageLifecycleEvent.succeeded(
                88L, 9L, "LEVEL_CONFIRMATION", "LEVEL_CONFIRMATION", 9L);

        coordinator.handle(event);

        verify(workflowService).transition(eq(88L), eq("LEVEL_CONFIRMING"),
                eq("COMPLETED"), eq("LEVEL_CONFIRMATION"));
    }

    @Test
    void illegalEvent_noWorkflowAdvance() {
        PersonCapabilityStageRun stageRun = stageRun(5L, 88L, "AI_TEST_GENERATION", "RUNNING");
        stubCommon(WorkflowStatusEnum.TEST_GENERATING.getCode(), stageRun);

        CapabilityStageLifecycleEvent event = new CapabilityStageLifecycleEvent(
                88L, 5L, "AI_INTERVIEW", "AI_INTERVIEW", 10L,
                StageLifecycleEventType.TASK_SUCCEEDED, null, null, LocalDateTime.now(), "evt-illegal");

        coordinator.handle(event);

        verify(workflowService, never()).casStageRunStatus(any(), any(), any(), any(), any());
        verify(workflowService, never()).transition(any(), any(), any(), any());
    }

    @Test
    void stageRunNotFound_eventLoggedAndSkipped() {
        when(workflowService.getWorkflow(88L))
                .thenReturn(workflow(88L, WorkflowStatusEnum.TEST_GENERATING.getCode()));
        when(workflowService.getStageRun(404L)).thenReturn(null);
        when(workflowService.resolveActiveStageRun(any(), any(), any(), any())).thenReturn(null);

        CapabilityStageLifecycleEvent event = CapabilityStageLifecycleEvent.succeeded(
                88L, 404L, "AI_TEST_GENERATION", "AI_TEST", 9L);

        coordinator.handle(event);

        ArgumentCaptor<CapabilityStageLifecycleEventLog> captor = ArgumentCaptor.forClass(CapabilityStageLifecycleEventLog.class);
        verify(workflowService).completeLifecycleEventLog(captor.capture());
        assertEquals("STAGE_RUN_NOT_FOUND", captor.getValue().getHandledResult());
        verify(workflowService, never()).casStageRunStatus(any(), any(), any(), any(), any());
        verify(workflowService, never()).transition(any(), any(), any(), any());
    }

    @Test
    void workflowTerminal_eventRejected() {
        PersonCapabilityStageRun stageRun = stageRun(5L, 88L, "AI_TEST_GENERATION", "RUNNING");
        stubCommon(WorkflowStatusEnum.COMPLETED.getCode(), stageRun);

        CapabilityStageLifecycleEvent event = CapabilityStageLifecycleEvent.succeeded(
                88L, 5L, "AI_TEST_GENERATION", "AI_TEST", 9L);

        coordinator.handle(event);

        verify(workflowService, never()).casStageRunStatus(any(), any(), any(), any(), any());
        verify(workflowService, never()).transition(any(), any(), any(), any());
    }

    @Test
    void stageRunIdNull_resolvesByWorkflowStageAndSourceRef() {
        when(workflowService.getWorkflow(88L))
                .thenReturn(workflow(88L, WorkflowStatusEnum.TEST_GENERATING.getCode()));
        PersonCapabilityStageRun stageRun = stageRun(5L, 88L, "AI_TEST_GENERATION", "RUNNING");
        when(workflowService.resolveActiveStageRun(eq(88L), eq("AI_TEST_GENERATION"), any(), any()))
                .thenReturn(stageRun);
        when(workflowService.casStageRunStatus(any(), any(), any(), any(), any())).thenReturn(true);
        when(workflowService.transition(any(), any(), any(), any())).thenReturn(true);

        CapabilityStageLifecycleEvent event = new CapabilityStageLifecycleEvent(
                88L, null, "AI_TEST_GENERATION", "AI_TEST", 9L,
                StageLifecycleEventType.TASK_SUCCEEDED, null, null, LocalDateTime.now(), "evt-resolved");

        coordinator.handle(event);

        verify(workflowService).casStageRunStatus(any(), any(), any(), any(), any());
        verify(workflowService).transition(any(), any(), any(), any());
    }

    @Test
    void taskSucceededOnTerminalStageRun_skipsStageCasButKeepsWorkflow() {
        PersonCapabilityStageRun stageRun = stageRun(5L, 88L, "AI_TEST_GENERATION", "FAILED_FINAL");
        stubCommon(WorkflowStatusEnum.TEST_GENERATING.getCode(), stageRun);

        CapabilityStageLifecycleEvent event = CapabilityStageLifecycleEvent.succeeded(
                88L, 5L, "AI_TEST_GENERATION", "AI_TEST", 9L);

        coordinator.handle(event);

        verify(workflowService, never()).casStageRunStatus(any(), any(), any(), any(), any());
    }
}
