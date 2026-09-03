package com.example.matching.service.interview.observation;

import com.example.matching.agent.lc4j.InterviewObservationAiService;
import com.example.matching.agent.service.impl.AgentOutputValidator;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewAbilityObservation;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.employee.EmpVideoInterviewAbilityMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.interview.InterviewAbilityObservationMapper;
import com.example.matching.mapper.interview.InterviewFollowUpQuestionMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H6 行为测试：规则兜底不再只依赖 EmpVideoInterviewAbility，
 * 而是从当前 session 的题目（tagId）、回答、追问派生能力候选；
 * 无有效题目关联时不生成虚构观察，且不静默返回空。
 */
class InterviewObservationProcessorTest {

    private EmpVideoInterviewSessionMapper sessionMapper;
    private EmpVideoInterviewQuestionMapper questionMapper;
    private EmpVideoInterviewAbilityMapper abilityMapper;
    private InterviewAbilityObservationMapper observationMapper;
    private InterviewFollowUpQuestionMapper followUpQuestionMapper;
    private AbilityTagMapper abilityTagMapper;
    private PersonAbilityClaimGroupMapper claimGroupMapper;
    private AiTrustHarnessService harnessService;
    private ObjectProvider<InterviewObservationAiService> aiServiceProvider;
    private PlatformTransactionManager transactionManager;

    private InterviewObservationProcessor processor;
    private com.example.matching.agent.service.AgentGraphContextAssembler graphAssembler;
    private EmpVideoInterviewSession session;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        abilityMapper = mock(EmpVideoInterviewAbilityMapper.class);
        observationMapper = mock(InterviewAbilityObservationMapper.class);
        followUpQuestionMapper = mock(InterviewFollowUpQuestionMapper.class);
        abilityTagMapper = mock(AbilityTagMapper.class);
        claimGroupMapper = mock(PersonAbilityClaimGroupMapper.class);
        harnessService = mock(AiTrustHarnessService.class);
        aiServiceProvider = mock(ObjectProvider.class);
        transactionManager = mock(PlatformTransactionManager.class);

        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        AiHarnessDecisionDTO pass = new AiHarnessDecisionDTO();
        pass.setDecision("PASS");
        when(harnessService.verify(any())).thenReturn(pass);

