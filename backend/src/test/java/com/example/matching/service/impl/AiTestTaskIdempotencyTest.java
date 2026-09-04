package com.example.matching.service.impl;

import com.example.matching.ai.validation.AiOutputValidationException;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.SysOperationLog;
import com.example.matching.listener.AiTestTaskPayload;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.agent.AgentBusinessApplyService;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.service.employee.AiTestAgent;
import com.example.matching.service.employee.impl.AiTestServiceImpl;
import com.example.matching.service.system.SysOperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * 工作包5：AI 测试任务幂等、僵尸恢复、失败重放
 */
@ExtendWith(MockitoExtension.class)
class AiTestTaskIdempotencyTest {

    @Mock private AiTestAgent aiTestAgent;
    @Mock private ObjectMapper objectMapper;
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
    @Mock private com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper claimGroupMapper;
    @Mock private com.example.matching.mapper.ability.PersonAbilityClaimMapper claimMapper;
    @Mock private com.example.matching.ai.validation.AssessmentQuestionBindingValidator bindingValidator;
    @Mock private com.example.matching.service.assessment.AssessmentScopeService assessmentScopeService;
    @Mock private com.example.matching.mapper.workflow.AiTestCoverageMapper coverageMapper;

    private AiTestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiTestServiceImpl(
                aiTestAgent, objectMapper, abilityTagMapper,
                abilityEvidenceIngestionService,
                postPostMapper, postAbilityModelMapper,
                agentBusinessApplyService, outboxDispatcher,
                sysOperationLogService, eventPublisher, lifecycleEventPublisher,
                claimGroupMapper, claimMapper, bindingValidator, assessmentScopeService,
                coverageMapper
        );
        ReflectionTestUtils.setField(service, "baseMapper", empAiTestMapper);
    }

    private EmpAiTest pendingGenerationTest() {
        EmpAiTest test = new EmpAiTest();
        test.setId(9L);
        test.setEmpId(100L);
        test.setAbilityTagId(7L);
        test.setAbilityTagName("Java");
        test.setTestTitle("Java 能力测试");
        test.setStatus(-1);
        test.setGenerationState("PROCESSING");
        test.setRetryCount(0);
        return test;
    }

    @Test
    void concurrentDeliveriesOnlyOneClaimSucceeds() throws Exception {
        // 模拟 100 次并发投递：claim 只允许一次成功
        AtomicInteger claimSuccess = new AtomicInteger(0);
        when(empAiTestMapper.claimGeneration(anyLong())).thenAnswer(invocation ->
                claimSuccess.getAndIncrement() == 0 ? 1 : 0);
        when(empAiTestMapper.selectById(anyLong())).thenReturn(pendingGenerationTest());
        AbilityTag tag = new AbilityTag();
        tag.setTagName("Java");
        when(abilityTagMapper.selectById(7L)).thenReturn(tag);
        when(aiTestAgent.generateQuestions(any())).thenReturn("[{\"type\":\"text\",\"question\":\"q\"}]");

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch latch = new CountDownLatch(100);
        for (int i = 0; i < 100; i++) {
            pool.submit(() -> {
                try {
                    service.processGenerateQuestions(9L);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        // 只有一次成功抢占，题目生成只执行一次
        verify(aiTestAgent, times(1)).generateQuestions(any());
        verify(empAiTestMapper, times(1)).markGenerationSucceeded(9L);
        assertThat(claimSuccess.get()).isGreaterThan(0);
    }

    @Test
    void retryableFailureReturnsToPendingAndRedelivers() {
        EmpAiTest test = pendingGenerationTest();
        when(empAiTestMapper.claimGeneration(9L)).thenReturn(1);
        when(empAiTestMapper.selectById(9L)).thenReturn(test);
        when(abilityTagMapper.selectById(7L)).thenReturn(new AbilityTag());
        when(aiTestAgent.generateQuestions(any()))
                .thenThrow(new com.example.matching.common.exception.AiServiceException(
                        "provider", "generate", true, "LLM timeout"));
        when(empAiTestMapper.retryGeneration(eq(9L), anyString(), anyString())).thenReturn(1);

        service.processGenerateQuestions(9L);

        verify(empAiTestMapper).retryGeneration(eq(9L), anyString(), anyString());
        verify(outboxDispatcher).enqueue(eq("AI_TEST"), anyString(), eq("ai.test.generate"),
                argThat(payload -> payload instanceof AiTestTaskPayload task
                        && "GENERATE".equals(task.getTaskType())));
        verify(empAiTestMapper, never()).failGeneration(anyLong(), anyString(), anyString());
    }

    @Test
    void retryLimitRejectedByDatabaseStillSchedulesReliableRedelivery() {
        EmpAiTest test = pendingGenerationTest();
        test.setRetryCount(2);
        when(empAiTestMapper.claimGeneration(9L)).thenReturn(1);
        when(empAiTestMapper.selectById(9L)).thenReturn(test);
        when(abilityTagMapper.selectById(7L)).thenReturn(new AbilityTag());
        when(aiTestAgent.generateQuestions(any()))
                .thenThrow(new com.example.matching.common.exception.AiServiceException(
                        "provider", "generate", true, "LLM timeout"));
        when(empAiTestMapper.retryGeneration(eq(9L), anyString(), anyString())).thenReturn(0);

        service.processGenerateQuestions(9L);

        verify(empAiTestMapper).retryGeneration(eq(9L), eq("AI_SERVICE_ERROR"), anyString());
        verify(outboxDispatcher).enqueue(eq("AI_TEST"), anyString(), eq("ai.test.generate"),
                argThat(payload -> payload instanceof AiTestTaskPayload task
                        && "GENERATE".equals(task.getTaskType()) && Long.valueOf(9L).equals(task.getTestId())));
    }

    @Test
    void workflowTestGenerationNotifiesAssessmentFlowWhenQuestionsAreReady() {
        EmpAiTest test = pendingGenerationTest();
        test.setWorkflowId(88L);
        when(empAiTestMapper.claimGeneration(9L)).thenReturn(1);
        when(empAiTestMapper.selectById(9L)).thenReturn(test);
        when(abilityTagMapper.selectById(7L)).thenReturn(new AbilityTag());
        when(aiTestAgent.generateQuestions(any())).thenReturn("[{\"type\":\"text\",\"question\":\"q\"}]");

        service.processGenerateQuestions(9L);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isInstanceOf(com.example.matching.event.AiTestQuestionsGeneratedEvent.class)
                .extracting(event -> ((com.example.matching.event.AiTestQuestionsGeneratedEvent) event).workflowId())
                .isEqualTo(88L);
    }

    @Test
    void validationFailureIsPermanentAndNeverRetried() {
        EmpAiTest test = pendingGenerationTest();
        when(empAiTestMapper.claimGeneration(9L)).thenReturn(1);
        when(empAiTestMapper.selectById(9L)).thenReturn(test);
        when(abilityTagMapper.selectById(7L)).thenReturn(new AbilityTag());
        when(aiTestAgent.generateQuestions(any()))
                .thenThrow(new AiOutputValidationException("AI_TEST_QUESTION_SET", "questions", "题数不合法"));

        service.processGenerateQuestions(9L);

        verify(empAiTestMapper).failGeneration(eq(9L), eq("AI_OUTPUT_INVALID"), anyString());
        verify(outboxDispatcher, never()).enqueue(anyString(), anyString(), anyString(), any());
        verify(empAiTestMapper, never()).retryGeneration(anyLong(), anyString(), anyString());
    }

    @Test
    void invalidEvaluationOutputIsPermanentAndNeverRetried() {
        EmpAiTest test = pendingGenerationTest();
        test.setStatus(1);
        test.setEvaluationState("PROCESSING");
        when(empAiTestMapper.claimEvaluation(9L)).thenReturn(1);
        when(empAiTestMapper.selectById(9L)).thenReturn(test);
        when(aiTestAgent.evaluateAnswers(any())).thenReturn(
                new AiTestAgent.AiTestEvaluationResult(
                        AiTestAgent.AiTestEvaluationResult.INVALID_OUTPUT,
                        "{\"status\":\"INVALID_OUTPUT\"}", null, null,
                        "invalid", java.util.List.of()));

        service.processEvaluateAnswers(9L);

        verify(empAiTestMapper).markEvaluationSucceeded(9L);
        verify(outboxDispatcher, never()).enqueue(anyString(), anyString(), anyString(), any());
        verify(empAiTestMapper, never()).retryEvaluation(anyLong(), anyString(), anyString());
    }

    @Test
    void retriesExhaustedMarksFailedAndStopsAutoDelivery() {
        EmpAiTest test = pendingGenerationTest();
        test.setRetryCount(3);
        when(empAiTestMapper.claimGeneration(9L)).thenReturn(1);
        when(empAiTestMapper.selectById(9L)).thenReturn(test);
        when(abilityTagMapper.selectById(7L)).thenReturn(new AbilityTag());
        when(aiTestAgent.generateQuestions(any()))
                .thenThrow(new com.example.matching.common.exception.AiServiceException(
                        "provider", "generate", true, "LLM timeout"));

        service.processGenerateQuestions(9L);

        verify(empAiTestMapper).failGeneration(eq(9L), eq("AI_SERVICE_ERROR"), anyString());
        verify(outboxDispatcher, never()).enqueue(anyString(), anyString(), anyString(), any());
    }

    @Test
    void redeliverOnlyFromFailedAndWritesAuditLog() {
        EmpAiTest test = pendingGenerationTest();
        test.setGenerationState("FAILED");
        test.setEvaluationState("FAILED");
        when(empAiTestMapper.selectById(9L)).thenReturn(test);
        when(empAiTestMapper.resetGenerationToPending(9L)).thenReturn(1);
        when(empAiTestMapper.resetEvaluationToPending(9L)).thenReturn(1);

        boolean result = service.redeliverTask(9L);

        assertThat(result).isTrue();
        verify(outboxDispatcher).enqueue(eq("AI_TEST"), anyString(), eq("ai.test.generate"), any());
        verify(outboxDispatcher).enqueue(eq("AI_TEST"), anyString(), eq("ai.test.evaluate"), any());
        verify(sysOperationLogService).save(any(SysOperationLog.class));
    }

    @Test
    void redeliverRejectsNonFailedState() {
        EmpAiTest test = pendingGenerationTest();
        test.setGenerationState("SUCCEEDED");
        test.setEvaluationState("SUCCEEDED");
        when(empAiTestMapper.selectById(9L)).thenReturn(test);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.example.matching.common.exception.BusinessException.class,
                () -> service.redeliverTask(9L));

        verify(sysOperationLogService, never()).save(any(SysOperationLog.class));
    }
}
