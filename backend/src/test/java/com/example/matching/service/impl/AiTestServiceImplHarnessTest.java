package com.example.matching.service.impl;

import com.example.matching.service.employee.impl.AiTestServiceImpl;

import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.agent.AgentBusinessApplyService;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.service.employee.AiTestAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiTestServiceImplHarnessTest {

    private AiTestServiceImpl service;

    @Mock private AiTestAgent aiTestAgent;
    @Mock private ObjectMapper objectMapper;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    @Mock private PostPostMapper postPostMapper;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;
    @Mock private EmpAiTestMapper empAiTestMapper;
    @Mock private AgentBusinessApplyService agentBusinessApplyService;
    @Mock private EventOutboxDispatcher outboxDispatcher;
    @Mock private com.example.matching.service.system.SysOperationLogService sysOperationLogService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;
    @Mock private com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper claimGroupMapper;
    @Mock private com.example.matching.mapper.ability.PersonAbilityClaimMapper claimMapper;
    @Mock private com.example.matching.ai.validation.AssessmentQuestionBindingValidator bindingValidator;
    @Mock private com.example.matching.service.assessment.AssessmentScopeService assessmentScopeService;
    @Mock private com.example.matching.mapper.workflow.AiTestCoverageMapper coverageMapper;

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
        // ServiceImpl requires baseMapper to be set for getById()
        ReflectionTestUtils.setField(service, "baseMapper", empAiTestMapper);
    }

    @Test
    void importToAbilityProfile_blocksWhenHarnessRejectsClaim() {
        // Arrange: test record with status=2 (completed)
        EmpAiTest test = new EmpAiTest();
        test.setId(9L);
        test.setEmpId(100L);
        test.setAbilityTagId(7L);
        test.setAbilityTagName("Java");
        test.setMasteryLevel(4);
        test.setScore(new BigDecimal("86"));
        test.setAnalysisReport("AI判断候选人在Java后端方面表现稳定。");
        test.setAiEvaluation("{\"score\":86}");
        test.setStatus(2);
        when(empAiTestMapper.selectById(9L)).thenReturn(test);

        // Arrange: AgentBusinessApplyService returns all blocks
        AgentBusinessApplyService.PersonAbilityApplyResult blockResult =
                new AgentBusinessApplyService.PersonAbilityApplyResult(1, 0, 0, 1, 0);
        when(agentBusinessApplyService.applyPersonAbilities(any(PersonAbilityExtractionResult.class)))
                .thenReturn(blockResult);

        // Act
        boolean imported = service.importToAbilityProfile(9L);

        // Assert: should return false when all claims are blocked
        assertThat(imported).isFalse();

        // Assert: AgentBusinessApplyService was called (Harness path)
        verify(agentBusinessApplyService).applyPersonAbilities(any(PersonAbilityExtractionResult.class));

        // Assert: should NOT import evidence when blocked
        verify(abilityEvidenceIngestionService, never()).ingestEmployeeAbility(anyLong(), anyString());
    }

    @Test
    void importToAbilityProfile_doesNotUseAbilityTagIdAsEmployeeAbilityId() {
        EmpAiTest test = new EmpAiTest();
        test.setId(9L);
        test.setEmpId(100L);
        test.setAbilityTagId(7L);
        test.setAbilityTagName("Java");
        test.setMasteryLevel(4);
        test.setScore(new BigDecimal("86"));
        test.setAnalysisReport("Java backend capability evidence");
        test.setStatus(2);
        when(empAiTestMapper.selectById(9L)).thenReturn(test);
        when(agentBusinessApplyService.applyPersonAbilities(any(PersonAbilityExtractionResult.class)))
                .thenReturn(new AgentBusinessApplyService.PersonAbilityApplyResult(1, 1, 0, 0, 0));

        assertThat(service.importToAbilityProfile(9L)).isTrue();

        // AgentBusinessApplyService owns evidence ingestion and receives the emp_ability ID from admission.
        verify(abilityEvidenceIngestionService, never()).ingestEmployeeAbility(anyLong(), anyString());
    }

    @Test
    void generateTestPersistsGenerationTaskInOutbox() {
        com.example.matching.entity.system.AbilityTag tag = new com.example.matching.entity.system.AbilityTag();
        tag.setTagName("Java");
        when(abilityTagMapper.selectById(7L)).thenReturn(tag);
        when(empAiTestMapper.insert(any(EmpAiTest.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, EmpAiTest.class).setId(12L);
            return 1;
        });

        service.generateTest(100L, 7L, 9L);

        verify(outboxDispatcher).enqueue(
                eq("AI_TEST"), eq("matching.exchange"), eq("ai.test.generate"),
                argThat(payload -> payload instanceof com.example.matching.listener.AiTestTaskPayload task
                        && "GENERATE".equals(task.getTaskType()) && Long.valueOf(12L).equals(task.getTestId())));
    }

    @Test
    void generatePostTestPersistsTheExactPostIdForAsyncGeneration() {
        com.example.matching.entity.post.PostPost post = new com.example.matching.entity.post.PostPost();
        post.setId(42L);
        post.setPostName("Java Engineer");
        com.example.matching.entity.post.PostAbilityModel model = new com.example.matching.entity.post.PostAbilityModel();
        model.setTagId(7L);
        when(postPostMapper.selectById(42L)).thenReturn(post);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(model));
        when(empAiTestMapper.insert(any(EmpAiTest.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, EmpAiTest.class).setId(12L);
            return 1;
        });

        EmpAiTest generated = service.generatePostTest(100L, 42L, 9L);

        assertThat(generated.getPostId()).isEqualTo(42L);
    }

    @Test
    void processPostTestUsesPersistedPostIdInsteadOfNonUniqueTitle() {
        EmpAiTest test = new EmpAiTest();
        test.setId(12L);
        test.setPostId(42L);
        test.setTestTitle("Java Engineer 岗位综合能力测试");
        com.example.matching.entity.post.PostPost post = new com.example.matching.entity.post.PostPost();
        post.setId(42L);
        post.setPostName("Java Engineer");

        when(empAiTestMapper.claimGeneration(12L)).thenReturn(1);
        when(empAiTestMapper.selectById(12L)).thenReturn(test);
        when(postPostMapper.selectById(42L)).thenReturn(post);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of());
        when(aiTestAgent.generateQuestions(any())).thenReturn("[]");

        service.processGenerateQuestions(12L);

        verify(postPostMapper).selectById(42L);
        verify(postPostMapper, never()).selectOne(any());
        verify(empAiTestMapper).markGenerationSucceeded(12L);
    }

    @Test
    void buildResumeClaimsForWorkflow_capsAtLimit() {
        EmpAiTest test = new EmpAiTest();
        test.setId(1L);
        test.setWorkflowId(10L);

        List<PersonAbilityClaimGroup> groups = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            PersonAbilityClaimGroup g = new PersonAbilityClaimGroup();
            g.setId((long) (100 + i));
            g.setNormalizedAbilityName("Ability" + i);
            groups.add(g);
        }
        when(claimGroupMapper.selectList(any())).thenReturn(groups);
        when(claimMapper.selectList(any())).thenAnswer(inv -> {
            PersonAbilityClaim c = new PersonAbilityClaim();
            c.setClaimedLevel(3);
            c.setEvidenceText("evidence text");
            c.setConfidenceScore(BigDecimal.valueOf(50));
            return List.of(c);
        });

        String result = ReflectionTestUtils.invokeMethod(service, "buildResumeClaimsForWorkflow", test);

        assertThat(result).isNotNull();
        assertThat(result.lines().count()).isEqualTo(8); // 上限 K=8，不注入全部 10 条
    }
}
