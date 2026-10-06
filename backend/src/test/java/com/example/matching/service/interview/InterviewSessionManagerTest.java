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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InterviewSessionManager} 单元测试（公共入口与状态机分支）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewSessionManagerTest {

    @Mock private EmpVideoInterviewSessionMapper sessionMapper;
    @Mock private EmpVideoInterviewQuestionMapper questionMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private InterviewConversationStateService stateService;
    @Mock private InterviewAnswerQualityService qualityService;
    @Mock private InterviewFollowUpPolicyService policyService;
    @Mock private InterviewFollowUpGenerationService generationService;
    @Mock private InterviewFollowUpRuntimeService runtimeService;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private InterviewTimerManager timerManager;
    @Mock private InterviewDurationPolicy durationPolicy;
    @Mock private EmpResumeParseMapper resumeParseMapper;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private InterviewSessionContextSupport contextSupport;
    @Mock private InterviewTranscriptBuffer transcriptBuffer;
    @Mock private ChatMemoryProvider chatMemoryProvider;

    private InterviewSessionManager manager;
    private InterviewSessionStateSupport stateSupport;
    private final Executor directExecutor = Runnable::run;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        manager = new InterviewSessionManager(
                sessionMapper, questionMapper, eventPublisher, stateService, qualityService,
                policyService, generationService, runtimeService, abilityTagMapper, objectMapper,
                timerManager, durationPolicy, resumeParseMapper, stringRedisTemplate,
                contextSupport, null, transcriptBuffer, directExecutor);
        stateSupport = new InterviewSessionStateSupport(
                sessionMapper, questionMapper, stateService, abilityTagMapper, objectMapper);
        ReflectionTestUtils.setField(manager, "stateSupport", stateSupport);
        ReflectionTestUtils.setField(manager, "chatMemoryProvider", chatMemoryProvider);
    }

    private EmpVideoInterviewSession session(long id, int status) {
        EmpVideoInterviewSession s = new EmpVideoInterviewSession();
        s.setId(id);
        s.setStatus(status);
        return s;
    }

    private EmpVideoInterviewQuestion question(long id, int order, int duration) {
        EmpVideoInterviewQuestion q = new EmpVideoInterviewQuestion();
        q.setId(id);
        q.setQuestionOrder(order);
        q.setDurationSeconds(duration);
        q.setQuestionText("题目" + order);
        return q;
    }

    // ==================== markInterviewInProgress / isStartable / isActive ====================

    @Test
    @DisplayName("markInterviewInProgress：条件更新成功时返回 true")
    void markInterviewInProgress_success() {
        when(sessionMapper.transitionStatus(7L, 1, 2)).thenReturn(1);
        assertTrue(manager.markInterviewInProgress(7L));
    }

    @Test
    @DisplayName("markInterviewInProgress：状态不允许时返回 false")
    void markInterviewInProgress_rejected() {
        when(sessionMapper.transitionStatus(7L, 1, 2)).thenReturn(0);
        assertFalse(manager.markInterviewInProgress(7L));
    }

    @Test
    @DisplayName("isStartable：status=1 可开始")
    void isStartable_statusOne() {
        when(sessionMapper.selectById(7L)).thenReturn(session(7L, 1));
        assertTrue(manager.isStartable(7L));
    }

    @Test
    @DisplayName("isStartable：会话不存在返回 false")
    void isStartable_missingSession() {
        when(sessionMapper.selectById(7L)).thenReturn(null);
        assertFalse(manager.isStartable(7L));
    }

    @Test
    @DisplayName("isActive：status=2 进行中")
    void isActive_statusTwo() {
        when(sessionMapper.selectById(7L)).thenReturn(session(7L, 2));
        assertTrue(manager.isActive(7L));
    }

    @Test
    @DisplayName("isActive：status=1 非进行中")
    void isActive_statusOneFalse() {
        when(sessionMapper.selectById(7L)).thenReturn(session(7L, 1));
        assertFalse(manager.isActive(7L));
    }

    // ==================== resolveQuestionId ====================

    @Test
    @DisplayName("resolveQuestionId：按题序返回题目 ID")
    void resolveQuestionId_found() {
        when(questionMapper.selectList(any())).thenReturn(List.of(question(31L, 1, 60), question(32L, 2, 60)));
        assertEquals(32L, manager.resolveQuestionId(7L, 2));
    }

    @Test
    @DisplayName("resolveQuestionId：题序不存在返回 null")
    void resolveQuestionId_notFound() {
        when(questionMapper.selectList(any())).thenReturn(List.of(question(31L, 1, 60)));
        assertNull(manager.resolveQuestionId(7L, 9));
    }

    // ==================== handleManualNext 分支 ====================

    @Test
    @DisplayName("handleManualNext：EVALUATING_ANSWER 时提示等待且不推进")
    void handleManualNext_evaluatingPromptsWait() throws Exception {
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.EVALUATING_ANSWER);

        manager.handleManualNext("7");

        verify(eventPublisher).publishEvent(any(com.example.matching.event.InterviewWsEvent.class));
        verify(qualityService, never()).evaluate(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handleManualNext：ANSWERING_FOLLOW_UP 时按追问回答推进")
    void handleManualNext_followUpAnswerPath() throws Exception {
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.ANSWERING_FOLLOW_UP);
        // 无活跃追问 → 直接进入下一题分支
        when(runtimeService.getActiveFollowUp(7L)).thenReturn(null);
        when(questionMapper.selectList(any())).thenReturn(List.of(question(31L, 1, 60)));
        stateSupport.putQuestionIndex("7", 0);
        when(stateService.transition(7L, InterviewConversationState.ANSWERING_FOLLOW_UP,
                InterviewConversationState.EVALUATING_ANSWER)).thenReturn(true);
        when(stateService.transition(7L, InterviewConversationState.EVALUATING_ANSWER,
                InterviewConversationState.NEXT_OR_FINISH)).thenReturn(true);

        manager.handleManualNext("7");

        // 无活跃追问时不再触发评分
        verify(qualityService, never()).evaluate(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handleManualNext：未开始时抛 IllegalStateException")
    void handleManualNext_notStartedThrows() {
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.PRESET_QUESTION);
        // 无题目索引 → prepareAnswerEvaluation 抛"面试未开始"
        assertThrows(IllegalStateException.class, () -> manager.handleManualNext("7"));
    }

    // ==================== handleFollowUpAnswered ====================

    @Test
    @DisplayName("handleFollowUpAnswered：无活跃追问时直接推进下一题")
    void handleFollowUpAnswered_noActiveFollowUp() throws Exception {
        when(runtimeService.getActiveFollowUp(7L)).thenReturn(null);
        when(questionMapper.selectList(any())).thenReturn(List.of(question(31L, 1, 60), question(32L, 2, 60)));
        stateSupport.putQuestionIndex("7", 0);
        when(sessionMapper.selectById(7L)).thenReturn(session(7L, 2));
        when(stateService.transition(7L, null, InterviewConversationState.PRESET_QUESTION)).thenReturn(true);

        manager.handleFollowUpAnswered(7L, "MANUAL_NEXT");

        verify(timerManager).stopTimer("7");
        verify(runtimeService, never()).markAnswered(anyLong(), any());
    }

    @Test
    @DisplayName("handleFollowUpAnswered：超时结束追问时标记跳过")
    void handleFollowUpAnswered_timeoutMarksSkipped() throws Exception {
        InterviewFollowUpQuestion followUp = new InterviewFollowUpQuestion();
        followUp.setId(41L);
        followUp.setAnswerText("回答");
        when(runtimeService.getActiveFollowUp(7L)).thenReturn(followUp);
        when(questionMapper.selectList(any())).thenReturn(List.of(question(31L, 1, 60), question(32L, 2, 60)));
        stateSupport.putQuestionIndex("7", 0);
        when(sessionMapper.selectById(7L)).thenReturn(session(7L, 2));
        when(stateService.transition(7L, InterviewConversationState.ANSWERING_FOLLOW_UP,
                InterviewConversationState.EVALUATING_ANSWER)).thenReturn(true);
        when(stateService.transition(7L, InterviewConversationState.EVALUATING_ANSWER,
                InterviewConversationState.NEXT_OR_FINISH)).thenReturn(true);
        when(stateService.transition(7L, null, InterviewConversationState.PRESET_QUESTION)).thenReturn(true);

        manager.handleFollowUpAnswered(7L, "TIMEOUT");

        verify(runtimeService).markSkipped(41L, "追问回答超时，证据不足");
    }

    // ==================== startAnswerPeriodAfterQuestionRead ====================

    @Test
    @DisplayName("读题完成：状态不匹配时忽略")
    void startAnswerPeriod_ignoresWhenNotAwaiting() throws Exception {
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.ANSWERING_PRESET);

        manager.startAnswerPeriodAfterQuestionRead(7L, 1, null);

        verify(timerManager, never()).startAnswerTimer(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    @DisplayName("读题完成：追问读题确认时启动追问计时器")
    void startAnswerPeriod_followUpStartsTimer() throws Exception {
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.FOLLOW_UP_QUESTION);
        InterviewFollowUpQuestion followUp = new InterviewFollowUpQuestion();
        followUp.setId(41L);
        when(runtimeService.getActiveFollowUp(7L)).thenReturn(followUp);
        when(stateService.transition(7L, InterviewConversationState.FOLLOW_UP_QUESTION,
                InterviewConversationState.ANSWERING_FOLLOW_UP)).thenReturn(true);
        when(durationPolicy.durationForFollowUp(followUp)).thenReturn(90);

        manager.startAnswerPeriodAfterQuestionRead(7L, null, 41L);

        verify(timerManager).startFollowUpTimer(eq("7"), eq(90), eq(41L), any());
    }

    @Test
    @DisplayName("读题完成：过期的追问ID被忽略")
    void startAnswerPeriod_staleFollowUpIgnored() throws Exception {
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.FOLLOW_UP_QUESTION);
        InterviewFollowUpQuestion followUp = new InterviewFollowUpQuestion();
        followUp.setId(41L);
        when(runtimeService.getActiveFollowUp(7L)).thenReturn(followUp);

        manager.startAnswerPeriodAfterQuestionRead(7L, null, 99L);

        verify(timerManager, never()).startFollowUpTimer(any(), org.mockito.ArgumentMatchers.anyInt(),
                anyLong(), any());
    }

    @Test
    @DisplayName("读题完成：题序不匹配的主问题确认被忽略")
    void startAnswerPeriod_staleQuestionOrderIgnored() throws Exception {
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.PRESET_QUESTION);
        when(questionMapper.selectList(any())).thenReturn(List.of(question(31L, 1, 60)));
        stateSupport.putQuestionIndex("7", 0);

        manager.startAnswerPeriodAfterQuestionRead(7L, 2, null);

        verify(stateService, never()).transition(7L, InterviewConversationState.PRESET_QUESTION,
                InterviewConversationState.ANSWERING_PRESET);
    }

    // ==================== recoverActiveSession 分支 ====================

    @Test
    @DisplayName("恢复：非进行中会话抛异常")
    void recoverActiveSession_notInProgressThrows() {
        when(sessionMapper.selectById(7L)).thenReturn(session(7L, 3));
        assertThrows(IllegalStateException.class, () -> manager.recoverActiveSession(7L));
    }

    @Test
    @DisplayName("恢复：缺少当前题序抛异常")
    void recoverActiveSession_missingCurrentOrderThrows() {
        when(sessionMapper.selectById(7L)).thenReturn(session(7L, 2));
        assertThrows(IllegalStateException.class, () -> manager.recoverActiveSession(7L));
    }

    @Test
    @DisplayName("恢复：当前题目不存在时抛异常")
    void recoverActiveSession_questionNotFoundThrows() {
        EmpVideoInterviewSession s = session(7L, 2);
        s.setCurrentQuestionOrder(5);
        when(sessionMapper.selectById(7L)).thenReturn(s);
        when(questionMapper.selectList(any())).thenReturn(List.of(question(31L, 1, 60)));
        assertThrows(IllegalStateException.class, () -> manager.recoverActiveSession(7L));
    }

    @Test
    @DisplayName("恢复：EVALUATING_ANSWER 状态下返回当前状态而不重播题目")
    void recoverActiveSession_evaluatingReturnsState() throws Exception {
        EmpVideoInterviewSession s = session(7L, 2);
        s.setCurrentQuestionOrder(1);
        when(sessionMapper.selectById(7L)).thenReturn(s);
        when(questionMapper.selectList(any())).thenReturn(List.of(question(31L, 1, 60)));
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.EVALUATING_ANSWER);
        when(runtimeService.getActiveFollowUp(7L)).thenReturn(null);
        when(durationPolicy.durationForQuestion(any())).thenReturn(60);

        InterviewResumeState resume = manager.recoverActiveSession(7L);

        assertEquals(InterviewConversationState.EVALUATING_ANSWER.name(), resume.conversationState());
        assertEquals(0L, resume.questionDeadlineEpochMillis());
    }

    @Test
    @DisplayName("恢复：PRESET 状态下重播题目且不恢复计时器")
    void recoverActiveSession_presetReplaysQuestion() throws Exception {
        EmpVideoInterviewSession s = session(7L, 2);
        s.setCurrentQuestionOrder(1);
        when(sessionMapper.selectById(7L)).thenReturn(s);
        when(questionMapper.selectList(any())).thenReturn(List.of(question(31L, 1, 60)));
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.PRESET_QUESTION);
        when(runtimeService.getActiveFollowUp(7L)).thenReturn(null);
        when(durationPolicy.durationForQuestion(any())).thenReturn(60);

        InterviewResumeState resume = manager.recoverActiveSession(7L);

        assertEquals(InterviewConversationState.PRESET_QUESTION.name(), resume.conversationState());
        assertEquals(60, resume.remainingSeconds());
        verify(timerManager, never()).restoreTimers(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    @DisplayName("恢复：答题窗口有效时恢复计时器并返回剩余秒数")
    void recoverActiveSession_activeWindowRestoresTimers() throws Exception {
        EmpVideoInterviewSession s = session(7L, 2);
        s.setCurrentQuestionOrder(1);
        s.setConversationState(InterviewConversationState.ANSWERING_PRESET.name());
        s.setQuestionStartedAt(LocalDateTime.now().minusSeconds(10));
        s.setQuestionDeadlineAt(LocalDateTime.now().plusMinutes(2));
        when(sessionMapper.selectById(7L)).thenReturn(s);
        when(questionMapper.selectList(any())).thenReturn(List.of(question(31L, 1, 60)));
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.ANSWERING_PRESET);
        when(runtimeService.getActiveFollowUp(7L)).thenReturn(null);
        when(durationPolicy.durationForQuestion(any())).thenReturn(60);

        InterviewResumeState resume = manager.recoverActiveSession(7L);

        assertTrue(resume.remainingSeconds() > 0);
        verify(timerManager).restoreTimers(eq("7"), eq(7L), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    @DisplayName("恢复：缺少答题窗口时抛异常")
    void recoverActiveSession_missingDeadlineThrows() {
        EmpVideoInterviewSession s = session(7L, 2);
        s.setCurrentQuestionOrder(1);
        when(sessionMapper.selectById(7L)).thenReturn(s);
        when(questionMapper.selectList(any())).thenReturn(List.of(question(31L, 1, 60)));
        when(stateService.getState(7L)).thenReturn(InterviewConversationState.ANSWERING_PRESET);
        when(runtimeService.getActiveFollowUp(7L)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> manager.recoverActiveSession(7L));
    }

    // ==================== getCurrentQuestionIndex / isInterviewInProgress ====================

    @Test
    @DisplayName("getCurrentQuestionIndex：内存索引存在时直接返回")
    void getCurrentQuestionIndex_memoryHit() {
        stateSupport.putQuestionIndex("7", 3);
        assertEquals(3, manager.getCurrentQuestionIndex(7L));
    }

    @Test
    @DisplayName("getCurrentQuestionIndex：内存未命中时从 Redis 恢复")
    void getCurrentQuestionIndex_redisFallback() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("interview:qidx:7")).thenReturn("2");

        assertEquals(2, manager.getCurrentQuestionIndex(7L));
    }

    @Test
    @DisplayName("getCurrentQuestionIndex：Redis 无值时回退到会话记录")
    void getCurrentQuestionIndex_sessionFallback() {
        EmpVideoInterviewSession s = session(7L, 2);
        s.setCurrentQuestionOrder(4);
        when(sessionMapper.selectById(7L)).thenReturn(s);

        assertEquals(4, manager.getCurrentQuestionIndex(7L));
    }

    @Test
    @DisplayName("getCurrentQuestionIndex：全部未命中返回 null")
    void getCurrentQuestionIndex_noneReturnsNull() {
        when(sessionMapper.selectById(7L)).thenReturn(session(7L, 2));
        assertNull(manager.getCurrentQuestionIndex(7L));
    }

    @Test
    @DisplayName("isInterviewInProgress：内存有索引时为 true")
    void isInterviewInProgress_true() {
        stateSupport.putQuestionIndex("7", 0);
        assertTrue(manager.isInterviewInProgress(7L));
    }

    @Test
    @DisplayName("isInterviewInProgress：无索引时为 false")
    void isInterviewInProgress_false() {
        assertFalse(manager.isInterviewInProgress(7L));
    }

    // ==================== onManualNext ====================

    @Test
    @DisplayName("onManualNext：非 MANUAL_NEXT 事件被忽略")
    void onManualNext_otherTypeIgnored() {
        com.example.matching.event.InterviewActionEvent event =
                com.example.matching.event.InterviewActionEvent.finishInterview("7");
        manager.onManualNext(event);
        verify(qualityService, never()).evaluate(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("onManualNext：MANUAL_NEXT 事件内部异常被吞掉")
    void onManualNext_swallowsException() {
        com.example.matching.event.InterviewActionEvent event =
                com.example.matching.event.InterviewActionEvent.manualNext("7");
        // 未开始 → 内部抛 IllegalStateException，事件监听器应吞掉
        lenient().when(stateService.getState(7L)).thenReturn(InterviewConversationState.PRESET_QUESTION);

        manager.onManualNext(event);

        verify(eventPublisher, never()).publishEvent(any(
                com.example.matching.event.InterviewFinishedEvent.class));
    }
}
