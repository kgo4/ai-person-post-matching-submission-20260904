package com.example.matching.service.assessment;

import com.example.matching.service.assessment.AbilityLevelConfirmationService;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.mapper.workflow.CapabilityStageLifecycleEventLogMapper;
import com.example.matching.mapper.workflow.PersonAbilityLevelDecisionMapper;
import com.example.matching.mapper.workflow.PersonCapabilityStageRunMapper;
import com.example.matching.mapper.workflow.PersonCapabilityWorkflowMapper;
import com.example.matching.service.assessment.impl.CapabilityAssessmentLifecycleCoordinatorImpl;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CapabilityAssessmentLifecycleCoordinatorIT.TestConfig.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@org.springframework.test.context.jdbc.Sql(
        scripts = "classpath:sql/capability-workflow-test-schema.sql",
        executionPhase = org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CapabilityAssessmentLifecycleCoordinatorIT {

    @Configuration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class
    })
    @MapperScan("com.example.matching.mapper.workflow")
    static class TestConfig {
    }

    @Autowired
    private PersonCapabilityWorkflowMapper workflowMapper;
    @Autowired
    private PersonCapabilityStageRunMapper stageRunMapper;
    @Autowired
    private PersonAbilityLevelDecisionMapper decisionMapper;
    @Autowired
    private CapabilityStageLifecycleEventLogMapper eventLogMapper;

    private CapabilityAssessmentLifecycleCoordinator newCoordinator() {
        CapabilityAssessmentWorkflowService workflowService =
                new com.example.matching.service.assessment.impl.CapabilityAssessmentWorkflowServiceImpl(
                        workflowMapper, stageRunMapper, eventLogMapper, null);
        AbilityLevelConfirmationService levelConfirmationService =
                org.mockito.Mockito.mock(AbilityLevelConfirmationService.class);
        org.mockito.Mockito.when(levelConfirmationService.listDecisions(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());
        AbilityProfileProjectionService projectionService =
                org.mockito.Mockito.mock(AbilityProfileProjectionService.class);
        org.mockito.Mockito.when(projectionService.listProvisionalGroups(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());
        return new CapabilityAssessmentLifecycleCoordinatorImpl(
                workflowService, levelConfirmationService, projectionService);
    }

    @Test
    void fullAssessmentFlow_reachesCompleted() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setEmpId(1L);
        workflow.setStatus(WorkflowStatusEnum.RESUME_REQUIRED.getCode());
        workflow.setWorkflowVersion(0);
        workflow.setCreatedTime(java.time.LocalDateTime.now());
        workflow.setUpdatedTime(java.time.LocalDateTime.now());
        workflowMapper.insert(workflow);
        Long wf = workflow.getId();

        CapabilityAssessmentLifecycleCoordinator coordinator = newCoordinator();

        PersonCapabilityStageRun parseRun = stageRun(workflow, "RESUME_PARSE", 1L);
        stageRunMapper.insert(parseRun);
        handle(coordinator, wf, parseRun, StageLifecycleEventType.TASK_CLAIMED);
        assertWorkflowStatus(wf, WorkflowStatusEnum.RESUME_PARSING);

        PersonCapabilityStageRun extractRun = stageRun(workflow, "RESUME_CLAIM_EXTRACTION", 1L);
        stageRunMapper.insert(extractRun);
        handle(coordinator, wf, parseRun, StageLifecycleEventType.TASK_SUCCEEDED);
        handle(coordinator, wf, extractRun, StageLifecycleEventType.TASK_SUCCEEDED);
        assertWorkflowStatus(wf, WorkflowStatusEnum.RESUME_EVIDENCE_READY);

        PersonCapabilityStageRun genRun = stageRun(workflow, "AI_TEST_GENERATION", 1024L);
        stageRunMapper.insert(genRun);
        handle(coordinator, wf, genRun, StageLifecycleEventType.TASK_CLAIMED);
        assertWorkflowStatus(wf, WorkflowStatusEnum.TEST_GENERATING);
        handle(coordinator, wf, genRun, StageLifecycleEventType.TASK_SUCCEEDED);
        assertWorkflowStatus(wf, WorkflowStatusEnum.TEST_IN_PROGRESS);

        PersonCapabilityStageRun evalRun = stageRun(workflow, "AI_TEST_EVALUATION", 1024L);
        stageRunMapper.insert(evalRun);
        handle(coordinator, wf, evalRun, StageLifecycleEventType.USER_ACTION_STARTED);
        assertWorkflowStatus(wf, WorkflowStatusEnum.TEST_EVALUATING);
        handle(coordinator, wf, evalRun, StageLifecycleEventType.TASK_SUCCEEDED);
        assertWorkflowStatus(wf, WorkflowStatusEnum.TEST_EVIDENCE_READY);

        PersonCapabilityStageRun interviewRun = stageRun(workflow, "AI_INTERVIEW", 500L);
        stageRunMapper.insert(interviewRun);
        handle(coordinator, wf, interviewRun, StageLifecycleEventType.TASK_CLAIMED);
        assertWorkflowStatus(wf, WorkflowStatusEnum.INTERVIEW_PREPARING);
        handle(coordinator, wf, interviewRun, StageLifecycleEventType.TASK_READY_FOR_USER);
        assertWorkflowStatus(wf, WorkflowStatusEnum.INTERVIEW_IN_PROGRESS);
        assertThat(stageRunMapper.selectById(interviewRun.getId()).getStatus()).isEqualTo("WAITING_USER");
        handle(coordinator, wf, interviewRun, StageLifecycleEventType.USER_ACTION_COMPLETED);
        assertWorkflowStatus(wf, WorkflowStatusEnum.INTERVIEW_ANALYZING);
        handle(coordinator, wf, interviewRun, StageLifecycleEventType.TASK_SUCCEEDED);
        assertWorkflowStatus(wf, WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING);

        PersonCapabilityStageRun harnessRun = stageRun(workflow, "AGGREGATE_HARNESS", interviewRun.getId());
        stageRunMapper.insert(harnessRun);
        handle(coordinator, wf, harnessRun, StageLifecycleEventType.TASK_CLAIMED);
        assertWorkflowStatus(wf, WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING);
        handle(coordinator, wf, harnessRun, StageLifecycleEventType.TASK_SUCCEEDED);
        assertWorkflowStatus(wf, WorkflowStatusEnum.COMPLETED);
    }

    @Test
    void finalFailure_marksWorkflowFailed_withReason() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setEmpId(2L);
        workflow.setStatus(WorkflowStatusEnum.RESUME_REQUIRED.getCode());
        workflow.setWorkflowVersion(0);
        workflow.setCreatedTime(java.time.LocalDateTime.now());
        workflow.setUpdatedTime(java.time.LocalDateTime.now());
        workflowMapper.insert(workflow);
        Long wf = workflow.getId();

        CapabilityAssessmentLifecycleCoordinator coordinator = newCoordinator();

        PersonCapabilityStageRun parseRun = stageRun(workflow, "RESUME_PARSE", 1L);
        stageRunMapper.insert(parseRun);
        handle(coordinator, wf, parseRun, StageLifecycleEventType.TASK_CLAIMED);
        handle(coordinator, wf, parseRun, StageLifecycleEventType.TASK_FAILED_FINAL, "PARSE_FAILED", "解析永久失败");

        PersonCapabilityWorkflow after = workflowMapper.selectById(wf);
        assertThat(after.getStatus()).isEqualTo(WorkflowStatusEnum.FAILED.getCode());
        assertThat(after.getFailedReason()).contains("解析永久失败");
        assertThat(stageRunMapper.selectById(parseRun.getId()).getStatus()).isEqualTo("FAILED_FINAL");
    }

    @Test
    void duplicateEvent_doesNotAdvanceTwice() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setEmpId(3L);
        workflow.setStatus(WorkflowStatusEnum.RESUME_REQUIRED.getCode());
        workflow.setWorkflowVersion(0);
        workflow.setCreatedTime(java.time.LocalDateTime.now());
        workflow.setUpdatedTime(java.time.LocalDateTime.now());
        workflowMapper.insert(workflow);
        Long wf = workflow.getId();

        CapabilityAssessmentLifecycleCoordinator coordinator = newCoordinator();

        PersonCapabilityStageRun parseRun = stageRun(workflow, "RESUME_PARSE", 1L);
        stageRunMapper.insert(parseRun);
        handle(coordinator, wf, parseRun, StageLifecycleEventType.TASK_CLAIMED);
        handle(coordinator, wf, parseRun, StageLifecycleEventType.TASK_CLAIMED);
        assertWorkflowStatus(wf, WorkflowStatusEnum.RESUME_PARSING);
        assertThat(stageRunMapper.selectById(parseRun.getId()).getStatus()).isEqualTo("RUNNING");
    }

    private PersonCapabilityStageRun stageRun(PersonCapabilityWorkflow workflow, String stageType, Long sourceRefId) {
        PersonCapabilityStageRun run = new PersonCapabilityStageRun();
        run.setWorkflowId(workflow.getId());
        run.setStageType(stageType);
        run.setStatus("PENDING");
        run.setInputHash("h-" + stageType + "-" + sourceRefId);
        run.setSourceRefType(stageType);
        run.setSourceRefId(sourceRefId);
        run.setCreatedTime(java.time.LocalDateTime.now());
        run.setUpdatedTime(java.time.LocalDateTime.now());
        return run;
    }

    private void handle(CapabilityAssessmentLifecycleCoordinator coordinator, Long wf, PersonCapabilityStageRun run,
                        StageLifecycleEventType eventType) {
        handle(coordinator, wf, run, eventType, null, null);
    }

    private void handle(CapabilityAssessmentLifecycleCoordinator coordinator, Long wf, PersonCapabilityStageRun run,
                        StageLifecycleEventType eventType, String errorCode, String errorMessage) {
        coordinator.handle(com.example.matching.event.CapabilityStageLifecycleEvent.of(
                wf, run.getId(), run.getStageType(), run.getStageType(), run.getSourceRefId(),
                eventType, errorCode, errorMessage));
    }

    private void assertWorkflowStatus(Long wf, WorkflowStatusEnum expected) {
        assertThat(workflowMapper.selectById(wf).getStatus()).isEqualTo(expected.getCode());
    }
}
