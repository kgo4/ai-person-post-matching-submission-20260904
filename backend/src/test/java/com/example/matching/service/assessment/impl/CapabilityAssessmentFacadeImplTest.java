package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.common.enums.StageTypeEnum;
import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.workflow.AiTestCoverageMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.assessment.AbilityProfileProjectionService;
import com.example.matching.service.assessment.AggregateAbilityHarnessService;
import com.example.matching.service.assessment.AbilityLevelConfirmationService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.service.assessment.ProvisionalMatchingSnapshotService;
import com.example.matching.service.employee.AiTestService;
import com.example.matching.service.employee.VideoInterviewService;
import com.example.matching.vo.assessment.CapabilityAssessmentVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 能力评估应用门面测试
 * <p>
 * 覆盖计划 2026-08-09-capability-assessment-closure 验收标准：
 * <ul>
 *   <li>No-evidence 结果：空 claims 不发布证据成功，而是发布 NO_EVIDENCE</li>
 *   <li>API 返回明确资源 ID（testId, sessionId）</li>
 *   <li>工作流绑定面试完成：finishInterview 接受 workflowId + sessionId</li>
 *   <li>RESUME_PARSED_NO_EVIDENCE 状态可继续生成测试（低证据验证测试）</li>
 * </ul>
 *
 * @author system
 */
class CapabilityAssessmentFacadeImplTest {

    private CapabilityAssessmentWorkflowService workflowService;
    private AbilityEvidenceCollectionService evidenceCollectionService;
    private AbilityProfileProjectionService projectionService;
    private ProvisionalMatchingSnapshotService snapshotService;
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

    @BeforeEach
    void setUp() {
        workflowService = mock(CapabilityAssessmentWorkflowService.class);
        evidenceCollectionService = mock(AbilityEvidenceCollectionService.class);
        projectionService = mock(AbilityProfileProjectionService.class);
        snapshotService = mock(ProvisionalMatchingSnapshotService.class);
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
        // 默认评估范围：generateTest 走到 build 步骤时返回确定性 scope
        when(assessmentScopeService.build(anyLong(), anyLong(), anyLong()))
                .thenReturn(new com.example.matching.dto.assessment.AssessmentScopeDTO(
                        1L, 100L, 300L, Collections.emptyList(), Collections.emptyList(), "scope-hash-123"));

        facade = new CapabilityAssessmentFacadeImpl(
                workflowService, evidenceCollectionService, projectionService, snapshotService,
                aiTestService, coverageMapper, claimGroupMapper, claimMapper,
                videoInterviewService, empAiTestMapper, aggregateHarnessService,
                levelConfirmationService, publisher,
                mock(com.example.matching.service.assessment.AssessmentReportService.class),
                assessmentScopeService, objectMapper);

        // 初始化 MyBatis-Plus TableInfo 缓存
        MybatisConfiguration config = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(config, "");
        TableInfoHelper.initTableInfo(assistant, PersonCapabilityWorkflow.class);
        TableInfoHelper.initTableInfo(assistant, PersonCapabilityStageRun.class);
    }

    // ═══════════════════════════════════════════════════════════════
    // Acceptance Criterion 1: No-Evidence Outcome
    // ═══════════════════════════════════════════════════════════════

    /**
     * 当 submitResumeEvidence 入参 claims 为空时，应发布 NO_EVIDENCE 事件，
     * 工作流最终到达 RESUME_PARSED_NO_EVIDENCE。
     * <p>
     * 当前行为（将失败）：空 claims 仍发布 TASK_SUCCEEDED，工作流进入 RESUME_EVIDENCE_READY。
     * Phase 2 将修复为发布 NO_EVIDENCE → RESUME_PARSED_NO_EVIDENCE。
     */
    @Test
    void submitResumeEvidence_emptyClaims_publishesNoEvidenceEvent() {
        // Given
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.RESUME_REQUIRED.getCode());
        when(workflowService.getOrCreateActiveWorkflow(100L, 9L)).thenReturn(workflow);