        processor = new InterviewObservationProcessor(
                sessionMapper, questionMapper, abilityMapper, observationMapper,
                followUpQuestionMapper, abilityTagMapper, claimGroupMapper, new ObjectMapper(),
                harnessService, aiServiceProvider, mock(AgentOutputValidator.class),
                new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper()),
                transactionManager,
                graphAssembler = mock(com.example.matching.agent.service.AgentGraphContextAssembler.class),
                mock(com.example.matching.service.assessment.impl.AssessmentTestResultProvider.class));

        session = new EmpVideoInterviewSession();
        session.setId(7L);
        session.setEmpId(11L);
        session.setPostId(22L);
        session.setWorkflowId(31L);
        when(sessionMapper.selectById(7L)).thenReturn(session);
        when(abilityMapper.selectList(any())).thenReturn(List.of());
        when(observationMapper.selectList(any())).thenReturn(List.of());
        when(followUpQuestionMapper.selectList(any())).thenReturn(List.of());
        when(abilityTagMapper.selectById(1L)).thenReturn(null);
        PersonAbilityClaimGroup resumeClaim = new PersonAbilityClaimGroup();
        resumeClaim.setCanonicalTagId(1L);
        resumeClaim.setTagResolutionStatus("RESOLVED");
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(resumeClaim));
    }

    private EmpVideoInterviewQuestion question(Long id, String tagsJson, String answer, BigDecimal score) {
        EmpVideoInterviewQuestion q = new EmpVideoInterviewQuestion();
        q.setId(id);
        q.setSessionId(7L);
        q.setQuestionOrder(1);
        q.setQuestionText("请描述一次项目经历");
        q.setExpectedTagsJson(tagsJson);
        q.setAnswerTranscript(answer);
        q.setAnswerScore(score);
        return q;
    }

    @Test
    void agentOffStillGeneratesObservationFromTaggedAnswer() {
        // Agent 关闭（provider 为空）时，带 tagId 且有回答的题目仍能生成观察
        when(aiServiceProvider.getIfAvailable()).thenReturn(null);
        EmpVideoInterviewQuestion q = question(101L, "[1]", "我在项目中负责了核心模块开发，带领团队完成上线，用户量提升 30%。", BigDecimal.valueOf(72));
        when(questionMapper.selectList(any())).thenReturn(List.of(q));

        List<InterviewAbilityObservation> observations = processor.conductInterviewAndObserve(7L);

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).getTagId()).isEqualTo(1L);
        assertThat(observations.get(0).getEvidenceText()).contains("核心模块开发");
        assertThat(observations.get(0).getObservedLevel()).isNotNull();
        assertThat(observations.get(0).getSourceRefsJson()).contains("fact:INTERVIEW_QUESTION:101");
        verify(followUpQuestionMapper, never()).insert(any(com.example.matching.entity.interview.InterviewFollowUpQuestion.class));
        verify(followUpQuestionMapper, never()).updateById(any(com.example.matching.entity.interview.InterviewFollowUpQuestion.class));
    }

    @Test
    void workflowLocalAssessmentAbilityUsesFrozenResumeNameInsteadOfUnknownTag() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(null);
        PersonAbilityClaimGroup localGroup = new PersonAbilityClaimGroup();
        localGroup.setId(88L);
        localGroup.setAssessmentAbilityId(88L);
        localGroup.setNormalizedAbilityName("Vue3");
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(localGroup));
        EmpVideoInterviewQuestion q = question(101L, "[88]",
                "我负责了前端核心模块开发，并完成性能优化和上线复盘。", BigDecimal.valueOf(72));
        when(questionMapper.selectList(any())).thenReturn(List.of(q));

        List<InterviewAbilityObservation> observations = processor.conductInterviewAndObserve(7L);

        assertThat(observations).singleElement().extracting(InterviewAbilityObservation::getAbilityName)
                .isEqualTo("Vue3");
    }

    @Test
    void persistsQuestionResultFromScopedObservationWithoutDiscoveringAbilities() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(null);
        EmpVideoInterviewQuestion q = question(101L, "[1]",
                "我负责支付系统重构，把接口耗时从 800ms 降到 120ms，并完成上线复盘。", BigDecimal.valueOf(75));
        when(questionMapper.selectList(any())).thenReturn(List.of(q));

        processor.conductInterviewAndObserve(7L);

        org.mockito.ArgumentCaptor<EmpVideoInterviewQuestion> captor =
                org.mockito.ArgumentCaptor.forClass(EmpVideoInterviewQuestion.class);
        verify(questionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getAnswerScore()).isNotNull();
        assertThat(captor.getValue().getAnalysisComment()).contains("核验");
    }

    @Test
    void preservesRealtimeZeroScoreInsteadOfReplacingItWithDerivedAbilityLevel() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(null);
        EmpVideoInterviewQuestion q = question(101L, "[1]", "这个项目是我造假的。", BigDecimal.ZERO);
        q.setAnalysisComment("候选人明确否认简历中的项目经历，未形成能力证据");
        when(questionMapper.selectList(any())).thenReturn(List.of(q));

        List<InterviewAbilityObservation> observations = processor.conductInterviewAndObserve(7L);

        assertThat(observations).isEmpty();
        assertThat(q.getAnswerScore()).isZero();
        assertThat(q.getAnalysisComment()).contains("否认");
        verify(questionMapper, never()).updateById(any(EmpVideoInterviewQuestion.class));
    }

    @Test
    void agentThrowsFallsBackToRuleObservations() {
        // Agent 抛异常时规则兜底仍写入 observation
        InterviewObservationAiService aiService = mock(InterviewObservationAiService.class);
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(aiService.extractObservations(anyLong(), anyString())).thenThrow(new RuntimeException("boom"));
        EmpVideoInterviewQuestion q = question(101L, "[1]", "我负责支付系统重构，把接口耗时从 800ms 降到 120ms。", BigDecimal.valueOf(75));
        when(questionMapper.selectList(any())).thenReturn(List.of(q));

        List<InterviewAbilityObservation> observations = processor.conductInterviewAndObserve(7L);

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).getTagId()).isEqualTo(1L);
    }

    @Test
    void agentPathBuildsCurrentSessionGraphAndFiltersTagIds() throws Exception {
        com.example.matching.agent.dto.graph.AgentGraphContext graphContext =
                new com.example.matching.agent.dto.graph.AgentGraphContext();
        graphContext.setStatus("FRESH");
        when(graphAssembler.buildForInterviewObservation(eq(7L), eq(java.util.Set.of(1L))))
                .thenReturn(graphContext);
        InterviewObservationAiService aiService = mock(InterviewObservationAiService.class);
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        EmpVideoInterviewQuestion q = question(101L, "[1]", "我负责了核心模块开发，带领团队完成上线。", BigDecimal.valueOf(72));
        when(questionMapper.selectList(any())).thenReturn(List.of(q));
        com.example.matching.agent.dto.interview.InterviewObservationDTO dto =
                new com.example.matching.agent.dto.interview.InterviewObservationDTO();
        com.example.matching.agent.dto.interview.InterviewObservationDTO.Observation obs =
                new com.example.matching.agent.dto.interview.InterviewObservationDTO.Observation();
        obs.setTagId(1L);            // 白名单内
        obs.setAbilityName("Java");
        obs.setObservedLevel(3);
        obs.setConfidenceScore(70);
        obs.setEvidenceText("负责核心模块开发");
        com.example.matching.agent.dto.interview.InterviewObservationDTO.Observation obs2 =
                new com.example.matching.agent.dto.interview.InterviewObservationDTO.Observation();
        obs2.setTagId(99L);          // 白名单外 → 剔除
        obs2.setAbilityName("虚构");
        obs2.setObservedLevel(3);
        obs2.setConfidenceScore(70);
        obs2.setEvidenceText("x");
        dto.setObservations(java.util.List.of(obs, obs2));
        when(aiService.extractObservations(eq(7L), anyString())).thenReturn(dto);

        List<InterviewAbilityObservation> observations = processor.conductInterviewAndObserve(7L);

        // 子图按当前会话+白名单构建；prompt 含 graphContext；白名单外观察被剔除
        verify(graphAssembler).buildForInterviewObservation(7L, java.util.Set.of(1L));
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(aiService).extractObservations(eq(7L), captor.capture());
        assertThat(captor.getValue()).contains("graphContext");
        assertThat(observations).extracting("tagId").containsExactly(1L);
    }

    @Test
    void noValidTaggedAnswerDoesNotFabricateObservations() {
        // 题目无 tagId 或没有回答：不生成虚构能力观察
        when(aiServiceProvider.getIfAvailable()).thenReturn(null);
        EmpVideoInterviewQuestion noTag = question(101L, "[]", "我做过一些开发工作。", null);
        EmpVideoInterviewQuestion noAnswer = question(102L, "[1]", null, null);
        when(questionMapper.selectList(any())).thenReturn(List.of(noTag, noAnswer));

        List<InterviewAbilityObservation> observations = processor.conductInterviewAndObserve(7L);

        assertThat(observations).isEmpty();
    }

    @Test
    void noEvidenceSkipsObservationAgentInsteadOfTriggeringAnEmptyOutputFailure() {
        InterviewObservationAiService aiService = mock(InterviewObservationAiService.class);
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(questionMapper.selectList(any())).thenReturn(List.of(question(102L, "[1]", null, null)));

        List<InterviewAbilityObservation> observations = processor.conductInterviewAndObserve(7L);

        assertThat(observations).isEmpty();
        verify(aiService, never()).extractObservations(anyLong(), anyString());
    }

    @Test
    void ruleFallbackDoesNotObserveQuestionCapabilityOutsideResumeScope() {
        when(aiServiceProvider.getIfAvailable()).thenReturn(null);
        EmpVideoInterviewQuestion q = question(101L, "[2]", "我负责了一个完整的能力模块交付。", BigDecimal.valueOf(72));
        when(questionMapper.selectList(any())).thenReturn(List.of(q));

        List<InterviewAbilityObservation> observations = processor.conductInterviewAndObserve(7L);

        assertThat(observations).isEmpty();
    }

    @Test
    void zeroScoreAnswerIsNotIncludedInAnotherValidObservationSourceRefs() {
        InterviewObservationAiService aiService = mock(InterviewObservationAiService.class);
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        EmpVideoInterviewQuestion denied = question(101L, "[1]", "这个项目是我造假的。", BigDecimal.ZERO);
        EmpVideoInterviewQuestion valid = question(102L, "[1]", "我负责接口设计、压测和上线复盘，最终把延迟降到了 120ms。", BigDecimal.valueOf(80));
        when(questionMapper.selectList(any())).thenReturn(List.of(denied, valid));
        com.example.matching.agent.dto.interview.InterviewObservationDTO dto =
                new com.example.matching.agent.dto.interview.InterviewObservationDTO();
        com.example.matching.agent.dto.interview.InterviewObservationDTO.Observation observation =
                new com.example.matching.agent.dto.interview.InterviewObservationDTO.Observation();
        observation.setTagId(1L);
        observation.setAbilityName("Java");
        observation.setObservedLevel(3);
        observation.setConfidenceScore(70);
        observation.setEvidenceText("接口设计和复盘");
        observation.setSourceRefs(List.of("fact:INTERVIEW_QUESTION:102"));
        dto.setObservations(List.of(observation));
        when(aiService.extractObservations(eq(7L), anyString())).thenReturn(dto);

        List<InterviewAbilityObservation> observations = processor.conductInterviewAndObserve(7L);

        assertThat(observations).singleElement().satisfies(obs -> {
            assertThat(obs.getSourceRefsJson()).contains("INTERVIEW_QUESTION:102");
            assertThat(obs.getSourceRefsJson()).doesNotContain("INTERVIEW_QUESTION:101");
        });
    }
}
