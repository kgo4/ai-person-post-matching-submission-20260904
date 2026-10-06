package com.example.matching.service.employee.impl;

import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.ai.validation.AssessmentQuestionBindingValidator;
import com.example.matching.common.exception.AiServiceException;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.assessment.AssessmentScopeDTO;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.agent.AgentBusinessApplyService;
import com.example.matching.service.assessment.AssessmentAgentArtifactService;
import com.example.matching.service.assessment.AssessmentScopeService;
import com.example.matching.service.assessment.CapabilityAssessmentOrchestrator;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.service.employee.AiTestAgent;
import com.example.matching.service.system.SysOperationLogService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiTestServiceImpl} 单元测试。
 * <p>覆盖题目生成 / 评分两条任务链路的抢占、状态校验、AI 合法/非法/空返回降级、
 * 可重试与不可重试失败、人工重放、结果查询与能力导入等分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiTestServiceImplTest {

    @Mock private AiTestAgent aiTestAgent;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    @Mock private PostPostMapper postPostMapper;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;
    @Mock private EmpAiTestMapper empAiTestMapper;
    @Mock private AgentBusinessApplyService agentBusinessApplyService;
    @Mock private EventOutboxDispatcher outboxDispatcher;
    @Mock private SysOperationLogService sysOperationLogService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;
    @Mock private PersonAbilityClaimGroupMapper claimGroupMapper;
    @Mock private PersonAbilityClaimMapper claimMapper;
    @Mock private CapabilityAssessmentOrchestrator assessmentOrchestrator;
    @Mock private AssessmentQuestionBindingValidator questionBindingValidator;
    @Mock private AssessmentAgentArtifactService artifactService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiTestServiceImpl service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, EmpAiTest.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PostPost.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PostAbilityModel.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, AbilityTag.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant, PersonAbilityClaimGroup.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant, com.example.matching.entity.ability.PersonAbilityClaim.class);
    }

    @BeforeEach
    void setUp() {
        service = new AiTestServiceImpl(
                aiTestAgent, objectMapper, abilityTagMapper, abilityEvidenceIngestionService,
                postPostMapper, postAbilityModelMapper, agentBusinessApplyService,
                outboxDispatcher, sysOperationLogService, eventPublisher, lifecycleEventPublisher,
                claimGroupMapper, claimMapper);
        service.setAssessmentOrchestrator(assessmentOrchestrator);
        service.setQuestionBindingValidator(questionBindingValidator);
        service.setArtifactService(artifactService);
        ReflectionTestUtils.setField(service, "baseMapper", empAiTestMapper);
        ReflectionTestUtils.setField(service, "questionCount", 5);
        // insert 回填主键
        lenient().doAnswer(inv -> {
            EmpAiTest e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(1001L);
            }
            return 1;
        }).when(empAiTestMapper).insert(any(EmpAiTest.class));
    }

    private EmpAiTest test(Long id, String title, Integer status) {
        EmpAiTest t = new EmpAiTest();
        t.setId(id);
        t.setEmpId(7L);
        t.setTestTitle(title);
        t.setAbilityTagId(11L);
        t.setAbilityTagName("Java");
        t.setStatus(status);
        return t;
    }

    private PostPost post() {
        PostPost post = new PostPost();
        post.setId(9L);
        post.setPostName("Java 工程师");
        post.setJobDescription("负责后端服务");
        return post;
    }

    private PostAbilityModel abilityModel(long tagId) {
        PostAbilityModel m = new PostAbilityModel();
        m.setId(tagId * 100);
        m.setPostId(9L);
        m.setTagId(tagId);
        m.setMinRequiredLevel(3);
        m.setIsCore(1);
        m.setIsRequired(1);
        return m;
    }

    // ==================== assertWorkflowTestPostConfigured ====================

    @Test
    @DisplayName("岗位未选择时抛出 400")
    void assertPostConfigured_nullPostIdThrows() {
        assertThrows(BusinessException.class, () -> service.assertWorkflowTestPostConfigured(null));
    }

    @Test
    @DisplayName("岗位不存在时抛出 404")
    void assertPostConfigured_missingPostThrows() {
        when(postPostMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.assertWorkflowTestPostConfigured(9L));
    }

    @Test
    @DisplayName("岗位未配置能力模型时抛出 400")
    void assertPostConfigured_notConfiguredThrows() {
        when(postPostMapper.selectById(9L)).thenReturn(post());
        when(postAbilityModelMapper.exists(any())).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.assertWorkflowTestPostConfigured(9L));
    }

    @Test
    @DisplayName("岗位已配置能力模型时校验通过")
    void assertPostConfigured_configuredPasses() {
        when(postPostMapper.selectById(9L)).thenReturn(post());
        when(postAbilityModelMapper.exists(any())).thenReturn(true);
        service.assertWorkflowTestPostConfigured(9L);
        verify(postAbilityModelMapper).exists(any());
    }

    // ==================== generateWorkflowTest ====================

    @Test
    @DisplayName("工作流测试创建记录、冻结范围并投递生成任务")
    void generateWorkflowTest_persistsAndEnqueues() {
        when(postPostMapper.selectById(9L)).thenReturn(post());
        when(postAbilityModelMapper.exists(any())).thenReturn(true);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(abilityModel(11L)));

        EmpAiTest result = service.generateWorkflowTest(7L, 55L, 9L, 99L);

        assertEquals(7L, result.getEmpId());
        assertEquals(55L, result.getWorkflowId());
        verify(assessmentOrchestrator).freezeScope(55L, 7L, 9L);
        verify(outboxDispatcher).enqueue(eq("AI_TEST"), anyString(), eq("ai.test.generate"), any());
    }

    @Test
    @DisplayName("岗位测试未配置能力模型时工作流测试创建失败")
    void generateWorkflowTest_unconfiguredPostThrows() {
        when(postPostMapper.selectById(9L)).thenReturn(post());
        when(postAbilityModelMapper.exists(any())).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.generateWorkflowTest(7L, 55L, 9L, 99L));
    }

    // ==================== processGenerateQuestions ====================

    @Test
    @DisplayName("题目生成抢占失败时直接返回，不查询记录")
    void processGenerateQuestions_claimFailsReturns() {
        when(empAiTestMapper.claimGeneration(1L)).thenReturn(0);

        service.processGenerateQuestions(1L);

        verify(empAiTestMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("题目生成记录不存在时标记失败")
    void processGenerateQuestions_missingRecordMarksFailed() {
        when(empAiTestMapper.claimGeneration(1L)).thenReturn(1);
        when(empAiTestMapper.selectById(1L)).thenReturn(null);

        service.processGenerateQuestions(1L);

        verify(empAiTestMapper).failGeneration(eq(1L), eq("BUSINESS_ERROR"), anyString());
    }

    @Test
    @DisplayName("岗位综合测试：AI 返回合法 JSON 时保存题目并标记成功")
    void processGenerateQuestions_postTestSuccess() {
        EmpAiTest test = test(1L, "Java 工程师 岗位综合能力测试", -1);
        test.setPostId(9L);
        when(empAiTestMapper.claimGeneration(1L)).thenReturn(1);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(postPostMapper.selectById(9L)).thenReturn(post());
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(abilityModel(11L)));
        when(abilityTagMapper.selectById(11L)).thenReturn(tag(11L, "Java"));
        when(aiTestAgent.generateQuestions(any())).thenReturn("[{\"text\":\"题目\"}]");

        service.processGenerateQuestions(1L);

        assertEquals(0, test.getStatus());
        assertNotNull(test.getQuestions());
        verify(empAiTestMapper).markGenerationSucceeded(1L);
    }

    @Test
    @DisplayName("岗位测试缺少岗位ID时永久失败")
    void processGenerateQuestions_postTestMissingPostIdFails() {
        EmpAiTest test = test(1L, "Java 工程师 岗位综合能力测试", -1);
        test.setPostId(null);
        when(empAiTestMapper.claimGeneration(1L)).thenReturn(1);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);

        service.processGenerateQuestions(1L);

        assertEquals(-1, test.getStatus());
        verify(empAiTestMapper).failGeneration(eq(1L), eq("BUSINESS_ERROR"), anyString());
        verify(aiTestAgent, never()).generateQuestions(any());
    }

    @Test
    @DisplayName("岗位已不存在时永久失败")
    void processGenerateQuestions_postMissingFails() {
        EmpAiTest test = test(1L, "Java 工程师 岗位综合能力测试", -1);
        test.setPostId(9L);
        when(empAiTestMapper.claimGeneration(1L)).thenReturn(1);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(postPostMapper.selectById(9L)).thenReturn(null);

        service.processGenerateQuestions(1L);

        assertEquals(-1, test.getStatus());
        verify(empAiTestMapper).failGeneration(eq(1L), eq("BUSINESS_ERROR"), anyString());
    }

    @Test
    @DisplayName("能力标签测试：AI 返回空响应时仍保存（空题目）")
    void processGenerateQuestions_tagTestEmptyAiResponse() {
        EmpAiTest test = test(1L, "Java 能力测试", -1);
        test.setAbilityTagId(11L);
        when(empAiTestMapper.claimGeneration(1L)).thenReturn(1);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(abilityTagMapper.selectById(11L)).thenReturn(tag(11L, "Java"));
        when(aiTestAgent.generateQuestions(any())).thenReturn("");

        service.processGenerateQuestions(1L);

        assertEquals(0, test.getStatus());
        assertEquals("", test.getQuestions());
        verify(empAiTestMapper).markGenerationSucceeded(1L);
    }

    @Test
    @DisplayName("能力标签测试：AI 返回非法 JSON 时仍落库为原始文本")
    void processGenerateQuestions_tagTestInvalidJson() {
        EmpAiTest test = test(1L, "Java 能力测试", -1);
        test.setAbilityTagId(11L);
        when(empAiTestMapper.claimGeneration(1L)).thenReturn(1);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(abilityTagMapper.selectById(11L)).thenReturn(tag(11L, "Java"));
        when(aiTestAgent.generateQuestions(any())).thenReturn("{not-json");

        service.processGenerateQuestions(1L);

        assertEquals(0, test.getStatus());
        assertEquals("{not-json", test.getQuestions());
    }

    @Test
    @DisplayName("能力标签已不存在时永久失败")
    void processGenerateQuestions_tagMissingFails() {
        EmpAiTest test = test(1L, "Java 能力测试", -1);
        test.setAbilityTagId(11L);
        when(empAiTestMapper.claimGeneration(1L)).thenReturn(1);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(abilityTagMapper.selectById(11L)).thenReturn(null);

        service.processGenerateQuestions(1L);

        assertEquals(-1, test.getStatus());
        verify(empAiTestMapper).failGeneration(eq(1L), eq("BUSINESS_ERROR"), anyString());
    }

    @Test
    @DisplayName("AI 服务可重试异常时重新投递生成任务")
    void processGenerateQuestions_retryableFailureReenqueues() {
        EmpAiTest test = test(1L, "Java 能力测试", -1);
        test.setAbilityTagId(11L);
        when(empAiTestMapper.claimGeneration(1L)).thenReturn(1);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(abilityTagMapper.selectById(11L)).thenReturn(tag(11L, "Java"));
        when(aiTestAgent.generateQuestions(any()))
                .thenThrow(new AiServiceException("AI", "generate", true, "模型超时"));

        service.processGenerateQuestions(1L);

        verify(empAiTestMapper).retryGeneration(eq(1L), eq("AI_SERVICE_ERROR"), anyString());
        verify(outboxDispatcher).enqueue(eq("AI_TEST"), anyString(), eq("ai.test.generate"), any());
    }

    @Test
    @DisplayName("重试次数耗尽时标记最终失败并发布事件")
    void processGenerateQuestions_retryExhaustedFails() {
        EmpAiTest test = test(1L, "Java 能力测试", -1);
        test.setAbilityTagId(11L);
        test.setRetryCount(3);
        test.setWorkflowId(55L);
        when(empAiTestMapper.claimGeneration(1L)).thenReturn(1);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(abilityTagMapper.selectById(11L)).thenReturn(tag(11L, "Java"));
        when(aiTestAgent.generateQuestions(any()))
                .thenThrow(new AiServiceException("AI", "generate", true, "模型超时"));

        service.processGenerateQuestions(1L);

        verify(empAiTestMapper).failGeneration(eq(1L), eq("AI_SERVICE_ERROR"), anyString());
        verify(lifecycleEventPublisher, org.mockito.Mockito.atLeastOnce()).publish(any());
    }

    // ==================== processEvaluateAnswers ====================

    @Test
    @DisplayName("评分抢占失败时直接返回")
    void processEvaluateAnswers_claimFailsReturns() {
        when(empAiTestMapper.claimEvaluation(1L)).thenReturn(0);

        service.processEvaluateAnswers(1L);

        verify(empAiTestMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("评分记录状态不允许时标记失败")
    void processEvaluateAnswers_invalidStatusMarksFailed() {
        when(empAiTestMapper.claimEvaluation(1L)).thenReturn(1);
        when(empAiTestMapper.selectById(1L)).thenReturn(test(1L, "Java 能力测试", 0));

        service.processEvaluateAnswers(1L);

        verify(empAiTestMapper).failEvaluation(eq(1L), eq("BUSINESS_ERROR"), anyString());
    }

    @Test
    @DisplayName("评分正常路径：保存评估结果并标记成功")
    void processEvaluateAnswers_success() {
        EmpAiTest test = test(1L, "Java 能力测试", 1);
        test.setQuestions("[]");
        test.setAnswers("{}");
        when(empAiTestMapper.claimEvaluation(1L)).thenReturn(1);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(aiTestAgent.evaluateAnswers(any())).thenReturn(new AiTestAgent.AiTestEvaluationResult(
                "VALID", "{\"score\":80}", new BigDecimal("80"), 4, "表现良好", List.of()));

        service.processEvaluateAnswers(1L);

        assertEquals(2, test.getStatus());
        assertEquals(4, test.getMasteryLevel());
        verify(empAiTestMapper).markEvaluationSucceeded(1L);
    }

    @Test
    @DisplayName("评分 AI 异常时按错误类型处理失败")
    void processEvaluateAnswers_failureHandled() {
        EmpAiTest test = test(1L, "Java 能力测试", 1);
        test.setQuestions("[]");
        test.setAnswers("{}");
        when(empAiTestMapper.claimEvaluation(1L)).thenReturn(1);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(aiTestAgent.evaluateAnswers(any())).thenThrow(new RuntimeException("boom"));

        service.processEvaluateAnswers(1L);

        verify(empAiTestMapper).retryEvaluation(eq(1L), eq("SYSTEM_ERROR"), anyString());
    }

    // ==================== submitAnswers ====================

    @Test
    @DisplayName("提交答案：记录不存在时抛出 404")
    void submitAnswers_missingTestThrows() {
        when(empAiTestMapper.selectById(1L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.submitAnswers(1L, Map.of()));
    }

    @Test
    @DisplayName("提交答案：状态不为 0 时拒绝")
    void submitAnswers_invalidStatusThrows() {
        when(empAiTestMapper.selectById(1L)).thenReturn(test(1L, "Java 能力测试", 2));

        assertThrows(BusinessException.class, () -> service.submitAnswers(1L, Map.of()));
    }

    @Test
    @DisplayName("提交答案：状态 0 时保存答案并投递评分任务")
    void submitAnswers_success() {
        EmpAiTest test = test(1L, "Java 能力测试", 0);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);

        EmpAiTest result = service.submitAnswers(1L, Map.of("q1", "a1"));

        assertEquals(1, result.getStatus());
        assertNotNull(result.getAnswers());
        verify(outboxDispatcher).enqueue(eq("AI_TEST"), anyString(), eq("ai.test.evaluate"), any());
    }

    // ==================== redeliverTask ====================

    @Test
    @DisplayName("人工重放：记录不存在时抛出 404")
    void redeliverTask_missingTestThrows() {
        when(empAiTestMapper.selectById(1L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.redeliverTask(1L));
    }

    @Test
    @DisplayName("人工重放：非 FAILED 状态被拒绝")
    void redeliverTask_stateConflictThrows() {
        EmpAiTest test = test(1L, "Java 能力测试", 2);
        test.setGenerationState("SUCCEEDED");
        test.setEvaluationState("SUCCEEDED");
        when(empAiTestMapper.selectById(1L)).thenReturn(test);

        assertThrows(BusinessException.class, () -> service.redeliverTask(1L));
    }

    @Test
    @DisplayName("人工重放：生成任务 FAILED 时重置并重新投递")
    void redeliverTask_generationReenqueued() {
        EmpAiTest test = test(1L, "Java 能力测试", -1);
        test.setGenerationState("FAILED");
        test.setEvaluationState("SUCCEEDED");
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(empAiTestMapper.resetGenerationToPending(1L)).thenReturn(1);

        assertTrue(service.redeliverTask(1L));

        verify(outboxDispatcher).enqueue(eq("AI_TEST"), anyString(), eq("ai.test.generate"), any());
        verify(sysOperationLogService).save(any());
    }

    @Test
    @DisplayName("人工重放：评分任务 FAILED 时重置并重新投递")
    void redeliverTask_evaluationReenqueued() {
        EmpAiTest test = test(1L, "Java 能力测试", 2);
        test.setGenerationState("SUCCEEDED");
        test.setEvaluationState("FAILED");
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(empAiTestMapper.resetEvaluationToPending(1L)).thenReturn(1);

        assertTrue(service.redeliverTask(1L));

        verify(outboxDispatcher).enqueue(eq("AI_TEST"), anyString(), eq("ai.test.evaluate"), any());
    }

    @Test
    @DisplayName("人工重放：审计日志写入失败不影响重放结果")
    void redeliverTask_auditLogFailureStillSucceeds() {
        EmpAiTest test = test(1L, "Java 能力测试", -1);
        test.setGenerationState("FAILED");
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(empAiTestMapper.resetGenerationToPending(1L)).thenReturn(1);
        doThrow(new RuntimeException("log down")).when(sysOperationLogService).save(any());

        assertTrue(service.redeliverTask(1L));
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("查询测试结果：不存在时抛出 404")
    void getTestResult_missingThrows() {
        when(empAiTestMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getTestResult(1L));
    }

    @Test
    @DisplayName("查询测试结果：存在时返回记录")
    void getTestResult_returnsRecord() {
        when(empAiTestMapper.selectById(1L)).thenReturn(test(1L, "Java 能力测试", 2));
        assertNotNull(service.getTestResult(1L));
    }

    @Test
    @DisplayName("按 workflowId 查询：入参为 null 时返回 null")
    void getLatestByWorkflowId_nullReturnsNull() {
        assertNull(service.getLatestByWorkflowId(null));
    }

    @Test
    @DisplayName("按 workflowId 查询：正常返回最新记录")
    void getLatestByWorkflowId_returnsLatest() {
        when(empAiTestMapper.selectOne(any())).thenReturn(test(1L, "Java 能力测试", 2));
        assertNotNull(service.getLatestByWorkflowId(55L));
    }

    // ==================== importToAbilityProfile ====================

    @Test
    @DisplayName("导入能力档案：状态不等于 2 时拒绝")
    void importToAbilityProfile_invalidStatusThrows() {
        when(empAiTestMapper.selectById(1L)).thenReturn(test(1L, "Java 能力测试", 1));
        assertThrows(BusinessException.class, () -> service.importToAbilityProfile(1L));
    }

    @Test
    @DisplayName("导入能力档案：工作流测试禁止直接导入")
    void importToAbilityProfile_workflowTestBlocked() {
        EmpAiTest test = test(1L, "Java 能力测试", 2);
        test.setWorkflowId(55L);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        assertThrows(BusinessException.class, () -> service.importToAbilityProfile(1L));
    }

    @Test
    @DisplayName("导入能力档案：缺少掌握等级时拒绝")
    void importToAbilityProfile_missingMasteryLevelThrows() {
        EmpAiTest test = test(1L, "Java 能力测试", 2);
        test.setMasteryLevel(null);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        assertThrows(BusinessException.class, () -> service.importToAbilityProfile(1L));
    }

    @Test
    @DisplayName("导入能力档案：无法确定能力标签时拒绝")
    void importToAbilityProfile_noAbilityTagThrows() {
        EmpAiTest test = test(1L, "综合能力测试", 2);
        test.setAbilityTagId(null);
        test.setMasteryLevel(4);
        test.setScore(new BigDecimal("90"));
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        assertThrows(BusinessException.class, () -> service.importToAbilityProfile(1L));
    }

    @Test
    @DisplayName("导入能力档案：有通过声明时状态置 3 并返回 true")
    void importToAbilityProfile_passUpdatesStatus() {
        EmpAiTest test = test(1L, "Java 能力测试", 2);
        test.setMasteryLevel(4);
        test.setScore(new BigDecimal("90"));
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(agentBusinessApplyService.applyPersonAbilities(any(PersonAbilityExtractionResult.class)))
                .thenReturn(new AgentBusinessApplyService.PersonAbilityApplyResult(1, 1, 0, 0, 0));

        assertTrue(service.importToAbilityProfile(1L));
        assertEquals(3, test.getStatus());
    }

    @Test
    @DisplayName("导入能力档案：无通过声明时返回 false")
    void importToAbilityProfile_noPassReturnsFalse() {
        EmpAiTest test = test(1L, "Java 能力测试", 2);
        test.setMasteryLevel(4);
        test.setScore(new BigDecimal("90"));
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(agentBusinessApplyService.applyPersonAbilities(any(PersonAbilityExtractionResult.class)))
                .thenReturn(new AgentBusinessApplyService.PersonAbilityApplyResult(1, 0, 0, 1, 0));

        assertFalse(service.importToAbilityProfile(1L));
    }

    @Test
    @DisplayName("导入岗位综合测试：标题含岗位且无能力标签时按岗位能力模型导入")
    void importToAbilityProfile_postComprehensiveImport() {
        EmpAiTest test = test(1L, "Java 工程师 岗位综合能力测试", 2);
        test.setAbilityTagId(null);
        test.setMasteryLevel(4);
        test.setScore(new BigDecimal("90"));
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(postPostMapper.selectList(any())).thenReturn(List.of(post()));
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(abilityModel(11L)));
        when(abilityTagMapper.selectById(11L)).thenReturn(tag(11L, "Java"));
        when(agentBusinessApplyService.applyPersonAbilities(any(PersonAbilityExtractionResult.class)))
                .thenReturn(new AgentBusinessApplyService.PersonAbilityApplyResult(1, 1, 0, 0, 0));

        assertTrue(service.importToAbilityProfile(1L));
        assertEquals(3, test.getStatus());
    }

    @Test
    @DisplayName("导入岗位综合测试：同名岗位不存在时返回 false")
    void importToAbilityProfile_postNotFoundReturnsFalse() {
        EmpAiTest test = test(1L, "Java 工程师 岗位综合能力测试", 2);
        test.setAbilityTagId(null);
        test.setMasteryLevel(4);
        when(empAiTestMapper.selectById(1L)).thenReturn(test);
        when(postPostMapper.selectList(any())).thenReturn(List.of());

        assertFalse(service.importToAbilityProfile(1L));
    }

    private AbilityTag tag(long id, String name) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setTagName(name);
        tag.setTagCategory("TECH");
        tag.setDescription("描述");
        return tag;
    }
}
