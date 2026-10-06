package com.example.matching.service.employee.impl;

import com.example.matching.common.exception.AiServiceException;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.employee.video.VideoInterviewCreateDTO;
import com.example.matching.dto.employee.video.VideoInterviewFrameDTO;
import com.example.matching.dto.employee.video.VideoInterviewImportDTO;
import com.example.matching.dto.employee.video.VideoInterviewQuestionGenerateDTO;
import com.example.matching.dto.interview.CompetencyReport;
import com.example.matching.entity.employee.EmpVideoInterviewAbility;
import com.example.matching.entity.employee.EmpVideoInterviewEvidence;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.mapper.employee.EmpVideoInterviewAbilityMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewEvidenceMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.ability.PersonAbilityProfileAgent;
import com.example.matching.service.assessment.InterviewAssessmentEvidenceService;
import com.example.matching.service.interview.AIInterviewAgent;
import com.example.matching.service.interview.InterviewSessionManager;
import com.example.matching.service.interview.InterviewWebSocketTicketService;
import com.example.matching.service.system.SystemAiModelConfigService;
import com.example.matching.vo.employee.video.VideoInterviewDetailVO;
import com.example.matching.vo.employee.video.VideoInterviewWsTicketVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * {@link VideoInterviewServiceImpl} 单元测试。
 *
 * <p>LLM / ASR / 视频处理（AIInterviewAgent、InterviewSessionManager、visualAnalyzer）一律 mock，
 * 不联网。覆盖会话创建、题目生成、抽帧、状态流转、分析链路（含降级）与查询组装。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoInterviewServiceImplTest {

    @Mock private EmpVideoInterviewSessionMapper sessionMapper;
    @Mock private EmpVideoInterviewQuestionMapper questionMapper;
    @Mock private EmpVideoInterviewEvidenceMapper evidenceMapper;
    @Mock private EmpVideoInterviewAbilityMapper abilityMapper;
    @Mock private TalentQueryPort talentQueryPort;
    @Mock private PostQueryPort postQueryPort;
    @Mock private TagQueryPort tagQueryPort;
    @Mock private InterviewSessionManager interviewSessionManager;
    @Mock private InterviewWebSocketTicketService ticketService;
    @Mock private AIInterviewAgent aiInterviewAgent;
    @Mock private PersonAbilityProfileAgent personAbilityProfileAgent;
    @Mock private VideoInterviewQuestionBuilder questionBuilder;
    @Mock private VideoInterviewVisualAnalyzer visualAnalyzer;
    @Mock private SystemAiModelConfigService systemAiModelConfigService;
    @Mock private CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;
    @Mock private InterviewAssessmentEvidenceService interviewAssessmentEvidenceService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Executor aiTaskExecutor = Runnable::run;

    private VideoInterviewServiceImpl service;

    @BeforeAll
    static void initMpCache() {
        var cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), EmpVideoInterviewSession.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), EmpVideoInterviewQuestion.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), EmpVideoInterviewEvidence.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), EmpVideoInterviewAbility.class);
    }

    @BeforeEach
    void setUp() {
        service = new VideoInterviewServiceImpl(
                sessionMapper,
                questionMapper,
                evidenceMapper,
                abilityMapper,
                talentQueryPort,
                postQueryPort,
                tagQueryPort,
                objectMapper,
                interviewSessionManager,
                ticketService,
                aiInterviewAgent,
                personAbilityProfileAgent,
                mock(com.example.matching.integration.volcengine.VideoInterviewPromptBuilder.class),
                visualAnalyzer,
                questionBuilder,
                systemAiModelConfigService,
                lifecycleEventPublisher,
                interviewAssessmentEvidenceService,
                aiTaskExecutor);
    }

    private EmpVideoInterviewSession session(long id, int status, Long workflowId) {
        EmpVideoInterviewSession s = new EmpVideoInterviewSession();
        s.setId(id);
        s.setEmpId(100L);
        s.setPostId(200L);
        s.setWorkflowId(workflowId);
        s.setStatus(status);
        s.setCreatedBy(7L);
        return s;
    }

    // ==================== createSession ====================

    @Test
    @DisplayName("createSession：员工存在且岗位有模型 → 插入会话并回填ID")
    void createSession_success() {
        VideoInterviewCreateDTO dto = new VideoInterviewCreateDTO();
        dto.setEmpId(100L);
        dto.setPostId(200L);
        dto.setSessionName("Java面试");
        dto.setInterviewMode("POST_BASED");
        when(talentQueryPort.getEmployeeById(100L))
                .thenReturn(new TalentQueryPort.EmployeeDTO(100L, "张三", "E1", 1, "P5", 1L, 200L, 1));
        when(postQueryPort.countRequirementsByPostId(200L)).thenReturn(3L);
        doAnswer(inv -> {
            EmpVideoInterviewSession s = inv.getArgument(0);
            if (s.getId() == null) s.setId(11L);
            return 1;
        }).when(sessionMapper).insert(any(EmpVideoInterviewSession.class));

        EmpVideoInterviewSession result = service.createSession(dto, 9L);

        assertNotNull(result);
        assertEquals(11L, result.getId());
        assertEquals(0, result.getStatus());
        assertEquals(9L, result.getCreatedBy());
    }

    @Test
    @DisplayName("createSession：员工不存在 → 抛 404")
    void createSession_empNotFound() {
        VideoInterviewCreateDTO dto = new VideoInterviewCreateDTO();
        dto.setEmpId(999L);
        dto.setInterviewMode("POST_BASED");
        when(talentQueryPort.getEmployeeById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createSession(dto, 9L));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("createSession：POST_BASED 未指定岗位 → 抛 400")
    void createSession_postBasedNoPostId() {
        VideoInterviewCreateDTO dto = new VideoInterviewCreateDTO();
        dto.setEmpId(100L);
        dto.setInterviewMode("POST_BASED");
        when(talentQueryPort.getEmployeeById(100L))
                .thenReturn(new TalentQueryPort.EmployeeDTO(100L, "张三", "E1", 1, "P5", 1L, null, 1));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createSession(dto, 9L));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("createSession：岗位无能力模型 → 使用通用问题仍创建成功")
    void createSession_postNoModel() {
        VideoInterviewCreateDTO dto = new VideoInterviewCreateDTO();
        dto.setEmpId(100L);
        dto.setPostId(200L);
        dto.setInterviewMode("GENERAL");
        when(talentQueryPort.getEmployeeById(100L))
                .thenReturn(new TalentQueryPort.EmployeeDTO(100L, "张三", "E1", 1, "P5", 1L, null, 1));
        when(postQueryPort.countRequirementsByPostId(200L)).thenReturn(0L);
        doAnswer(inv -> {
            EmpVideoInterviewSession s = inv.getArgument(0);
            s.setId(12L);
            return 1;
        }).when(sessionMapper).insert(any(EmpVideoInterviewSession.class));

        EmpVideoInterviewSession result = service.createSession(dto, 9L);
        assertEquals(12L, result.getId());
    }

    // ==================== generateQuestions ====================

    @Test
    @DisplayName("generateQuestions：AI 生成成功 → 状态置为已生成题目")
    void generateQuestions_success() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 0, null));
        when(systemAiModelConfigService.getInterviewQuestionCount()).thenReturn(5);
        when(questionMapper.selectCount(any())).thenReturn(5L);

        service.generateQuestions(1L, new VideoInterviewQuestionGenerateDTO());

        verify(sessionMapper).updateById(argThat((EmpVideoInterviewSession s) ->
                s.getStatus() == 1 && s.getQuestionCount() == 5));
    }

    @Test
    @DisplayName("generateQuestions：会话不存在 → 抛 404")
    void generateQuestions_sessionNotFound() {
        when(sessionMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateQuestions(1L, new VideoInterviewQuestionGenerateDTO()));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("generateQuestions：状态不是 CREATED → 抛 400")
    void generateQuestions_wrongStatus() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 2, null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateQuestions(1L, new VideoInterviewQuestionGenerateDTO()));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("generateQuestions：AI 异常 + 无工作流 → 落库通用题目")
    void generateQuestions_agentFails_fallbackGeneral() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 0, null));
        when(systemAiModelConfigService.getInterviewQuestionCount()).thenReturn(4);
        when(aiInterviewAgent.generateInterviewPlan(any())).thenThrow(new RuntimeException("LLM down"));
        EmpVideoInterviewQuestion q = new EmpVideoInterviewQuestion();
        q.setSessionId(1L);
        when(questionBuilder.buildGeneralQuestions(1L, 1, 4)).thenReturn(List.of(q));
        when(questionMapper.selectCount(any())).thenReturn(4L, 4L);

        service.generateQuestions(1L, new VideoInterviewQuestionGenerateDTO());

        verify(questionMapper, atLeastOnce()).insert(any(EmpVideoInterviewQuestion.class));
        verify(sessionMapper).updateById(any(EmpVideoInterviewSession.class));
    }

    @Test
    @DisplayName("generateQuestions：AI 异常 + 属工作流 → 抛 502（禁止通用题兜底）")
    void generateQuestions_agentFails_workflowThrows() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 0, 500L));
        when(systemAiModelConfigService.getInterviewQuestionCount()).thenReturn(4);
        when(aiInterviewAgent.generateInterviewPlan(any())).thenThrow(new RuntimeException("LLM down"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateQuestions(1L, new VideoInterviewQuestionGenerateDTO()));
        assertEquals(502, ex.getCode());
    }

    @Test
    @DisplayName("generateQuestions：AI 成功但无题落库 + 工作流 → 抛 502")
    void generateQuestions_noPersisted_workflowThrows() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 0, 500L));
        when(systemAiModelConfigService.getInterviewQuestionCount()).thenReturn(4);
        when(questionMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateQuestions(1L, new VideoInterviewQuestionGenerateDTO()));
        assertEquals(502, ex.getCode());
    }

    @Test
    @DisplayName("generateQuestions：AI 成功但落库为 0 + 无工作流 → 兜底后仍为 0 抛 500")
    void generateQuestions_noPersisted_generalThrows500() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 0, null));
        when(systemAiModelConfigService.getInterviewQuestionCount()).thenReturn(4);
        when(questionMapper.selectCount(any())).thenReturn(0L);
        when(questionBuilder.buildGeneralQuestions(1L, 1, 4)).thenReturn(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateQuestions(1L, new VideoInterviewQuestionGenerateDTO()));
        assertEquals(500, ex.getCode());
    }

    // ==================== issueWebSocketTicket ====================

    @Test
    @DisplayName("issueWebSocketTicket：创建者匹配 → 返回票据")
    void issueWebSocketTicket_success() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 0, null));
        when(ticketService.issue(1L, 7L))
                .thenReturn(new InterviewWebSocketTicketService.IssuedTicket("tk", 12345L));

        VideoInterviewWsTicketVO vo = service.issueWebSocketTicket(1L, 7L);

        assertEquals("tk", vo.getTicket());
        assertEquals(12345L, vo.getExpiresAt());
    }

    @Test
    @DisplayName("issueWebSocketTicket：会话不存在 → 404")
    void issueWebSocketTicket_notFound() {
        when(sessionMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.issueWebSocketTicket(1L, 7L));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("issueWebSocketTicket：非创建者 → 403")
    void issueWebSocketTicket_forbidden() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 0, null));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.issueWebSocketTicket(1L, 8L));
        assertEquals(403, ex.getCode());
    }

    // ==================== uploadFrame ====================

    @Test
    @DisplayName("uploadFrame：合法 JPEG → 插入证据")
    void uploadFrame_success() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 2, null));
        VideoInterviewFrameDTO dto = new VideoInterviewFrameDTO();
        dto.setQuestionOrder(1);
        dto.setCaptureSecond(10);
        dto.setImageDataUrl("data:image/jpeg;base64,AAAA");
        when(questionBuilder.findQuestionId(1L, 1)).thenReturn(50L);
        when(visualAnalyzer.buildFrameRefsJson(1L, dto)).thenReturn("{}");

        service.uploadFrame(1L, dto);

        verify(evidenceMapper).insert(any(EmpVideoInterviewEvidence.class));
    }

    @Test
    @DisplayName("uploadFrame：会话不存在 → 404")
    void uploadFrame_notFound() {
        when(sessionMapper.selectById(1L)).thenReturn(null);
        VideoInterviewFrameDTO dto = new VideoInterviewFrameDTO();
        dto.setImageDataUrl("data:image/jpeg;base64,AAAA");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.uploadFrame(1L, dto));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("uploadFrame：非 JPEG dataUrl → 400")
    void uploadFrame_badDataUrl() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 2, null));
        VideoInterviewFrameDTO dto = new VideoInterviewFrameDTO();
        dto.setImageDataUrl("data:image/png;base64,AAAA");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.uploadFrame(1L, dto));
        assertEquals(400, ex.getCode());
    }

    // ==================== 状态流转 ====================

    @Test
    @DisplayName("startInterview：可开始 → 委托管理器")
    void startInterview_success() throws Exception {
        when(interviewSessionManager.isStartable(1L)).thenReturn(true);
        service.startInterview(1L);
        verify(interviewSessionManager).startInterview(1L);
    }

    @Test
    @DisplayName("startInterview：已活跃 → 幂等返回不报错")
    void startInterview_alreadyActive() throws Exception {
        when(interviewSessionManager.isStartable(1L)).thenReturn(false);
        when(interviewSessionManager.isActive(1L)).thenReturn(true);
        service.startInterview(1L);
        verify(interviewSessionManager, never()).startInterview(1L);
    }

    @Test
    @DisplayName("startInterview：不可开始且不活跃 → 400")
    void startInterview_notAllowed() {
        when(interviewSessionManager.isStartable(1L)).thenReturn(false);
        when(interviewSessionManager.isActive(1L)).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.startInterview(1L));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("startInterview：底层抛非 AiServiceException → 包装为可重试 AiServiceException")
    void startInterview_wrapsException() throws Exception {
        when(interviewSessionManager.isStartable(1L)).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(interviewSessionManager).startInterview(1L);
        assertThrows(AiServiceException.class, () -> service.startInterview(1L));
    }

    @Test
    @DisplayName("startInterview：底层抛 AiServiceException → 原样抛出")
    void startInterview_rethrowsAiServiceException() throws Exception {
        when(interviewSessionManager.isStartable(1L)).thenReturn(true);
        AiServiceException original = AiServiceException.retryable("X", "y", "z");
        doThrow(original).when(interviewSessionManager).startInterview(1L);
        assertSame(original, assertThrows(AiServiceException.class, () -> service.startInterview(1L)));
    }

    @Test
    @DisplayName("nextQuestion：正常 → 委托 MANUAL_NEXT")
    void nextQuestion_success() throws Exception {
        service.nextQuestion(1L);
        verify(interviewSessionManager).nextQuestion(1L, "MANUAL_NEXT");
    }

    @Test
    @DisplayName("nextQuestion：普通异常 → 包装为可重试异常")
    void nextQuestion_wrapsException() throws Exception {
        doThrow(new RuntimeException("boom")).when(interviewSessionManager).nextQuestion(anyLong(), any());
        assertThrows(AiServiceException.class, () -> service.nextQuestion(1L));
    }

    @Test
    @DisplayName("finishInterview：正常 → 委托管理器")
    void finishInterview_success() throws Exception {
        service.finishInterview(1L);
        verify(interviewSessionManager).finishInterview(1L);
    }

    @Test
    @DisplayName("finishInterview：普通异常 → 包装为可重试异常")
    void finishInterview_wrapsException() throws Exception {
        doThrow(new RuntimeException("boom")).when(interviewSessionManager).finishInterview(1L);
        assertThrows(AiServiceException.class, () -> service.finishInterview(1L));
    }

    // ==================== analyze ====================

    @Test
    @DisplayName("analyze：成功抢占 CAS → 完成分析、写报告、发布证据推进")
    void analyze_success() {
        EmpVideoInterviewSession s = session(1L, 3, null);
        when(sessionMapper.selectById(1L)).thenReturn(s);
        when(sessionMapper.update(isNull(), any())).thenReturn(1);
        CompetencyReport report = new CompetencyReport(1L, 100L, 200L, 80, 75, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), "结论", "建议", false, null);
        when(aiInterviewAgent.generateCompetencyReport(1L)).thenReturn(report);

        service.analyze(1L);

        assertEquals(5, s.getStatus());
        assertEquals(BigDecimal.valueOf(80), s.getOverallScore());
        verify(sessionMapper, atLeastOnce()).updateById(any(EmpVideoInterviewSession.class));
    }

    @Test
    @DisplayName("analyze：CAS 抢占失败 → 直接返回不改状态")
    void analyze_claimFailed() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 4, null));
        when(sessionMapper.update(isNull(), any())).thenReturn(0);

        service.analyze(1L);

        verify(aiInterviewAgent, never()).generateCompetencyReport(anyLong());
        verify(sessionMapper, never()).updateById(any(EmpVideoInterviewSession.class));
    }

    @Test
    @DisplayName("analyze：会话不存在 → 404")
    void analyze_notFound() {
        when(sessionMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.analyze(1L));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("analyze：FAILED 且仍在评估回答 → 抛 409")
    void analyze_failedEvaluating() {
        EmpVideoInterviewSession s = session(1L, 7, null);
        s.setConversationState("EVALUATING_ANSWER");
        when(sessionMapper.selectById(1L)).thenReturn(s);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.analyze(1L));
        assertEquals(409, ex.getCode());
    }

    @Test
    @DisplayName("analyze：FAILED 可重置 → 重置后继续分析")
    void analyze_failedResettable() {
        EmpVideoInterviewSession failed = session(1L, 7, null);
        EmpVideoInterviewSession finished = session(1L, 3, null);
        when(sessionMapper.selectById(1L)).thenReturn(failed, finished);
        when(sessionMapper.resetFailedAnalysisToFinished(1L)).thenReturn(1);
        when(sessionMapper.update(isNull(), any())).thenReturn(1);
        when(aiInterviewAgent.generateCompetencyReport(1L)).thenReturn(null);

        service.analyze(1L);

        assertEquals(5, finished.getStatus());
    }

    @Test
    @DisplayName("analyze：报告为 degraded → 总分置空但状态完成")
    void analyze_degradedReport() {
        EmpVideoInterviewSession s = session(1L, 3, null);
        when(sessionMapper.selectById(1L)).thenReturn(s);
        when(sessionMapper.update(isNull(), any())).thenReturn(1);
        CompetencyReport report = new CompetencyReport(1L, 100L, 200L, 0, 0, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), "证据不足", null, true, "无观察");
        when(aiInterviewAgent.generateCompetencyReport(1L)).thenReturn(report);

        service.analyze(1L);

        assertNull(s.getOverallScore());
        assertEquals("证据不足", s.getSummaryReport());
    }

    @Test
    @DisplayName("analyze：工作流会话证据保存失败 → 回退 FINISHED 并记失败原因")
    void analyze_workflowEvidenceSaveFails() {
        EmpVideoInterviewSession s = session(1L, 3, 500L);
        when(sessionMapper.selectById(1L)).thenReturn(s);
        when(sessionMapper.update(isNull(), any())).thenReturn(1);
        when(aiInterviewAgent.generateCompetencyReport(1L)).thenReturn(null);
        doThrow(new RuntimeException("save failed")).when(interviewAssessmentEvidenceService)
                .saveInterviewEvidenceAndAdvance(500L, 100L, 1L);

        service.analyze(1L);

        verify(interviewAssessmentEvidenceService).saveInterviewEvidenceAndAdvance(500L, 100L, 1L);
        // CAS 抢占 1 次 + 回退更新 1 次
        verify(sessionMapper, times(2)).update(isNull(), any());
    }

    @Test
    @DisplayName("analyze：分析抛 AiServiceException → 状态 FAILED 并原样抛出")
    void analyze_aiServiceException() {
        EmpVideoInterviewSession s = session(1L, 3, null);
        when(sessionMapper.selectById(1L)).thenReturn(s);
        when(sessionMapper.update(isNull(), any())).thenReturn(1);
        AiServiceException ex = AiServiceException.retryable("Volcengine", "observe", "down");
        when(aiInterviewAgent.conductInterviewAndObserve(1L)).thenThrow(ex);

        assertSame(ex, assertThrows(AiServiceException.class, () -> service.analyze(1L)));
        assertEquals(7, s.getStatus());
    }

    @Test
    @DisplayName("analyze：分析抛普通异常 → 状态 FAILED 并包装抛出")
    void analyze_genericException() {
        EmpVideoInterviewSession s = session(1L, 3, null);
        when(sessionMapper.selectById(1L)).thenReturn(s);
        when(sessionMapper.update(isNull(), any())).thenReturn(1);
        when(aiInterviewAgent.conductInterviewAndObserve(1L)).thenThrow(new RuntimeException("boom"));

        assertThrows(AiServiceException.class, () -> service.analyze(1L));
        assertEquals(7, s.getStatus());
    }

    @Test
    @DisplayName("analyzeAsync：内部异常被吞掉不抛出")
    void analyzeAsync_swallows() {
        when(sessionMapper.selectById(1L)).thenReturn(null);
        assertDoesNotThrow(() -> service.analyzeAsync(1L));
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("listByEmpId：返回映射结果")
    void listByEmpId_ok() {
        when(sessionMapper.selectList(any())).thenReturn(List.of(session(1L, 0, null)));
        assertEquals(1, service.listByEmpId(100L).size());
    }

    @Test
    @DisplayName("listAll：返回全部会话")
    void listAll_ok() {
        when(sessionMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.listAll().isEmpty());
    }

    @Test
    @DisplayName("getDetail：组装题目/证据/能力（含标签回填与标签缺失）")
    void getDetail_success() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 3, null));

        EmpVideoInterviewQuestion q = new EmpVideoInterviewQuestion();
        q.setId(50L);
        q.setQuestionOrder(1);
        q.setQuestionText("自我介绍一下");
        when(questionMapper.selectList(any())).thenReturn(List.of(q));

        EmpVideoInterviewEvidence e = new EmpVideoInterviewEvidence();
        e.setId(60L);
        e.setQuestionId(50L);
        e.setEvidenceType("VISUAL");
        when(evidenceMapper.selectList(any())).thenReturn(List.of(e));

        EmpVideoInterviewAbility a1 = new EmpVideoInterviewAbility();
        a1.setId(70L);
        a1.setTagId(5L);
        a1.setImportedFlag(1);
        EmpVideoInterviewAbility a2 = new EmpVideoInterviewAbility();
        a2.setId(71L);
        a2.setTagId(6L);
        a2.setImportedFlag(0);
        when(abilityMapper.selectList(any())).thenReturn(List.of(a1, a2));
        when(tagQueryPort.getTagById(5L)).thenReturn(new TagQueryPort.TagDTO(5L, "Java", "java", "SKILL",
                null, 3, null, null, "SYS", null, null, null));
        when(tagQueryPort.getTagById(6L)).thenReturn(null);

        VideoInterviewDetailVO vo = service.getDetail(1L);

        assertEquals(1L, vo.getId());
        assertEquals(1, vo.getQuestions().size());
        assertEquals(1, vo.getEvidences().size());
        assertEquals(2, vo.getAbilities().size());
        assertEquals("Java", vo.getAbilities().get(0).getTagName());
        assertEquals("未知标签", vo.getAbilities().get(1).getTagName());
        assertTrue(vo.getAbilities().get(0).getImportedFlag());
    }

    @Test
    @DisplayName("getDetail：会话不存在 → 404")
    void getDetail_notFound() {
        when(sessionMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getDetail(1L));
        assertEquals(404, ex.getCode());
    }

    // ==================== importToAbilityProfile ====================

    @Test
    @DisplayName("importToAbilityProfile：已完成且非工作流 → 重定向画像融合并置 IMPORTED")
    void import_success() {
        EmpVideoInterviewSession s = session(1L, 5, null);
        when(sessionMapper.selectById(1L)).thenReturn(s);

        service.importToAbilityProfile(1L, new VideoInterviewImportDTO(), 9L);

        verify(aiInterviewAgent).conductInterviewAndObserve(1L);
        verify(personAbilityProfileAgent).buildProfileWithInterview(100L, 1L);
        assertEquals(6, s.getStatus());
        verify(sessionMapper).updateById(s);
    }

    @Test
    @DisplayName("importToAbilityProfile：会话不存在 → 404")
    void import_notFound() {
        when(sessionMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importToAbilityProfile(1L, new VideoInterviewImportDTO(), 9L));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("importToAbilityProfile：未完成分析 → 400")
    void import_notCompleted() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 2, null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importToAbilityProfile(1L, new VideoInterviewImportDTO(), 9L));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("importToAbilityProfile：属工作流 → 400 禁止旧接口导入")
    void import_workflowForbidden() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L, 5, 500L));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importToAbilityProfile(1L, new VideoInterviewImportDTO(), 9L));
        assertEquals(400, ex.getCode());
    }
}
