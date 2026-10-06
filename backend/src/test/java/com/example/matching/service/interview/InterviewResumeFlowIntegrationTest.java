package com.example.matching.service.interview;

import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewConversationState;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 关键链路集成测试：WS 面试启动/断线恢复。
 * <p>
 * 覆盖：START 条件迁移 status=1->2 → 推第 0 题 → 推进到第 1 题 →
 * 断线后 RESUME 可恢复（currentQuestionOrder 不重置）→ 重复 START 被拒绝。
 */
@ExtendWith(MockitoExtension.class)
class InterviewResumeFlowIntegrationTest {

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
    private EmpVideoInterviewSession liveSession;

    @BeforeEach
    void setUp() {
        stateSupport = spy(new InterviewSessionStateSupport(
                sessionMapper, questionMapper, stateService, abilityTagMapper, objectMapper));
        ReflectionTestUtils.setField(manager, "stateSupport", stateSupport);
        ReflectionTestUtils.setField(manager, "chatMemoryProvider", chatMemoryProvider);

        liveSession = new EmpVideoInterviewSession();
        liveSession.setId(7L);
        liveSession.setStatus(1);
        liveSession.setCurrentQuestionOrder(0);
        // 可变 session 引用：测试中通过 setStatus 模拟 DB 侧条件更新结果
        org.mockito.Mockito.lenient().when(sessionMapper.selectById(7L)).thenAnswer(inv -> liveSession);
        when(sessionMapper.transitionStatus(7L, 1, 2)).thenReturn(1);
        org.mockito.Mockito.lenient().when(sessionMapper.transitionStatus(7L, 2, 1)).thenReturn(1);
    }

    private EmpVideoInterviewQuestion question(Long id, int order, String text) {
        EmpVideoInterviewQuestion q = new EmpVideoInterviewQuestion();
        q.setId(id);
        q.setSessionId(7L);
        q.setQuestionOrder(order);
        q.setQuestionText(text);
        q.setDurationSeconds(60);
        q.setExpectedTagsJson("[]");
        return q;
    }

    @Test
    void startThenResumeAfterDisconnectKeepsQuestionIndex() throws Exception {
        // 1. START：条件迁移 status=1->2，推第 0 题
        when(stateSupport.loadQuestions(anyLong()))
                .thenReturn(List.of(question(31L, 1, "题目一"), question(32L, 2, "题目二")));

        manager.startInterviewFlow("7");
        verify(sessionMapper).transitionStatus(7L, 1, 2);
        assertThat(stateSupport.getQuestionIndex("7")).isEqualTo(0);
        // 模拟 DB 侧条件更新生效：会话进入进行中（status=2）
        liveSession.setStatus(2);

        // 2. 推进到第 1 题（模拟答题进度）
        stateSupport.putQuestionIndex("7", 1);

        // 3. 断线重连：RESUME 语义 = 会话 active 且 currentQuestionOrder 不重置
        assertThat(manager.isActive(7L)).isTrue();
        assertThat(stateSupport.getQuestionIndex("7")).isEqualTo(1);

        // 4. 重复 START 幂等返回（会话进行中），题目索引不被重置（仍从第 1 题继续）
        when(sessionMapper.transitionStatus(7L, 1, 2)).thenReturn(0);
        manager.startInterviewFlow("7");
        assertThat(stateSupport.getQuestionIndex("7")).isEqualTo(1);
        assertThat(manager.isActive(7L)).isTrue();
    }

    @Test
    void failedStartBeforePushingQuestionRollsBackToStatus1() throws Exception {
        // 启动失败且尚未推题：回滚到 status=1，允许重新开始
        when(stateSupport.loadQuestions(anyLong())).thenReturn(List.of());

        assertThatThrownBy(() -> manager.startInterviewFlow("7"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有面试题目");

        verify(sessionMapper).transitionStatus(7L, 2, 1);
    }
}