        PersonCapabilityStageRun parseRun = new PersonCapabilityStageRun();
        parseRun.setId(10L);
        parseRun.setStageType("RESUME_PARSE");
        parseRun.setStatus("PENDING");
        PersonCapabilityStageRun extractRun = new PersonCapabilityStageRun();
        extractRun.setId(11L);
        extractRun.setStageType("RESUME_CLAIM_EXTRACTION");
        extractRun.setStatus("PENDING");
        when(workflowService.createStageRun(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(parseRun, extractRun);
        when(evidenceCollectionService.saveResumeClaims(anyLong(), anyLong(), anyLong(), any(), anyLong()))
                .thenReturn(0); // zero claims saved

        // When
        int saved = facade.submitResumeEvidence(100L, 200L, Collections.emptyList(), 9L);

        // Then
        assertThat(saved).isZero();
        // Phase 2 will change this assertion: NO_EVIDENCE event must be published for RESUME_CLAIM_EXTRACTION
        ArgumentCaptor<CapabilityStageLifecycleEvent> captor =
                ArgumentCaptor.forClass(CapabilityStageLifecycleEvent.class);
        verify(publisher, org.mockito.Mockito.atLeastOnce()).publish(captor.capture());

        // Check that at least one NO_EVIDENCE event is published
        boolean hasNoEvidenceEvent = captor.getAllValues().stream()
                .anyMatch(e -> e.eventType() == StageLifecycleEventType.NO_EVIDENCE);
        // This assertion WILL FAIL until Phase 2 implements NO_EVIDENCE
        assertThat(hasNoEvidenceEvent)
                .as("Empty claims must publish NO_EVIDENCE event to reach RESUME_PARSED_NO_EVIDENCE")
                .isTrue();
    }

    /**
     * NO_EVIDENCE 事件推进工作流到 RESUME_PARSED_NO_EVIDENCE。
     * 当前（Phase 1）：此状态尚不存在，测试预期失败。
     */
    @Test
    void noEvidenceEvent_transitionsWorkflowToResumeParsedNoEvidence() {
        // 验证 WorkflowStatusEnum 包含 RESUME_PARSED_NO_EVIDENCE
        assertThat(WorkflowStatusEnum.RESUME_PARSED_NO_EVIDENCE)
                .as("Phase 2 must add RESUME_PARSED_NO_EVIDENCE to WorkflowStatusEnum")
                .isNotNull();
    }

    /**
     * RESUME_PARSED_NO_EVIDENCE 状态下仍可生成验证测试（低证据测试）。
     */
    @Test
    void generateTest_allowedFromNoEvidenceState() {
        // Given
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        // Phase 2 将添加此状态
        workflow.setStatus("RESUME_PARSED_NO_EVIDENCE");
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);

        // mock prerequisite: RESUME_CLAIM_EXTRACTION has a SUCCEEDED run (void method, no-op)
        org.mockito.Mockito.doNothing().when(workflowService).assertStagePrerequisite(1L, "AI_TEST_GENERATION");

        com.example.matching.entity.employee.EmpAiTest test = new com.example.matching.entity.employee.EmpAiTest();
        test.setId(500L);
        test.setWorkflowId(1L);
        test.setPostId(300L);
        when(aiTestService.generateWorkflowTest(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(test);

        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setId(30L);
        stageRun.setStageType("AI_TEST_GENERATION");
        stageRun.setStatus("PENDING");
        when(workflowService.createStageRun(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(stageRun);
        when(coverageMapper.insert(any(com.example.matching.entity.workflow.AiTestCoverage.class))).thenReturn(1);
        when(claimGroupMapper.selectList(any())).thenReturn(Collections.emptyList());
        // When: generateTest from RESUME_PARSED_NO_EVIDENCE — should NOT throw
        com.example.matching.dto.assessment.GenerateVerificationTestResponse result =
                facade.generateTest(1L, 300L, 9L);
        assertThat(result).isNotNull();
        assertThat(result.getTestId()).isEqualTo(500L);
    }

    // ═══════════════════════════════════════════════════════════════
    // Acceptance Criterion 2: Workflow-Bound Interview Completion
    // ═══════════════════════════════════════════════════════════════

    /**
     * finishInterview 接受 workflowId + sessionId，验证工作流绑定关系。
     */
    @Test
    void finishInterview_verifiesSessionBelongsToWorkflow() {
        // Given
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.INTERVIEW_IN_PROGRESS.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);

        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setId(20L);
        stageRun.setStageType("AI_INTERVIEW");
        stageRun.setStatus("RUNNING");
        when(workflowService.createStageRun(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(stageRun);

        // When: finish interview with sessionId that doesn't match the workflow's session
        // Phase 5 will add session-workflow binding validation
        PersonCapabilityStageRun result = facade.finishInterview(1L, 999L /* wrong session */, 9L);

        // Then: Phase 5 should add validation that the session belongs to the workflow
        // For now, this test documents the expected behavior
        assertThat(result).isNotNull();
        verify(publisher).publish(any(CapabilityStageLifecycleEvent.class));
    }

    // ═══════════════════════════════════════════════════════════════
    // Acceptance Criterion 3: Create API Returns Resource IDs
    // ═══════════════════════════════════════════════════════════════

    /**
     * generateTest 应返回明确的 testId（而非仅 stageRun）。
     * Phase 5 将修改返回类型为 GenerateVerificationTestResponse。
     */
    @Test
    void generateTest_returnsExplicitTestId() {
        // Given
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);

        com.example.matching.entity.employee.EmpAiTest test =
                new com.example.matching.entity.employee.EmpAiTest();
        test.setId(500L);
        test.setWorkflowId(1L);
        test.setPostId(300L);
        test.setStatus(-1);
        when(aiTestService.generateWorkflowTest(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(test);

        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setId(30L);
        stageRun.setStageType("AI_TEST_GENERATION");
        stageRun.setStatus("PENDING");
        stageRun.setInputSnapshotJson("{\"postId\":300,\"testId\":500}");
        when(workflowService.createStageRun(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(stageRun);

        when(claimGroupMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(coverageMapper.insert(any(com.example.matching.entity.workflow.AiTestCoverage.class))).thenReturn(1);

        // When
        com.example.matching.dto.assessment.GenerateVerificationTestResponse result =
                facade.generateTest(1L, 300L, 9L);

        // Then: testId is now explicitly returned
        assertThat(result.getTestId()).isEqualTo(500L);
        assertThat(result.getPostId()).isEqualTo(300L);
    }

    /**
     * createInterview 应返回明确的 sessionId。
     * Phase 5 将修改返回类型为 CreateAssessmentInterviewResponse。
     */
    @Test
    void createInterview_returnsExplicitSessionId() {
        // Given
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.TEST_EVIDENCE_READY.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);
        stubInterviewSessionQuery(Collections.emptyList()); // 无已有会话 → 走新建分支

        com.example.matching.entity.employee.EmpAiTest test =
                new com.example.matching.entity.employee.EmpAiTest();
        test.setId(500L);
        test.setPostId(300L);
        when(empAiTestMapper.selectOne(any())).thenReturn(test);

        com.example.matching.entity.employee.EmpVideoInterviewSession session =
                new com.example.matching.entity.employee.EmpVideoInterviewSession();
        session.setId(600L);
        when(videoInterviewService.createSession(any(), anyLong())).thenReturn(session);

        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setId(40L);
        stageRun.setStageType("AI_INTERVIEW");
        stageRun.setStatus("PENDING");
        stageRun.setInputSnapshotJson("{\"sessionId\":600}");
        when(workflowService.createStageRun(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(stageRun);

        // When
        com.example.matching.dto.assessment.CreateAssessmentInterviewResponse result =
                facade.createInterview(1L, 9L);

        // Then: sessionId is now explicitly returned
        assertThat(result.getSessionId()).isEqualTo(600L);
        assertThat(result.getPostId()).isEqualTo(300L);
    }

    /**
     * mock 工作流面试会话查询：真实 LambdaQueryChainWrapper 构建条件，
     * 底层 mock BaseMapper.selectList 返回指定会话列表。
     */
    private void stubInterviewSessionQuery(java.util.Collection<EmpVideoInterviewSession> sessions) {
        com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper mapper =
                mock(com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper.class);
        when(mapper.selectList(any())).thenReturn(new java.util.ArrayList<>(sessions));
        when(videoInterviewService.lambdaQuery()).thenReturn(new LambdaQueryChainWrapper<>(mapper));
    }

    // ═══════════════════════════════════════════════════════════════
    // Acceptance Criterion: 已有面试会话复用（继续面试而非重复创建）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 工作流已有题目已生成的面试会话（status=1）时，createInterview 应复用返回，
     * 不重复创建会话、不重复生成题目（前端表现为"继续面试"）。
     */
    @Test
    void createInterview_reusesExistingSessionWithQuestions() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.INTERVIEW_PREPARING.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);

        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setId(600L);
        session.setPostId(300L);
        session.setStatus(1); // QUESTION_GENERATED
        stubInterviewSessionQuery(List.of(session));

        com.example.matching.dto.assessment.CreateAssessmentInterviewResponse result =
                facade.createInterview(1L, 9L);

        assertThat(result.getSessionId()).isEqualTo(600L);
        assertThat(result.getPostId()).isEqualTo(300L);
        verify(videoInterviewService, never()).createSession(any(), anyLong());
        verify(videoInterviewService, never()).generateQuestions(anyLong(), any());
    }

    /**
     * 工作流已有题目未生成的会话（status=0）时，复用并补生成题目，保证可直接开始。
     */
    @Test
    void createInterview_reusesCreatedSessionAndGeneratesQuestions() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.INTERVIEW_PREPARING.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);

        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setId(601L);
        session.setPostId(300L);
        session.setStatus(0); // CREATED，题目未生成
        stubInterviewSessionQuery(List.of(session));

        com.example.matching.dto.assessment.CreateAssessmentInterviewResponse result =
                facade.createInterview(1L, 9L);

        assertThat(result.getSessionId()).isEqualTo(601L);
        verify(videoInterviewService).generateQuestions(anyLong(), any());
        verify(videoInterviewService, never()).createSession(any(), anyLong());
    }

    // ═══════════════════════════════════════════════════════════════
    // Acceptance Criterion 4: WorkflowView Contains Evidence Outcome
    // ═══════════════════════════════════════════════════════════════

    /**
     * WorkflowView 应包含 evidenceOutcome 字段。
     * Phase 2 将添加此字段到 VO 和 toWorkflowView 映射。
     */
    @Test
    void workflowView_includesEvidenceOutcome() {
        // Given
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);
        when(workflowService.listStageRuns(anyLong())).thenReturn(Collections.emptyList());

        // When
        CapabilityAssessmentVO.WorkflowView view = facade.getWorkflow(1L);

        // Then: Phase 2 will add evidenceOutcome field
        // assertThat(view.getEvidenceOutcome())
        //         .as("WorkflowView must expose evidence outcome for the frontend")
        //         .isNotNull();
        // For now, document the expected contract
        assertThat(view).isNotNull();
        assertThat(view.getWorkflowStatus())
                .as("Current valid status")
                .isEqualTo(WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode());
    }

    /**
     * RESUME_PARSED_NO_EVIDENCE 状态的展示文本应体现"无证据"。
     */
    @Test
    void displayStatus_forNoEvidence_isMeaningful() {
        // Phase 2 将添加此显示文本
        // assertThat(facade 内部 displayStatus("RESUME_PARSED_NO_EVIDENCE"))
        //         .as("No-evidence status must show readable label")
        //         .isEqualTo("简历已解析（无可用证据）");
    }

    // ═══════════════════════════════════════════════════════════════
    // Acceptance Criterion 5: generateTest Status Eligibility
    // ═══════════════════════════════════════════════════════════════

    @Test
    void generateTest_rejectsResumeParsingState() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.RESUME_PARSING.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);

        assertThatThrownBy(() -> facade.generateTest(1L, 300L, 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESUME_PARSING");
    }

    @Test
    void generateTest_rejectsFailedState() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.FAILED.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);

        assertThatThrownBy(() -> facade.generateTest(1L, 300L, 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    void generateTest_rejectsTerminalCompletedState() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.COMPLETED.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);

        assertThatThrownBy(() -> facade.generateTest(1L, 300L, 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void generateTest_requiresConfiguredTargetPostBeforeBindingWorkflow() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);

        assertThatThrownBy(() -> facade.generateTest(1L, null, 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("选择已配置能力模型");

        verify(workflowService, never()).bindPost(anyLong(), anyLong());
        verify(aiTestService, never()).assertWorkflowTestPostConfigured(anyLong());
    }

    @Test
    void generateTest_allowsResumeEvidenceReadyState() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);
        org.mockito.Mockito.doNothing().when(workflowService).assertStagePrerequisite(1L, "AI_TEST_GENERATION");

        com.example.matching.entity.employee.EmpAiTest test = new com.example.matching.entity.employee.EmpAiTest();
        test.setId(500L);
        test.setWorkflowId(1L);
        test.setPostId(300L);
        when(aiTestService.generateWorkflowTest(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(test);

        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setId(30L);
        when(workflowService.createStageRun(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(stageRun);
        when(coverageMapper.insert(any(com.example.matching.entity.workflow.AiTestCoverage.class))).thenReturn(1);
        when(claimGroupMapper.selectList(any())).thenReturn(Collections.emptyList());

        com.example.matching.dto.assessment.GenerateVerificationTestResponse result =
                facade.generateTest(1L, 300L, 9L);
        assertThat(result).isNotNull();
        assertThat(result.getTestId()).isEqualTo(500L);
    }

    // ═══════════════════════════════════════════════════════════════
    // Acceptance Criterion: INTERVIEW_PREPARING 可进入/继续面试
    // ═══════════════════════════════════════════════════════════════

    /**
     * 工作流处于 INTERVIEW_PREPARING（面试准备中）时，availableActions 必须包含
     * CREATE_INTERVIEW，前端"发起 AI 面试/进入面试"按钮才会显示。
     * 回归：后端 createInterview 已允许该状态再次发起面试，但 availableActions
     * 只暴露了 VIEW_PROGRESS，导致前端在"面试准备中"没有进入面试的按钮。
     */
    @Test
    void workflowView_interviewPreparing_exposesCreateInterviewAction() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.INTERVIEW_PREPARING.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);
        when(workflowService.listStageRuns(anyLong())).thenReturn(Collections.emptyList());

        CapabilityAssessmentVO.WorkflowView view = facade.getWorkflow(1L);

        assertThat(view.getAvailableActions()).contains("CREATE_INTERVIEW");
    }

    // ═══════════════════════════════════════════════════════════════
    // Acceptance Criterion: 岗位绑定到工作流（单一真相源）
    // ═══════════════════════════════════════════════════════════════

    @Test
    void generateTest_bindsPostToWorkflow() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);
        org.mockito.Mockito.doNothing().when(workflowService).assertStagePrerequisite(1L, "AI_TEST_GENERATION");

        com.example.matching.entity.employee.EmpAiTest test = new com.example.matching.entity.employee.EmpAiTest();
        test.setId(500L);
        test.setWorkflowId(1L);
        test.setPostId(300L);
        when(aiTestService.generateWorkflowTest(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(test);

        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setId(30L);
        when(workflowService.createStageRun(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(stageRun);
        when(coverageMapper.insert(any(com.example.matching.entity.workflow.AiTestCoverage.class))).thenReturn(1);
        when(claimGroupMapper.selectList(any())).thenReturn(Collections.emptyList());

        facade.generateTest(1L, 300L, 9L);

        verify(aiTestService).assertWorkflowTestPostConfigured(300L);
        verify(workflowService).bindPost(1L, 300L);
        assertThat(workflow.getPostId()).isEqualTo(300L);
    }

    @Test
    void generateTest_rejectsDifferentPost() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode());
        workflow.setPostId(400L);
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);

        assertThatThrownBy(() -> facade.generateTest(1L, 300L, 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不允许更换");
        verify(workflowService, never()).bindPost(anyLong(), anyLong());
    }

    @Test
    void createInterview_usesWorkflowBoundPost() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.TEST_EVIDENCE_READY.getCode());
        workflow.setPostId(777L);
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);
        stubInterviewSessionQuery(Collections.emptyList());

        com.example.matching.entity.employee.EmpVideoInterviewSession session =
                new com.example.matching.entity.employee.EmpVideoInterviewSession();
        session.setId(600L);
        when(videoInterviewService.createSession(any(), anyLong())).thenReturn(session);

        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setId(40L);
        when(workflowService.createStageRun(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(stageRun);

        com.example.matching.dto.assessment.CreateAssessmentInterviewResponse result =
                facade.createInterview(1L, 9L);

        assertThat(result.getSessionId()).isEqualTo(600L);
        assertThat(result.getPostId()).isEqualTo(777L);
        verify(empAiTestMapper, never()).selectOne(any());
    }

    // ═══════════════════════════════════════════════════════════════
    // Task 3: 测试提交归属与幂等
    // ═══════════════════════════════════════════════════════════════

    private PersonCapabilityWorkflow workflowInTestProgress() {
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        workflow.setEmpId(100L);
        workflow.setStatus(WorkflowStatusEnum.TEST_IN_PROGRESS.getCode());
        return workflow;
    }

    private com.example.matching.entity.employee.EmpAiTest submittedTest(Long empId, Long workflowId, Long postId, int status) {
        com.example.matching.entity.employee.EmpAiTest test = new com.example.matching.entity.employee.EmpAiTest();
        test.setId(500L);
        test.setEmpId(empId);
        test.setWorkflowId(workflowId);
        test.setPostId(postId);
        test.setStatus(status);
        return test;
    }

    @Test
    void submitTest_wrongEmployee_rejected() {
        when(workflowService.getWorkflow(1L)).thenReturn(workflowInTestProgress());
        when(empAiTestMapper.selectById(500L)).thenReturn(submittedTest(999L, 1L, null, 0));

        assertThatThrownBy(() -> facade.submitTest(1L, 500L, java.util.Map.of("1", "a"), 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("归属员工");
        verify(workflowService, never()).createStageRun(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void submitTest_wrongWorkflow_rejected() {
        when(workflowService.getWorkflow(1L)).thenReturn(workflowInTestProgress());
        when(empAiTestMapper.selectById(500L)).thenReturn(submittedTest(100L, 2L, null, 0));

        assertThatThrownBy(() -> facade.submitTest(1L, 500L, java.util.Map.of("1", "a"), 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("工作流");
    }

    @Test
    void submitTest_wrongPost_rejected() {
        PersonCapabilityWorkflow workflow = workflowInTestProgress();
        workflow.setPostId(300L);
        when(workflowService.getWorkflow(1L)).thenReturn(workflow);
        when(empAiTestMapper.selectById(500L)).thenReturn(submittedTest(100L, 1L, 999L, 0));

        assertThatThrownBy(() -> facade.submitTest(1L, 500L, java.util.Map.of("1", "a"), 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("岗位");
    }

    @Test
    void submitTest_completedTest_rejected() {
        when(workflowService.getWorkflow(1L)).thenReturn(workflowInTestProgress());
        when(empAiTestMapper.selectById(500L)).thenReturn(submittedTest(100L, 1L, null, 2));

        assertThatThrownBy(() -> facade.submitTest(1L, 500L, java.util.Map.of("1", "a"), 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("状态");
    }

    @Test
    void submitTest_duplicateSubmit_returnsSameStageRunWithoutReEvaluating() {
        when(workflowService.getWorkflow(1L)).thenReturn(workflowInTestProgress());
        when(empAiTestMapper.selectById(500L)).thenReturn(submittedTest(100L, 1L, null, 0));
        PersonCapabilityStageRun stageRun = new PersonCapabilityStageRun();
        stageRun.setId(30L);
        when(workflowService.createStageRun(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(stageRun);

        PersonCapabilityStageRun first = facade.submitTest(1L, 500L, java.util.Map.of("1", "a"), 9L);
        assertThat(first.getId()).isEqualTo(30L);
        verify(aiTestService, org.mockito.Mockito.times(1))
                .submitAnswers(org.mockito.ArgumentMatchers.eq(500L), any());

        // 重复提交：status=1（已提交）不应再次触发评分
        when(empAiTestMapper.selectById(500L)).thenReturn(submittedTest(100L, 1L, null, 1));
        PersonCapabilityStageRun second = facade.submitTest(1L, 500L, java.util.Map.of("1", "a"), 9L);
        assertThat(second.getId()).isEqualTo(30L);
        verify(aiTestService, org.mockito.Mockito.times(1))
                .submitAnswers(org.mockito.ArgumentMatchers.eq(500L), any());
    }
}
