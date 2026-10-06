package com.example.matching.service.interview;

import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewConversationState;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class InterviewSessionManagerAnswerWindowTest {

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
    @Mock private InterviewDurationPolicy durationPolicy;
    @Mock private EmpResumeParseMapper resumeParseMapper;
    @Mock private InterviewSessionContextSupport contextSupport;
    @Mock private ChatMemoryProvider chatMemoryProvider;
    @Mock private InterviewTranscriptBuffer transcriptBuffer;
    @Mock private Executor interviewRealtimeExecutor;

    @InjectMocks private InterviewSessionManager manager;

    @BeforeEach
    void setUp() {
        InterviewSessionStateSupport stateSupport = new InterviewSessionStateSupport(
                sessionMapper, questionMapper, stateService, abilityTagMapper, objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "stateSupport", stateSupport);
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "chatMemoryProvider", chatMemoryProvider);
    }

    @Test
    void questionReadCompletionStartsAnswerTimerForTheAcknowledgedPresetQuestion() throws Exception {
        EmpVideoInterviewQuestion question = new EmpVideoInterviewQuestion();
        question.setQuestionOrder(1);
        question.setDurationSeconds(60);
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setCurrentQuestionOrder(1);
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.PRESET_QUESTION);
        when(stateService.transition(7L, InterviewConversationState.PRESET_QUESTION,
                InterviewConversationState.ANSWERING_PRESET)).thenReturn(true);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(sessionMapper.selectById(7L)).thenReturn(session);
        when(durationPolicy.durationForQuestion(question)).thenReturn(60);

        manager.startAnswerPeriodAfterQuestionRead(7L, 1, null);

        verify(stateService).transition(7L, InterviewConversationState.PRESET_QUESTION,
                InterviewConversationState.ANSWERING_PRESET);
        verify(timerManager).startAnswerTimer(eq("7"), eq(60), eq(1), any());
    }

    @Test
    void recoveryReplaysPresetQuestionInsteadOfEvaluatingAStaleDeadline() throws Exception {
        EmpVideoInterviewQuestion question = new EmpVideoInterviewQuestion();
        question.setQuestionOrder(1);
        question.setQuestionText("请说明订单系统的设计取舍");
        question.setDurationSeconds(60);
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setId(7L);
        session.setStatus(2);
        session.setCurrentQuestionOrder(1);
        session.setConversationState(InterviewConversationState.PRESET_QUESTION.name());
        // This models a reconnect during question playback after an old deadline remains in storage.
        session.setQuestionDeadlineAt(LocalDateTime.now().minusSeconds(1));
        when(sessionMapper.selectById(7L)).thenReturn(session);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.PRESET_QUESTION);
        when(durationPolicy.durationForQuestion(question)).thenReturn(60);

        InterviewResumeState resume = manager.recoverActiveSession(7L);

        org.assertj.core.api.Assertions.assertThat(resume.conversationState())
                .isEqualTo(InterviewConversationState.PRESET_QUESTION.name());
        org.assertj.core.api.Assertions.assertThat(resume.questionDeadlineEpochMillis()).isZero();
        org.assertj.core.api.Assertions.assertThat(resume.remainingSeconds()).isEqualTo(60);
        verify(stateService, org.mockito.Mockito.never()).transition(
                7L, InterviewConversationState.ANSWERING_PRESET, InterviewConversationState.EVALUATING_ANSWER);
        verify(timerManager, org.mockito.Mockito.never()).restoreTimers(
                any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void nextQuestionFlushesCurrentQuestionBeforeEvaluatingAnswer() throws Exception {
        // M14：读取 answerTranscript 前先冲刷缓冲，确保最后一段转写被评估读取
        EmpVideoInterviewQuestion question = new EmpVideoInterviewQuestion();
        question.setId(31L);
        question.setQuestionOrder(1);
        question.setDurationSeconds(60);
        question.setExpectedTagsJson("[]");
        question.setAnswerTranscript("我的回答");
        EmpVideoInterviewQuestion persistedQuestion = new EmpVideoInterviewQuestion();
        persistedQuestion.setId(31L);
        persistedQuestion.setQuestionOrder(1);
        persistedQuestion.setDurationSeconds(60);
        persistedQuestion.setExpectedTagsJson("[]");
        persistedQuestion.setAnswerTranscript("我的回答，最后一段转写");
        EmpVideoInterviewQuestion nextQuestion = new EmpVideoInterviewQuestion();
        nextQuestion.setId(32L);
        nextQuestion.setQuestionOrder(2);
        nextQuestion.setDurationSeconds(60);
        when(questionMapper.selectList(any())).thenReturn(List.of(question, nextQuestion));
        when(questionMapper.selectById(31L)).thenReturn(persistedQuestion);
        when(questionMapper.updateById(org.mockito.ArgumentMatchers.any(EmpVideoInterviewQuestion.class))).thenReturn(1);
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setId(7L);
        session.setStatus(2);
        when(sessionMapper.selectById(7L)).thenReturn(session);
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.ANSWERING_PRESET,
                InterviewConversationState.EVALUATING_ANSWER);
        when(durationPolicy.durationForQuestion(nextQuestion)).thenReturn(60);
        when(stateService.transition(7L, InterviewConversationState.ANSWERING_PRESET,
                InterviewConversationState.EVALUATING_ANSWER)).thenReturn(true);
        when(stateService.transition(7L, null, InterviewConversationState.PRESET_QUESTION)).thenReturn(true);
        when(qualityService.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new com.example.matching.dto.interview.AnswerQualityEvaluation(
                        new com.example.matching.dto.interview.AnswerQualityEvaluation.StarCompleteness(
                                true, false, true, false),
                        30, 25, 30, 30, true, "回答过短", "result",
                        "STAR_MISSING", List.of(), List.of(), "回答过短"));
        when(runtimeService.getFollowUpsByParentQuestion(7L, 31L)).thenReturn(List.of());
        when(policyService.decide(any(), any(), any(), any()))
                .thenReturn(com.example.matching.dto.interview.FollowUpDecision.skip("answer complete", 1, 3));

        // 注入真实 stateSupport 并预置题目索引（getQuestionIndex 为内存实现）
        InterviewSessionStateSupport stateSupport = new InterviewSessionStateSupport(
                sessionMapper, questionMapper, stateService, abilityTagMapper, objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "stateSupport", stateSupport);
        stateSupport.putQuestionIndex("7", 0);

        manager.nextQuestion(7L, "MANUAL_NEXT");

        // 缓冲冲刷发生在答案读取之前
        verify(transcriptBuffer).flushCurrentQuestion(7L);
        verify(qualityService).evaluate(eq(7L), eq(persistedQuestion), eq("我的回答，最后一段转写"), any(), any(), any());
        verify(stateService, org.mockito.Mockito.atLeastOnce())
                .transition(eq(7L), org.mockito.ArgumentMatchers.isNull(),
                        eq(InterviewConversationState.PRESET_QUESTION));
    }

    @Test
    void manualNextAcknowledgesImmediatelyAndEvaluatesOnRealtimeExecutor() throws Exception {
        EmpVideoInterviewQuestion question = new EmpVideoInterviewQuestion();
        question.setId(31L);
        question.setQuestionOrder(1);
        question.setDurationSeconds(60);
        question.setExpectedTagsJson("[]");
        question.setAnswerTranscript("我的回答");
        EmpVideoInterviewQuestion followingQuestion = new EmpVideoInterviewQuestion();
        followingQuestion.setId(32L);
        followingQuestion.setQuestionOrder(2);
        followingQuestion.setDurationSeconds(60);
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setStatus(2);
        session.setSessionVersion(9L);

        when(stateService.getState(7L)).thenReturn(InterviewConversationState.ANSWERING_PRESET,
                InterviewConversationState.ANSWERING_PRESET, InterviewConversationState.EVALUATING_ANSWER);
        when(stateService.transition(7L, InterviewConversationState.ANSWERING_PRESET,
                InterviewConversationState.EVALUATING_ANSWER)).thenReturn(true);
        when(questionMapper.selectList(any())).thenReturn(List.of(question, followingQuestion));
        when(questionMapper.selectById(31L)).thenReturn(question);
        when(sessionMapper.selectById(7L)).thenReturn(session);
        InterviewSessionStateSupport stateSupport = new InterviewSessionStateSupport(
                sessionMapper, questionMapper, stateService, abilityTagMapper, objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "stateSupport", stateSupport);
        stateSupport.putQuestionIndex("7", 0);

        manager.handleManualNext("7");

        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.argThat((Object event) ->
                event instanceof com.example.matching.event.InterviewWsEvent wsEvent
                        && wsEvent.getType() == com.example.matching.event.InterviewWsEvent.Type.ANSWER_ANALYSIS_STARTED));
        verify(interviewRealtimeExecutor).execute(any(Runnable.class));
        verify(qualityService, org.mockito.Mockito.never()).evaluate(any(), any(), any(), any(), any(), any());
    }

    @Test
    void completedInterviewInvalidatesQueuedAnswerEvaluationBeforeItCanPushAQuestion() throws Exception {
        EmpVideoInterviewQuestion question = new EmpVideoInterviewQuestion();
        question.setId(31L);
        question.setQuestionOrder(1);
        question.setDurationSeconds(60);
        question.setExpectedTagsJson("[]");
        question.setAnswerTranscript("我的回答");
        EmpVideoInterviewQuestion followingQuestion = new EmpVideoInterviewQuestion();
        followingQuestion.setId(32L);
        followingQuestion.setQuestionOrder(2);
        followingQuestion.setDurationSeconds(60);
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setStatus(2);
        session.setSessionVersion(9L);

        when(stateService.getState(7L)).thenReturn(InterviewConversationState.ANSWERING_PRESET,
                InterviewConversationState.ANSWERING_PRESET, InterviewConversationState.EVALUATING_ANSWER);
        when(stateService.transition(7L, InterviewConversationState.ANSWERING_PRESET,
                InterviewConversationState.EVALUATING_ANSWER)).thenReturn(true);
        when(questionMapper.selectList(any())).thenReturn(List.of(question, followingQuestion));
        when(questionMapper.selectById(31L)).thenReturn(question);
        when(sessionMapper.selectById(7L)).thenReturn(session);
        when(qualityService.evaluate(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            session.setStatus(3);
            return new com.example.matching.dto.interview.AnswerQualityEvaluation(
                    new com.example.matching.dto.interview.AnswerQualityEvaluation.StarCompleteness(
                            true, true, true, true),
                    30, 25, 30, 30, true, "完整", "result", "COMPLETE", List.of(), List.of(), "完整");
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(interviewRealtimeExecutor).execute(any(Runnable.class));

        InterviewSessionStateSupport stateSupport = new InterviewSessionStateSupport(
                sessionMapper, questionMapper, stateService, abilityTagMapper, objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "stateSupport", stateSupport);
        stateSupport.putQuestionIndex("7", 0);

        manager.handleManualNext("7");

        verify(generationService, org.mockito.Mockito.never()).generate(any(), any(), any(), any(), any(), any(), any(), any());
        verify(stateService, org.mockito.Mockito.never()).transition(7L, null, InterviewConversationState.PRESET_QUESTION);
    }

    @Test
    void followUpAnswerAlwaysProceedsWithoutGeneratingAnotherFollowUp() throws Exception {
        EmpVideoInterviewQuestion currentQuestion = new EmpVideoInterviewQuestion();
        currentQuestion.setId(31L);
        currentQuestion.setQuestionOrder(1);
        EmpVideoInterviewQuestion nextQuestion = new EmpVideoInterviewQuestion();
        nextQuestion.setId(32L);
        nextQuestion.setQuestionOrder(2);
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setStatus(2);
        InterviewFollowUpQuestion followUp = new InterviewFollowUpQuestion();
        followUp.setId(41L);
        followUp.setParentQuestionId(31L);
        followUp.setAnswerText("我负责了故障定位和修复。");

        when(runtimeService.getActiveFollowUp(7L)).thenReturn(followUp);
        when(questionMapper.selectList(any())).thenReturn(List.of(currentQuestion, nextQuestion));
        when(sessionMapper.selectById(7L)).thenReturn(session);
        when(stateService.transition(7L, InterviewConversationState.ANSWERING_FOLLOW_UP,
                InterviewConversationState.EVALUATING_ANSWER)).thenReturn(true);
        when(stateService.transition(7L, InterviewConversationState.EVALUATING_ANSWER,
                InterviewConversationState.NEXT_OR_FINISH)).thenReturn(true);
        when(stateService.transition(7L, null, InterviewConversationState.PRESET_QUESTION)).thenReturn(true);
        when(durationPolicy.durationForQuestion(nextQuestion)).thenReturn(120);

        InterviewSessionStateSupport stateSupport = new InterviewSessionStateSupport(
                sessionMapper, questionMapper, stateService, abilityTagMapper, objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "stateSupport", stateSupport);
        stateSupport.putQuestionIndex("7", 0);

        manager.handleFollowUpAnswered(7L, "MANUAL_NEXT");

        verify(runtimeService).markAnswered(41L, "我负责了故障定位和修复。");
        verify(generationService, org.mockito.Mockito.never()).generate(any(), any(), any(), any(), any(), any(), any(), any());
        verify(qualityService, org.mockito.Mockito.never()).evaluate(any(), any(), any(), any(), any(), any());
        verify(stateService).transition(7L, InterviewConversationState.EVALUATING_ANSWER,
                InterviewConversationState.NEXT_OR_FINISH);
    }

    @Test
    void finishingInterviewClearsSessionChatMemory() throws Exception {
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setStatus(2);
        when(sessionMapper.selectById(9L)).thenReturn(session);
        when(sessionMapper.transitionStatus(9L, 2, 3)).thenReturn(1);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        when(stateService.transition(9L, null, InterviewConversationState.FINISHED)).thenReturn(true);

        manager.finishInterview(9L);

        verify(chatMemoryProvider).clear(9L);
    }
}
