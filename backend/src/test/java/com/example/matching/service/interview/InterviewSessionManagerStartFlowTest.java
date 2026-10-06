package com.example.matching.service.interview;

import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.infrastructure.llm.memory.ChatMemoryProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H5 行为测试：面试状态转换只由 InterviewSessionManager 负责，
 * startInterviewFlow 最前面执行条件更新（status=1 -> status=2），
 * 重复 START 被拒绝且不重置题目索引；启动失败未推题时回滚到 status=1。
 */
@ExtendWith(MockitoExtension.class)
class InterviewSessionManagerStartFlowTest {

    @Mock private EmpVideoInterviewSessionMapper sessionMapper;
    @Mock private EmpVideoInterviewQuestionMapper questionMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private InterviewConversationStateService stateService;
    @Mock private InterviewAnswerQualityService qualityService;
    @Mock private InterviewFollowUpPolicyService policyService;
    @Mock private InterviewFollowUpGenerationService generationService;
    @Mock private InterviewFollowUpRuntimeService runtimeService;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private ObjectMapper objectMapper;
    @Mock private InterviewTimerManager timerManager;
    @Mock private EmpResumeParseMapper resumeParseMapper;
    @Mock private InterviewSessionContextSupport contextSupport;
    @Mock private ChatMemoryProvider chatMemoryProvider;

    @InjectMocks private InterviewSessionManager manager;

    private InterviewSessionStateSupport stateSupport;

    @BeforeEach
    void setUp() {
        stateSupport = spy(new InterviewSessionStateSupport(
                sessionMapper, questionMapper, stateService, abilityTagMapper, objectMapper));
        ReflectionTestUtils.setField(manager, "stateSupport", stateSupport);
        ReflectionTestUtils.setField(manager, "chatMemoryProvider", chatMemoryProvider);
        com.example.matching.entity.employee.EmpVideoInterviewSession session =
                new com.example.matching.entity.employee.EmpVideoInterviewSession();
        session.setId(7L);
        session.setStatus(1);
        org.mockito.Mockito.lenient().when(sessionMapper.selectById(7L)).thenReturn(session);
    }

    private EmpVideoInterviewQuestion question() {
        EmpVideoInterviewQuestion q = new EmpVideoInterviewQuestion();
        q.setId(1L);
        q.setQuestionOrder(1);
        q.setQuestionText("请描述一次项目经历");
        q.setDurationSeconds(60);
        return q;
    }

    @Test
    void startInterviewFlowPerformsConditionalStatusTransitionFirst() throws Exception {
        when(sessionMapper.transitionStatus(7L, 1, 2)).thenReturn(1);
        when(stateSupport.loadQuestions(anyLong())).thenReturn(List.of(question()));

        manager.startInterviewFlow("7");

        // 状态转换使用条件更新且最先执行
        verify(sessionMapper).transitionStatus(7L, 1, 2);
        verify(stateSupport).putQuestionIndex(eq("7"), eq(0));
    }

    @Test
    void duplicateStartRejectedWithoutResettingQuestionIndex() {
        // 已在进行中（status=2）：条件更新影响 0 行 → 拒绝重复 START
        when(sessionMapper.transitionStatus(7L, 1, 2)).thenReturn(0);

        assertThatThrownBy(() -> manager.startInterviewFlow("7"))
                .isInstanceOf(IllegalStateException.class);

        verify(stateService, never()).initState(anyLong());
        verify(stateSupport, never()).putQuestionIndex(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void startFailureBeforePushingQuestionRollsBackToStatus1() throws Exception {
        when(sessionMapper.transitionStatus(7L, 1, 2)).thenReturn(1);
        when(stateSupport.loadQuestions(anyLong())).thenReturn(List.of());

        assertThatThrownBy(() -> manager.startInterviewFlow("7"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有面试题目");

        // 尚未推题：回滚到 status=1，允许重新开始
        verify(sessionMapper).transitionStatus(7L, 2, 1);
    }

    @Test
    void restStartInterviewDelegatesToSameStateMachine() throws Exception {
        when(sessionMapper.transitionStatus(7L, 1, 2)).thenReturn(1);
        when(stateSupport.loadQuestions(anyLong())).thenReturn(List.of(question()));

        manager.startInterview(7L);

        verify(sessionMapper).transitionStatus(7L, 1, 2);
        verify(stateSupport).putQuestionIndex(eq("7"), eq(0));
    }
}
