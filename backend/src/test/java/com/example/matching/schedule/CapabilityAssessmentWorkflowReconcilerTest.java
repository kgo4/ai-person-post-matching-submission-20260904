package com.example.matching.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.event.AiTestEvaluatedEvent;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.workflow.PersonCapabilityStageRunMapper;
import com.example.matching.mapper.workflow.PersonCapabilityWorkflowMapper;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 能力评估工作流一致性巡检单元测试
 * <p>
 * 覆盖方案验收标准 7：服务重启或事件漏投后，巡检能恢复一致状态；
 * 补偿只依据业务记录和阶段运行真实状态，不猜测成功。
 */
class CapabilityAssessmentWorkflowReconcilerTest {

    private PersonCapabilityWorkflowMapper workflowMapper;
    private PersonCapabilityStageRunMapper stageRunMapper;
    private CapabilityStageLifecycleEventPublisher publisher;
    private EmpAiTestMapper empAiTestMapper;
    private PersonAbilityClaimMapper personAbilityClaimMapper;
    private ApplicationEventPublisher applicationEventPublisher;
    private com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper interviewSessionMapper;
    private com.example.matching.service.assessment.InterviewAssessmentEvidenceService interviewEvidenceService;
    private CapabilityAssessmentWorkflowReconciler reconciler;

    @BeforeEach
    void setUp() {
        workflowMapper = mock(PersonCapabilityWorkflowMapper.class);
        stageRunMapper = mock(PersonCapabilityStageRunMapper.class);
        publisher = mock(CapabilityStageLifecycleEventPublisher.class);
        empAiTestMapper = mock(EmpAiTestMapper.class);
        personAbilityClaimMapper = mock(PersonAbilityClaimMapper.class);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        interviewSessionMapper = mock(com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper.class);
        interviewEvidenceService = mock(com.example.matching.service.assessment.InterviewAssessmentEvidenceService.class);
        reconciler = new CapabilityAssessmentWorkflowReconciler(
                workflowMapper, stageRunMapper, publisher,
                empAiTestMapper, personAbilityClaimMapper,
                interviewSessionMapper, interviewEvidenceService, applicationEventPublisher);
    }

    private PersonCapabilityWorkflow workflow(Long id, String status) {
        PersonCapabilityWorkflow w = new PersonCapabilityWorkflow();
        w.setId(id);
        w.setEmpId(1L);
        w.setStatus(status);
        w.setUpdatedTime(LocalDateTime.now());
        return w;
    }

    private PersonCapabilityStageRun run(Long id, Long workflowId, String stageType, String status) {
        PersonCapabilityStageRun r = new PersonCapabilityStageRun();
        r.setId(id);
        r.setWorkflowId(workflowId);
        r.setStageType(stageType);
        r.setStatus(status);
        r.setSourceRefType(stageType);
        r.setSourceRefId(id);
        return r;
    }

