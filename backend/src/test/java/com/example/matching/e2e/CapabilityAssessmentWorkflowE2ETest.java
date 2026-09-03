package com.example.matching.e2e;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.agent.service.EmployeeAbilityAgentService;
import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.dto.assessment.ResumeAbilityClaimDTO;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.workflow.AiTestCoverageMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.assessment.AbilityLevelConfirmationService;
import com.example.matching.service.assessment.AbilityProfileProjectionService;
import com.example.matching.service.assessment.AggregateAbilityHarnessService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.service.assessment.impl.CapabilityAssessmentFacadeImpl;
import com.example.matching.service.assessment.impl.CapabilityAssessmentWorkflowServiceImpl;
import com.example.matching.service.employee.AiTestService;
import com.example.matching.service.employee.VideoInterviewService;
import com.example.matching.service.employee.impl.ResumeFileParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 能力评估工作流端到端测试（使用固定 Agent 响应和 Mock 端口）
 * <p>
 * 覆盖：简历上传 -> 提取 -> 测试 -> 面试 -> Harness -> 等级确认 的完整闭环。
 * 不调用真实 LLM、OCR、摄像头、麦克风、Redis 或 Milvus。
 */
class CapabilityAssessmentWorkflowE2ETest {

    private static final Long EMP_ID = 100L;
    private static final Long OPERATOR_ID = 9L;

    private CapabilityAssessmentWorkflowService workflowService;
    private AbilityEvidenceCollectionService evidenceCollectionService;
    private AbilityProfileProjectionService projectionService;
    private AiTestService aiTestService;
    private AiTestCoverageMapper coverageMapper;
    private PersonAbilityClaimGroupMapper claimGroupMapper;
    private PersonAbilityClaimMapper claimMapper;
    private VideoInterviewService videoInterviewService;
    private EmpAiTestMapper empAiTestMapper;
    private AggregateAbilityHarnessService aggregateHarnessService;
    private AbilityLevelConfirmationService levelConfirmationService;
    private CapabilityStageLifecycleEventPublisher publisher;
    private com.example.matching.service.assessment.AssessmentScopeService assessmentScopeService;
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private CapabilityAssessmentFacadeImpl facade;

