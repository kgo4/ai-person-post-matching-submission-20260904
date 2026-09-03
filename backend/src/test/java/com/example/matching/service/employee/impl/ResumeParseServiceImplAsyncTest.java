package com.example.matching.service.employee.impl;

import com.example.matching.agent.service.EmployeeAbilityAgentService;
import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.config.RabbitMQConfig;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.event.ResumeParseQueuedEvent;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.agent.AgentBusinessApplyService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.service.system.AbilityAdmissionService;
import com.example.matching.service.system.AbilityTagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeParseServiceImplAsyncTest {

    private static final long EMPLOYEE_ID = 9_999_991L;

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisPlusLambdaCache() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.example.matching.entity.employee.EmpResumeParse.class);
    }

    @Mock private EmployeeAbilityAgentService employeeAbilityAgentService;
    @Mock private AgentBusinessApplyService agentBusinessApplyService;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AbilityTagService abilityTagService;
    @Mock private AbilityAdmissionService abilityAdmissionService;
    @Mock private EmpResumeParseMapper resumeParseMapper;
    @Mock private RabbitTemplate rabbitTemplate;
@Mock private EventOutboxDispatcher outboxDispatcher;
@Mock private ResumeFileParser fileParser;
@Mock private ResumeAbilityImportService abilityImportService;
@Mock private CapabilityAssessmentWorkflowService workflowService;
@Mock private com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;

    @AfterEach
    void cleanUploadedFile() throws Exception {
        Path uploadDirectory = Path.of("uploads", "resume", String.valueOf(EMPLOYEE_ID));
        if (Files.exists(uploadDirectory)) {
            try (var paths = Files.walk(uploadDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
            }
        }
    }

    @Test
    void uploadAndParse_queuesTaskWithoutCallingAiInRequestThread() {
        ResumeParseServiceImpl service = createService();
        byte[] content = "%PDF-1.4\nresume content".getBytes();
        when(fileParser.getFileType("candidate.pdf")).thenReturn("pdf");
        when(fileParser.computeSha256(any())).thenReturn("hash-abc");
        when(fileParser.saveFile(any(), any(), any())).thenReturn("/tmp/resume.pdf");
        doAnswer(invocation -> {
            invocation.getArgument(0, EmpResumeParse.class).setId(42L);
            return 1;
        }).when(resumeParseMapper).insert(any(EmpResumeParse.class));

        var result = service.uploadAndParse(EMPLOYEE_ID, "candidate.pdf", content, 100L);

        assertThat(result.getStatus()).isZero();
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isInstanceOf(ResumeParseQueuedEvent.class)
                .extracting(event -> ((ResumeParseQueuedEvent) event).parseId())
                .isEqualTo(42L);
        verifyNoInteractions(employeeAbilityAgentService);
    }

    @Test
    void reparse_queuesTaskWithoutCallingAiInRequestThread() {
        ResumeParseServiceImpl service = createService();
        EmpResumeParse record = new EmpResumeParse();
        record.setId(43L);
        record.setEmpId(EMPLOYEE_ID);
        record.setParsedContent("previously extracted resume content");
        record.setStatus(2);
        org.mockito.Mockito.when(resumeParseMapper.selectById(43L)).thenReturn(record);

        var result = service.reparse(43L);

        assertThat(result.getStatus()).isZero();
        assertThat(result.getErrorMessage()).isNull();
        verify(resumeParseMapper).updateById(any(EmpResumeParse.class));
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isInstanceOf(ResumeParseQueuedEvent.class)
                .extracting(event -> ((ResumeParseQueuedEvent) event).parseId())
                .isEqualTo(43L);
        verifyNoInteractions(employeeAbilityAgentService);
    }

    // ==================== processQueuedParse ====================

    @Test
    void processQueuedParse_skipsAlreadyProcessedRecord() {
        ResumeParseServiceImpl service = createService();
        EmpResumeParse record = new EmpResumeParse();
        record.setId(50L);
        record.setStatus(2); // 已完成
        when(resumeParseMapper.selectById(50L)).thenReturn(record);

        service.processQueuedParse(50L);

        // 不应调用 AI 或更新记录
        verifyNoInteractions(employeeAbilityAgentService);
        verify(resumeParseMapper, never()).updateById(any(EmpResumeParse.class));
    }

    @Test
    void processQueuedParse_handlesRecordNotFound() {
        ResumeParseServiceImpl service = createService();
        when(resumeParseMapper.selectById(999L)).thenReturn(null);

        // 不应抛异常
        service.processQueuedParse(999L);

        verifyNoInteractions(employeeAbilityAgentService);
    }

    @Test
    void processQueuedParse_publishesLifecycleEventAfterClaimingTask() {
        ResumeParseServiceImpl service = createService();
        EmpResumeParse record = new EmpResumeParse();
        record.setId(52L);
        record.setEmpId(EMPLOYEE_ID);
        record.setStatus(0);
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(700L);
        workflow.setEmpId(EMPLOYEE_ID);
        workflow.setStatus(com.example.matching.common.enums.WorkflowStatusEnum.RESUME_REQUIRED.getCode());
        when(resumeParseMapper.selectById(52L)).thenReturn(record);
        when(resumeParseMapper.update(any(), any())).thenReturn(1);
        when(workflowService.getActiveWorkflow(EMPLOYEE_ID)).thenReturn(workflow);
        when(workflowService.createStageRun(eq(700L), eq("RESUME_PARSE"), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    com.example.matching.entity.workflow.PersonCapabilityStageRun run =
                            new com.example.matching.entity.workflow.PersonCapabilityStageRun();
                    run.setId(7001L);
                    run.setWorkflowId(700L);
                    return run;
                });

        service.processQueuedParse(52L);

        // 不再直接推进工作流：发布 TASK_CLAIMED 生命周期事件，由协调器统一推进
        ArgumentCaptor<com.example.matching.event.CapabilityStageLifecycleEvent> captor =
                ArgumentCaptor.forClass(com.example.matching.event.CapabilityStageLifecycleEvent.class);
        verify(lifecycleEventPublisher).publish(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.example.matching.common.enums.StageLifecycleEventType.TASK_CLAIMED,
                captor.getValue().eventType());
        org.junit.jupiter.api.Assertions.assertEquals(700L, captor.getValue().workflowId());
        org.junit.jupiter.api.Assertions.assertEquals("RESUME_PARSE", captor.getValue().stageType());
    }

    @Test
    void processQueuedParse_handlesDispatchFailed() {
        ResumeParseServiceImpl service = createService();
        EmpResumeParse record = new EmpResumeParse();
        record.setId(51L);
        record.setStatus(0);
        when(resumeParseMapper.selectById(51L)).thenReturn(record);

        service.markTaskDispatchFailed(51L, "MQ不可用");

        assertThat(record.getStatus()).isEqualTo(3);
        assertThat(record.getErrorMessage()).isEqualTo("MQ不可用");
        assertThat(record.getLastErrorType()).isEqualTo("PERMANENT");
        verify(resumeParseMapper).updateById(any(EmpResumeParse.class));
    }

    // ==================== retryFailedTask ====================

    @Test
    void retryFailedTask_resetsAndRequeues() {
        ResumeParseServiceImpl service = createService();
        EmpResumeParse record = new EmpResumeParse();
        record.setId(60L);
        record.setStatus(3); // 失败
        record.setErrorMessage("AI超时");
        when(resumeParseMapper.selectById(60L)).thenReturn(record);

        var result = service.retryFailedTask(60L);

        assertThat(result.getStatus()).isZero();
        assertThat(result.getErrorMessage()).isNull();
        verify(outboxDispatcher).enqueue(
                eq("RESUME_PARSE"),
                eq(RabbitMQConfig.MATCHING_EXCHANGE),
                eq("resume.parse.execute"),
                eq(60L));
    }

    @Test
    void retryFailedTask_throwsWhenStatusNotAllowed() {
        ResumeParseServiceImpl service = createService();
        EmpResumeParse record = new EmpResumeParse();
        record.setId(61L);
        record.setStatus(2); // 已完成，不允许重试
        when(resumeParseMapper.selectById(61L)).thenReturn(record);

        assertThatThrownBy(() -> service.retryFailedTask(61L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void retryFailedTask_throwsWhenRecordNotFound() {
        ResumeParseServiceImpl service = createService();
        when(resumeParseMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.retryFailedTask(999L))
                .isInstanceOf(BusinessException.class);
    }

    // ==================== recoverZombieTasks ====================

    @Test
    void recoverZombieTasks_recoversStuckTasks() {
        ResumeParseServiceImpl service = createService();
        EmpResumeParse zombie = new EmpResumeParse();
        zombie.setId(70L);
        zombie.setStatus(1); // 处理中
        zombie.setRetryCount(0);
        zombie.setProcessingStartedAt(LocalDateTime.now().minusMinutes(15)); // 超过10分钟
        when(resumeParseMapper.selectList(any())).thenReturn(List.of(zombie));
        when(resumeParseMapper.update(any(), any())).thenReturn(1);

        int recovered = service.recoverZombieTasks();

        assertThat(recovered).isEqualTo(1);
        // CAS 条件更新已执行（状态在 DB 侧变更，实体对象不随之修改）
        verify(resumeParseMapper).update(any(), any());
        verify(outboxDispatcher).enqueue(
                eq("RESUME_PARSE"),
                eq(RabbitMQConfig.MATCHING_EXCHANGE),
                eq("resume.parse.execute"),
                eq(70L));
    }

    @Test
    void recoverZombieTasks_marksMaxRetriesAsFailed() {
        ResumeParseServiceImpl service = createService();
        EmpResumeParse zombie = new EmpResumeParse();
        zombie.setId(71L);
        zombie.setStatus(1);
        zombie.setRetryCount(3); // 已达最大重试
        zombie.setProcessingStartedAt(LocalDateTime.now().minusMinutes(15));
        when(resumeParseMapper.selectList(any())).thenReturn(List.of(zombie));
        when(resumeParseMapper.update(any(), any())).thenReturn(1);

        int recovered = service.recoverZombieTasks();

        assertThat(recovered).isZero();
        // CAS 条件更新已执行（FAILED 状态在 DB 侧，实体对象不随之修改）
        verify(resumeParseMapper).update(any(), any());
        verify(outboxDispatcher, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void recoverZombieTasks_returnsZeroWhenNoZombies() {
        ResumeParseServiceImpl service = createService();
        when(resumeParseMapper.selectList(any())).thenReturn(Collections.emptyList());

        int recovered = service.recoverZombieTasks();

        assertThat(recovered).isZero();
    }

    // ==================== reparse edge cases ====================

    @Test
    void reparse_throwsWhenNoParsedContent() {
        ResumeParseServiceImpl service = createService();
        EmpResumeParse record = new EmpResumeParse();
        record.setId(80L);
        record.setStatus(2);
        record.setParsedContent(null); // 没有解析原文
        when(resumeParseMapper.selectById(80L)).thenReturn(record);

        assertThatThrownBy(() -> service.reparse(80L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void saveResumeEvidenceForWorkflow_publishesNoEvidenceWhenNoClaims() {
        // 简历解析成功但无能力主张时，解析阶段完成，证据提取阶段必须发布 NO_EVIDENCE。
        ResumeParseServiceImpl service = createService();
        EmpResumeParse record = new EmpResumeParse();
        record.setId(36L);
        record.setEmpId(EMPLOYEE_ID);
        record.setStatus(2);
        record.setAiAnalysisResult("{\"conclusion\":\"无能力信息\"}");
        when(resumeParseMapper.selectById(36L)).thenReturn(record);
        PersonCapabilityWorkflow workflow = new PersonCapabilityWorkflow();
        workflow.setId(1L);
        when(workflowService.getActiveWorkflow(EMPLOYEE_ID)).thenReturn(workflow);
        PersonCapabilityStageRun parseRun = new PersonCapabilityStageRun();
        parseRun.setId(10L);
        PersonCapabilityStageRun extractRun = new PersonCapabilityStageRun();
        extractRun.setId(11L);
        when(workflowService.createStageRun(any(), eq("RESUME_PARSE"), any(), any(), any(), any())).thenReturn(parseRun);
        when(workflowService.createStageRun(any(), eq("RESUME_CLAIM_EXTRACTION"), any(), any(), any(), any())).thenReturn(extractRun);

        int saved = service.saveResumeEvidenceForWorkflow(36L);

        assertThat(saved).isZero();
        // parseRun 成功，extractRun 无证据
        verify(lifecycleEventPublisher).publish(argThat(ev ->
                ev != null
                        && Long.valueOf(1L).equals(ev.workflowId())
                        && "RESUME_PARSE".equals(ev.stageType())
                        && StageLifecycleEventType.TASK_SUCCEEDED == ev.eventType()));
        verify(lifecycleEventPublisher).publish(argThat(ev ->
                ev != null
                        && Long.valueOf(1L).equals(ev.workflowId())
                        && "RESUME_CLAIM_EXTRACTION".equals(ev.stageType())
                        && StageLifecycleEventType.NO_EVIDENCE == ev.eventType()));
    }

    private ResumeParseServiceImpl createService() {
        ResumeParseServiceImpl service = new ResumeParseServiceImpl(
                new ObjectMapper(), eventPublisher, fileParser, abilityImportService, outboxDispatcher,
                new com.example.matching.common.util.PersonAbilityClaimNormalizer(new ObjectMapper()),
                workflowService, null, lifecycleEventPublisher);
        ReflectionTestUtils.setField(service, "baseMapper", resumeParseMapper);
        return service;
    }
}
