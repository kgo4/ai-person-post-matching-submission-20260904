package com.example.matching.service.assessment.impl;

import com.example.matching.entity.interview.InterviewAbilityObservation;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.mapper.interview.InterviewAbilityObservationMapper;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归：saveInterviewEvidenceAndAdvance 必须先将 AI_INTERVIEW 阶段运行 CAS 置为
 * SUCCEEDED（同步），再发布 TASK_SUCCEEDED 并启动 AGGREGATE_HARNESS。
 * 否则 startNextStage 的前置校验（同事务读库）早于协调器异步处理事件，
 * 读到未完成状态抛"前置阶段未完成"，导致整个事务回滚。
 */
class InterviewAssessmentEvidenceServiceImplTest {

    private InterviewAbilityObservationMapper observationMapper;
    private AbilityEvidenceCollectionService evidenceCollectionService;
    private CapabilityAssessmentWorkflowService workflowService;
    private CapabilityStageLifecycleEventPublisher publisher;
    private InterviewAssessmentEvidenceServiceImpl service;

    @BeforeEach
    void setUp() {
        observationMapper = mock(InterviewAbilityObservationMapper.class);
        evidenceCollectionService = mock(AbilityEvidenceCollectionService.class);
        workflowService = mock(CapabilityAssessmentWorkflowService.class);
        publisher = mock(CapabilityStageLifecycleEventPublisher.class);
        when(workflowService.hasRecordedLifecycleEvent(anyLong(), anyString())).thenReturn(false);
        service = new InterviewAssessmentEvidenceServiceImpl(
                observationMapper, evidenceCollectionService, workflowService, publisher);
    }

    private PersonCapabilityStageRun stubStageRun(String status) {
        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setId(40L);
        stageRun.setStageType("AI_INTERVIEW");
        stageRun.setStatus(status);
        when(workflowService.createStageRun(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(stageRun);
        when(workflowService.casStageRunStatus(anyLong(), anyString(), anyString(), any(), any()))
                .thenReturn(true);
        return stageRun;
    }

    @Test
    void saveInterviewEvidenceAndAdvance_marksStageRunSucceededBeforeAdvancing() {
        InterviewAbilityObservation observation = new InterviewAbilityObservation();
        observation.setAbilityName("Java");
        observation.setObservedLevel(3);
        observation.setEvidenceText("面试表现良好");
        observation.setSessionId(76L);
        when(observationMapper.selectList(any())).thenReturn(List.of(observation));
        when(evidenceCollectionService.saveInterviewClaims(anyLong(), anyLong(), anyLong(), any(), any()))
                .thenReturn(1);
        stubStageRun("WAITING_USER");

        service.saveInterviewEvidenceAndAdvance(7L, 100L, 76L);

        // 顺序：先 CAS 置 SUCCEEDED，再发布 TASK_SUCCEEDED，最后启动 AGGREGATE_HARNESS
        InOrder order = inOrder(workflowService, publisher);
        order.verify(workflowService).casStageRunStatus(40L, "WAITING_USER",
                com.example.matching.common.enums.StageRunStatusEnum.SUCCEEDED.getCode(), null, null);
        order.verify(publisher).publish(any(CapabilityStageLifecycleEvent.class));
        order.verify(workflowService).startNextStage(anyLong(), anyString(), anyString(), anyString(), any());
        assertThat(observation.getSessionId()).isEqualTo(76L);
    }

    @Test
    void saveInterviewEvidenceAndAdvance_skipsAdvanceWhenStageRunNotMarkable() {
        InterviewAbilityObservation observation = new InterviewAbilityObservation();
        observation.setAbilityName("Java");
        observation.setObservedLevel(3);
        when(observationMapper.selectList(any())).thenReturn(List.of(observation));
        when(evidenceCollectionService.saveInterviewClaims(anyLong(), anyLong(), anyLong(), any(), any()))
                .thenReturn(1);
        stubStageRun("FAILED_FINAL");
        // CAS 失败：终态阶段不可推进聚合
        when(workflowService.casStageRunStatus(anyLong(), anyString(), anyString(), any(), any()))
                .thenReturn(false);

        service.saveInterviewEvidenceAndAdvance(7L, 100L, 76L);

        verify(publisher, never()).publish(any(CapabilityStageLifecycleEvent.class));
        verify(workflowService, never()).startNextStage(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void saveInterviewEvidenceAndAdvance_skipsDuplicatePublishWhenAlreadyRecorded() {
        // 同 stageRun 已由协调器处理过 TASK_SUCCEEDED（事件日志已落库）→ 不重复发布
        when(workflowService.hasRecordedLifecycleEvent(anyLong(), anyString())).thenReturn(true);
        stubStageRun("SUCCEEDED");

        service.saveInterviewEvidenceAndAdvance(7L, 100L, 76L);

        verify(publisher, never()).publish(any(CapabilityStageLifecycleEvent.class));
        // 重复发布跳过，但下一阶段创建仍应执行（幂等，由 hash 复用）
        verify(workflowService).startNextStage(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void saveInterviewEvidenceAndAdvance_unclassifiedObservation_savedAsUnclassified() {
        InterviewAbilityObservation observation = new InterviewAbilityObservation();
        observation.setAbilityName("新兴技术");
        observation.setObservedLevel(2);
        observation.setEvidenceText("候选人提到某个新技术");
        observation.setSessionId(76L);
        observation.setTagId(null); // 未归类：无法映射到 scope 内标签
        when(observationMapper.selectList(any())).thenReturn(List.of(observation));
        stubStageRun("WAITING_USER");

        service.saveInterviewEvidenceAndAdvance(7L, 100L, 76L);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<com.example.matching.entity.ability.PersonAbilityClaim>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(evidenceCollectionService).saveInterviewClaims(anyLong(), anyLong(), anyLong(), captor.capture(), any());
        List<com.example.matching.entity.ability.PersonAbilityClaim> claims = captor.getValue();
        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).getEvidenceStatus()).isEqualTo("UNCLASSIFIED_OBSERVATION");
        assertThat(claims.get(0).getTagId()).isNull();
    }
}