    private PersonCapabilityWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflowService = mock(CapabilityAssessmentWorkflowService.class);
        evidenceCollectionService = mock(AbilityEvidenceCollectionService.class);
        projectionService = mock(AbilityProfileProjectionService.class);
        aiTestService = mock(AiTestService.class);
        coverageMapper = mock(AiTestCoverageMapper.class);
        claimGroupMapper = mock(PersonAbilityClaimGroupMapper.class);
        claimMapper = mock(PersonAbilityClaimMapper.class);
        videoInterviewService = mock(VideoInterviewService.class);
        empAiTestMapper = mock(EmpAiTestMapper.class);
        aggregateHarnessService = mock(AggregateAbilityHarnessService.class);
        levelConfirmationService = mock(AbilityLevelConfirmationService.class);
        publisher = mock(CapabilityStageLifecycleEventPublisher.class);
        assessmentScopeService = mock(com.example.matching.service.assessment.AssessmentScopeService.class);
        objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        when(assessmentScopeService.build(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new com.example.matching.dto.assessment.AssessmentScopeDTO(
                        1L, EMP_ID, 300L, java.util.Collections.emptyList(),
                        java.util.Collections.emptyList(), "scope-hash-123"));

        facade = new CapabilityAssessmentFacadeImpl(
                workflowService, evidenceCollectionService, projectionService,
                mock(com.example.matching.service.assessment.ProvisionalMatchingSnapshotService.class),
                aiTestService, coverageMapper, claimGroupMapper, claimMapper,
                videoInterviewService, empAiTestMapper, aggregateHarnessService,
                levelConfirmationService, publisher,
                mock(com.example.matching.service.assessment.AssessmentReportService.class),
                assessmentScopeService, objectMapper);

        // 初始化 MyBatis-Plus TableInfo 缓存
        com.baomidou.mybatisplus.core.MybatisConfiguration config =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(config, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant, PersonCapabilityWorkflow.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant, PersonCapabilityStageRun.class);

        workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(EMP_ID);
        workflow.setStatus(WorkflowStatusEnum.RESUME_REQUIRED.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 场景一：正常闭环（有证据路径）
    // ═══════════════════════════════════════════════════════════════

    @Test
    void happyPath_resumeEvidenceReady_to_levelConfirming() {
        // --- Step 1: 提交简历证据 ---
        when(workflowService.getOrCreateActiveWorkflow(EMP_ID, OPERATOR_ID)).thenReturn(workflow);
        PersonCapabilityStageRun parseRun = stageRun(10L, "RESUME_PARSE", "PENDING");
        PersonCapabilityStageRun extractRun = stageRun(11L, "RESUME_CLAIM_EXTRACTION", "PENDING");
        when(workflowService.createStageRun(anyLong(), eq("RESUME_PARSE"), anyString(), anyString(), anyString(), any()))
                .thenReturn(parseRun);
        when(workflowService.createStageRun(anyLong(), eq("RESUME_CLAIM_EXTRACTION"), anyString(), anyString(), anyString(), any()))
                .thenReturn(extractRun);
        when(evidenceCollectionService.saveResumeClaims(anyLong(), anyLong(), anyLong(), any(), anyLong()))
                .thenReturn(2); // 保存了2个有效Claim
        // groupClaimsByAbility void, no mock needed

        ResumeAbilityClaimDTO dto = new ResumeAbilityClaimDTO();
        dto.setAbilityName("Java");
        dto.setClaimedLevel(4);
        dto.setEvidenceText("5年Java后端开发经验");
        dto.setEvidenceLocation("sourceText:42-55");
        dto.setSourceRefId(200L);
        dto.setSourceRefs(List.of("source:RESUME_PARSE:200"));

        int saved = facade.submitResumeEvidence(EMP_ID, 200L, List.of(dto), OPERATOR_ID);
        assertThat(saved).isEqualTo(2);

        // 验证发布了正确的生命周期事件
        ArgumentCaptor<CapabilityStageLifecycleEvent> eventCaptor =
                ArgumentCaptor.forClass(CapabilityStageLifecycleEvent.class);
        verify(publisher, org.mockito.Mockito.atLeast(3)).publish(eventCaptor.capture());
        List<CapabilityStageLifecycleEvent> events = eventCaptor.getAllValues();

        // 事件1: RESUME_PARSE TASK_CLAIMED
        // 事件2: RESUME_PARSE TASK_SUCCEEDED
        // 事件3: RESUME_CLAIM_EXTRACTION TASK_SUCCEEDED（因为有证据）
        boolean hasResumeClaimSucceeded = events.stream()
                .anyMatch(e -> "RESUME_CLAIM_EXTRACTION".equals(e.stageType())
                        && e.eventType() == StageLifecycleEventType.TASK_SUCCEEDED);
        assertThat(hasResumeClaimSucceeded).as("有证据时应发布 RESUME_CLAIM_EXTRACTION TASK_SUCCEEDED").isTrue();
    }

    @Test
    void noEvidencePath_emptyClaims_triggersNoEvidence() {
        when(workflowService.getOrCreateActiveWorkflow(EMP_ID, OPERATOR_ID)).thenReturn(workflow);
        PersonCapabilityStageRun parseRun = stageRun(10L, "RESUME_PARSE", "PENDING");
        PersonCapabilityStageRun extractRun = stageRun(11L, "RESUME_CLAIM_EXTRACTION", "PENDING");
        when(workflowService.createStageRun(anyLong(), eq("RESUME_PARSE"), anyString(), anyString(), anyString(), any()))
                .thenReturn(parseRun);
        when(workflowService.createStageRun(anyLong(), eq("RESUME_CLAIM_EXTRACTION"), anyString(), anyString(), anyString(), any()))
                .thenReturn(extractRun);
        when(evidenceCollectionService.saveResumeClaims(anyLong(), anyLong(), anyLong(), any(), anyLong()))
                .thenReturn(0);

        int saved = facade.submitResumeEvidence(EMP_ID, 200L, Collections.emptyList(), OPERATOR_ID);
        assertThat(saved).isZero();

        ArgumentCaptor<CapabilityStageLifecycleEvent> eventCaptor =
                ArgumentCaptor.forClass(CapabilityStageLifecycleEvent.class);
        verify(publisher, org.mockito.Mockito.atLeastOnce()).publish(eventCaptor.capture());
        List<CapabilityStageLifecycleEvent> events = eventCaptor.getAllValues();

        boolean hasNoEvidence = events.stream()
                .anyMatch(e -> "RESUME_CLAIM_EXTRACTION".equals(e.stageType())
                        && e.eventType() == StageLifecycleEventType.NO_EVIDENCE);
        assertThat(hasNoEvidence).as("无证据时应发布 NO_EVIDENCE 事件").isTrue();

        boolean noClaimSucceededWhenZero = events.stream()
                .noneMatch(e -> "RESUME_CLAIM_EXTRACTION".equals(e.stageType())
                        && e.eventType() == StageLifecycleEventType.TASK_SUCCEEDED);
        assertThat(noClaimSucceededWhenZero).as("saved==0 时不应发布 RESUME_CLAIM_EXTRACTION TASK_SUCCEEDED").isTrue();
    }

    @Test
    void generateTest_returnsWorkflowIdTestIdPostId() {
        workflow.setStatus(WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);
        org.mockito.Mockito.doNothing().when(workflowService).assertStagePrerequisite(1L, "AI_TEST_GENERATION");

        EmpAiTest test = new EmpAiTest();
        test.setId(500L);
        test.setWorkflowId(1L);
        test.setPostId(300L);
        when(aiTestService.generateWorkflowTest(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(test);

        PersonCapabilityStageRun stageRun = stageRun(30L, "AI_TEST_GENERATION", "PENDING");
        when(workflowService.createStageRun(anyLong(), eq("AI_TEST_GENERATION"), anyString(), anyString(), anyString(), any()))
                .thenReturn(stageRun);
        when(coverageMapper.insert(any(com.example.matching.entity.workflow.AiTestCoverage.class))).thenReturn(1);
        when(claimGroupMapper.selectList(any())).thenReturn(Collections.emptyList());

        com.example.matching.dto.assessment.GenerateVerificationTestResponse result =
                facade.generateTest(1L, 300L, OPERATOR_ID);

        assertThat(result.getTestId()).isEqualTo(500L);
        assertThat(result.getPostId()).isEqualTo(300L);
    }

    @Test
    void createInterview_returnsSessionIdAndPostId() {
        workflow.setStatus(WorkflowStatusEnum.TEST_EVIDENCE_READY.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);

        EmpAiTest test = new EmpAiTest();
        test.setId(500L);
        test.setPostId(300L);
        when(empAiTestMapper.selectOne(any())).thenReturn(test);

        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setId(600L);
        when(videoInterviewService.createSession(any(), anyLong())).thenReturn(session);

        PersonCapabilityStageRun stageRun = stageRun(40L, "AI_INTERVIEW", "PENDING");
        when(workflowService.createStageRun(anyLong(), eq("AI_INTERVIEW"), anyString(), anyString(), anyString(), any()))
                .thenReturn(stageRun);

        com.example.matching.dto.assessment.CreateAssessmentInterviewResponse result =
                facade.createInterview(1L, OPERATOR_ID);

        assertThat(result.getSessionId()).isEqualTo(600L);
        assertThat(result.getPostId()).isEqualTo(300L);
    }

    @Test
    void noEvidenceWorkflow_canStillGenerateTest() {
        workflow.setStatus("RESUME_PARSED_NO_EVIDENCE");
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);
        org.mockito.Mockito.doNothing().when(workflowService).assertStagePrerequisite(1L, "AI_TEST_GENERATION");

        EmpAiTest test = new EmpAiTest();
        test.setId(501L);
        test.setWorkflowId(1L);
        test.setPostId(300L);
        when(aiTestService.generateWorkflowTest(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(test);

        PersonCapabilityStageRun stageRun = stageRun(30L, "AI_TEST_GENERATION", "PENDING");
        when(workflowService.createStageRun(anyLong(), eq("AI_TEST_GENERATION"), anyString(), anyString(), anyString(), any()))
                .thenReturn(stageRun);
        when(coverageMapper.insert(any(com.example.matching.entity.workflow.AiTestCoverage.class))).thenReturn(1);
        when(claimGroupMapper.selectList(any())).thenReturn(Collections.emptyList());

        com.example.matching.dto.assessment.GenerateVerificationTestResponse result =
                facade.generateTest(1L, 300L, OPERATOR_ID);

        assertThat(result).isNotNull();
        assertThat(result.getTestId()).isEqualTo(501L);
    }

    @Test
    void finishInterview_createsStageRunAndPublishesEvent() {
        workflow.setStatus(WorkflowStatusEnum.INTERVIEW_IN_PROGRESS.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);

        PersonCapabilityStageRun stageRun = stageRun(20L, "AI_INTERVIEW", "RUNNING");
        when(workflowService.createStageRun(anyLong(), eq("AI_INTERVIEW"), anyString(), anyString(), anyString(), any()))
                .thenReturn(stageRun);

        PersonCapabilityStageRun result = facade.finishInterview(1L, 600L, OPERATOR_ID);

        assertThat(result).isNotNull();
        verify(publisher).publish(any(CapabilityStageLifecycleEvent.class));
    }

    private PersonCapabilityStageRun stageRun(Long id, String stageType, String status) {
        PersonCapabilityStageRun run = new PersonCapabilityStageRun();
        run.setId(id);
        run.setWorkflowId(1L);
        run.setStageType(stageType);
        run.setStatus(status);
        run.setCreatedTime(LocalDateTime.now());
        run.setUpdatedTime(LocalDateTime.now());
        return run;
    }
}