    @Test
    void noActiveWorkflows_doesNothing() {
        when(workflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        reconciler.reconcileInternal();
        verify(publisher, never()).publish(any());
    }

    @Test
    void stageSucceededButWorkflowStuck_publishesCompensationSucceeded() {
        when(workflowMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(workflow(1L, "TEST_GENERATING")));
        when(stageRunMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(run(10L, 1L, "AI_TEST_GENERATION", "SUCCEEDED")));

        reconciler.reconcileInternal();

        ArgumentCaptor<CapabilityStageLifecycleEvent> captor = ArgumentCaptor.forClass(CapabilityStageLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        CapabilityStageLifecycleEvent event = captor.getValue();
        assertEquals(1L, event.workflowId());
        assertEquals(10L, event.stageRunId());
        assertEquals(StageLifecycleEventType.TASK_SUCCEEDED, event.eventType());
        assertEquals("RECONCILER_COMPENSATION", event.errorCode());
    }

    @Test
    void stageFailedFinalButWorkflowNotFailed_publishesCompensationFailed() {
        when(workflowMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(workflow(1L, "TEST_GENERATING")));
        when(stageRunMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(run(10L, 1L, "AI_TEST_GENERATION", "FAILED_FINAL")));

        reconciler.reconcileInternal();

        ArgumentCaptor<CapabilityStageLifecycleEvent> captor = ArgumentCaptor.forClass(CapabilityStageLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        assertEquals(StageLifecycleEventType.TASK_FAILED_FINAL, captor.getValue().eventType());
    }

    @Test
    void retryableStageWithoutNewerRun_publishesUserActionStarted() {
        when(workflowMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(workflow(1L, "TEST_GENERATING")));
        when(stageRunMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(run(10L, 1L, "AI_TEST_GENERATION", "FAILED_RETRYABLE")));
        when(stageRunMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        reconciler.reconcileInternal();

        ArgumentCaptor<CapabilityStageLifecycleEvent> captor = ArgumentCaptor.forClass(CapabilityStageLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        assertEquals(StageLifecycleEventType.USER_ACTION_STARTED, captor.getValue().eventType());
        assertEquals("AI_TEST_GENERATION", captor.getValue().stageType());
    }

    @Test
    void retryableStageWithNewerRun_skipsCompensation() {
        when(workflowMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(workflow(1L, "TEST_GENERATING")));
        when(stageRunMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        run(10L, 1L, "AI_TEST_GENERATION", "FAILED_RETRYABLE"),
                        run(11L, 1L, "AI_TEST_GENERATION", "RUNNING")));
        when(stageRunMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        reconciler.reconcileInternal();

        verify(publisher, never()).publish(any());
    }

    @Test
    void activeStageRunExists_noCompensation() {
        when(workflowMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(workflow(1L, "TEST_GENERATING")));
        when(stageRunMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(run(10L, 1L, "AI_TEST_GENERATION", "RUNNING")));

        reconciler.reconcileInternal();

        verify(publisher, never()).publish(any());
    }

    @Test
    void evaluatingActiveButTestCompletedWithoutEvidence_republishesEvaluatedEvent() {
        PersonCapabilityStageRun evalRun = run(20L, 1L, "AI_TEST_EVALUATION", "RUNNING");
        evalRun.setSourceRefType("AI_TEST");
        evalRun.setSourceRefId(99L);
        when(workflowMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(workflow(1L, "TEST_EVALUATING")));
        when(stageRunMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(evalRun));

        EmpAiTest test = new EmpAiTest();
        test.setId(99L);
        test.setEmpId(1L);
        test.setWorkflowId(1L);
        test.setStatus(2);
        when(empAiTestMapper.selectById(99L)).thenReturn(test);
        when(personAbilityClaimMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        reconciler.reconcileInternal();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue())
                .isInstanceOf(AiTestEvaluatedEvent.class)
                .satisfies(e -> {
                    AiTestEvaluatedEvent event = (AiTestEvaluatedEvent) e;
                    assertEquals(99L, event.testId());
                    assertEquals(1L, event.workflowId());
                });
        verify(publisher, never()).publish(any());
    }

    @Test
    void evaluatingActiveWithTestCompletedAndEvidence_publishesSucceededCompensation() {
        PersonCapabilityStageRun evalRun = run(20L, 1L, "AI_TEST_EVALUATION", "RUNNING");
        evalRun.setSourceRefType("AI_TEST");
        evalRun.setSourceRefId(99L);
        when(workflowMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(workflow(1L, "TEST_EVALUATING")));
        when(stageRunMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(evalRun));

        EmpAiTest test = new EmpAiTest();
        test.setId(99L);
        test.setEmpId(1L);
        test.setWorkflowId(1L);
        test.setStatus(2);
        when(empAiTestMapper.selectById(99L)).thenReturn(test);
        when(personAbilityClaimMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        reconciler.reconcileInternal();

        ArgumentCaptor<CapabilityStageLifecycleEvent> captor = ArgumentCaptor.forClass(CapabilityStageLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        assertEquals(20L, captor.getValue().stageRunId());
        assertEquals(StageLifecycleEventType.TASK_SUCCEEDED, captor.getValue().eventType());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    // ==================== 规则 4：面试阶段僵尸补偿 ====================

    /** 工作流 INTERVIEW_ANALYZING + 面试运行已 SUCCEEDED + 会话分析完成：补发 TASK_SUCCEEDED 推进聚合 */
    @Test
    void interviewAnalyzingWithCompletedSession_publishesSucceededCompensation() {
        when(workflowMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(workflow(1L, "INTERVIEW_ANALYZING")));
        when(stageRunMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(run(10L, 1L, "AI_INTERVIEW", "SUCCEEDED")));
        com.example.matching.entity.employee.EmpVideoInterviewSession session =
                new com.example.matching.entity.employee.EmpVideoInterviewSession();
        session.setId(10L);
        session.setStatus(5); // COMPLETED
        when(interviewSessionMapper.selectById(10L)).thenReturn(session);

        reconciler.reconcileInternal();

        // 规则 1（无活跃运行）与规则 4（面试僵尸）都可能补发 TASK_SUCCEEDED，均为幂等补偿
        verify(publisher, atLeastOnce()).publish(argThat(e ->
                e.eventType() == StageLifecycleEventType.TASK_SUCCEEDED && e.stageRunId() == 10L));
        verify(interviewEvidenceService, never()).saveInterviewEvidenceAndAdvance(anyLong(), anyLong(), anyLong());
    }

    /** 工作流 INTERVIEW_IN_PROGRESS + 会话分析完成但阶段未推进：补发 USER_ACTION_COMPLETED 并重放证据推进 */
    @Test
    void interviewInProgressWithCompletedSession_replaysEvidenceAndAdvance() {
        when(workflowMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(workflow(1L, "INTERVIEW_IN_PROGRESS")));
        when(stageRunMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(run(10L, 1L, "AI_INTERVIEW", "WAITING_USER")));
        com.example.matching.entity.employee.EmpVideoInterviewSession session =
                new com.example.matching.entity.employee.EmpVideoInterviewSession();
        session.setId(10L);
        session.setStatus(5);
        when(interviewSessionMapper.selectById(10L)).thenReturn(session);

        reconciler.reconcileInternal();

        ArgumentCaptor<CapabilityStageLifecycleEvent> captor = ArgumentCaptor.forClass(CapabilityStageLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        assertEquals(StageLifecycleEventType.USER_ACTION_COMPLETED, captor.getValue().eventType());
        verify(interviewEvidenceService).saveInterviewEvidenceAndAdvance(eq(1L), eq(1L), eq(10L));
    }

    /** 面试会话未分析完成（进行中）时不做补偿，避免误推进 */
    @Test
    void interviewInProgressWithIncompleteSession_doesNothing() {
        when(workflowMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(workflow(1L, "INTERVIEW_IN_PROGRESS")));
        when(stageRunMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(run(10L, 1L, "AI_INTERVIEW", "WAITING_USER")));
        com.example.matching.entity.employee.EmpVideoInterviewSession session =
                new com.example.matching.entity.employee.EmpVideoInterviewSession();
        session.setId(10L);
        session.setStatus(2); // IN_PROGRESS，面试未结束
        when(interviewSessionMapper.selectById(10L)).thenReturn(session);

        reconciler.reconcileInternal();

        verify(publisher, never()).publish(any());
        verify(interviewEvidenceService, never()).saveInterviewEvidenceAndAdvance(anyLong(), anyLong(), anyLong());
    }
}
