package com.example.matching.websocket;

import com.example.matching.application.interview.InterviewWebSocketSessionFacade;
import com.example.matching.event.InterviewActionEvent;
import com.example.matching.integration.volcengine.asr.StreamingAsrClient;
import com.example.matching.service.interview.InterviewWsEventHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M16 行为测试：WS Handler 仅保留协议、ASR 与消息发送，
 * START/RESUME 守卫与当前题目解析全部下沉 Facade/Manager，
 * Handler 不再注入业务 Mapper。
 */
class InterviewWebSocketHandlerTest {

    private StreamingAsrClient streamingAsrClient;
    private ApplicationEventPublisher eventPublisher;
    private InterviewWebSocketSessionFacade facade;
    private InterviewWebSocketHandler handler;
    private WebSocketSession wsSession;

    @BeforeEach
    void setUp() {
        streamingAsrClient = mock(StreamingAsrClient.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        facade = mock(InterviewWebSocketSessionFacade.class);
        handler = new InterviewWebSocketHandler(streamingAsrClient, eventPublisher, facade);
        wsSession = mock(WebSocketSession.class);
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put("sessionId", "7");
        attributes.put("userId", 1L);
        when(wsSession.getAttributes()).thenReturn(attributes);
        when(wsSession.getUri()).thenReturn(java.net.URI.create("ws://localhost/ws/7"));
    }

    @Test
    void startInterviewGuardDelegatesToFacade() throws Exception {
        // status != 1：守卫拒绝，不连接 ASR、不发事件
        when(facade.canStartInterview(7L)).thenReturn(false);

        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("action", "START_INTERVIEW");
        TextMessage message = new TextMessage(node.toString());

        handler.afterConnectionEstablished(wsSession);
        handler.handleTextMessage(wsSession, message);

        verify(facade).canStartInterview(7L);
        verify(streamingAsrClient, never()).createSession();
    }

    @Test
    void startInterviewDoesNotOpenAsrUntilQuestionReadingCompletes() throws Exception {
        when(facade.canStartInterview(7L)).thenReturn(true);
        when(facade.isActiveSession(7L)).thenReturn(true);

        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("action", "START_INTERVIEW");

        handler.afterConnectionEstablished(wsSession);
        handler.handleTextMessage(wsSession, new TextMessage(node.toString()));

        verify(eventPublisher).publishEvent(any(InterviewWsEventHandler.StartInterviewEvent.class));
        verify(streamingAsrClient, never()).createSession();
    }

    @Test
    void questionReadCompleteOpensAsrBeforeStartingAnswerPeriod() throws Exception {
        StreamingAsrClient.AsrSession asrSession = mock(StreamingAsrClient.AsrSession.class);
        when(streamingAsrClient.createSession()).thenReturn(asrSession);
        when(asrSession.isConnected()).thenReturn(true);

        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("action", "QUESTION_READ_COMPLETE");
        node.put("questionOrder", 1);

        handler.afterConnectionEstablished(wsSession);
        handler.handleTextMessage(wsSession, new TextMessage(node.toString()));

        var order = inOrder(streamingAsrClient, asrSession, facade);
        order.verify(streamingAsrClient).createSession();
        order.verify(asrSession).connect();
        order.verify(facade).startAnswerPeriodAfterQuestionRead(7L, 1, null);
    }

    @Test
    void nextQuestionClosesAsrBeforePublishingBusinessEvent() throws Exception {
        StreamingAsrClient.AsrSession asrSession = mock(StreamingAsrClient.AsrSession.class);
        when(streamingAsrClient.createSession()).thenReturn(asrSession);
        when(asrSession.isConnected()).thenReturn(true);

        ObjectNode readComplete = new ObjectMapper().createObjectNode();
        readComplete.put("action", "QUESTION_READ_COMPLETE");
        readComplete.put("questionOrder", 1);
        ObjectNode nextQuestion = new ObjectMapper().createObjectNode();
        nextQuestion.put("action", "NEXT_QUESTION");

        handler.afterConnectionEstablished(wsSession);
        handler.handleTextMessage(wsSession, new TextMessage(readComplete.toString()));
        handler.handleTextMessage(wsSession, new TextMessage(nextQuestion.toString()));

        var order = inOrder(asrSession, facade, eventPublisher);
        order.verify(asrSession).close();
        order.verify(facade).flushTranscriptSession(7L);
        order.verify(eventPublisher).publishEvent(any(InterviewActionEvent.class));
    }

    @Test
    void resumeBeforeAnswerWindowDoesNotOpenAsr() throws Exception {
        when(facade.isActiveSession(7L)).thenReturn(true);
        when(facade.recoverActiveSession(7L)).thenReturn(resumeState(0L));

        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("action", "RESUME_INTERVIEW");

        handler.afterConnectionEstablished(wsSession);
        handler.handleTextMessage(wsSession, new TextMessage(node.toString()));

        verify(streamingAsrClient, never()).createSession();
    }

    @Test
    void resumeDuringAnswerWindowOpensAsr() throws Exception {
        StreamingAsrClient.AsrSession asrSession = mock(StreamingAsrClient.AsrSession.class);
        when(facade.isActiveSession(7L)).thenReturn(true);
        when(facade.recoverActiveSession(7L)).thenReturn(resumeState(1L));
        when(streamingAsrClient.createSession()).thenReturn(asrSession);

        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("action", "RESUME_INTERVIEW");

        handler.afterConnectionEstablished(wsSession);
        handler.handleTextMessage(wsSession, new TextMessage(node.toString()));

        verify(asrSession).connect();
    }

    @Test
    void resumeInterviewGuardDelegatesToFacade() throws Exception {
        when(facade.isActiveSession(7L)).thenReturn(false);

        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("action", "RESUME_INTERVIEW");
        handler.afterConnectionEstablished(wsSession);
        handler.handleTextMessage(wsSession, new TextMessage(node.toString()));

        verify(facade).isActiveSession(7L);
        verify(streamingAsrClient, never()).createSession();
    }

    @Test
    void pushQuestionResolvesQuestionIdThroughFacade() throws Exception {
        when(facade.resolveQuestionId(7L, 1)).thenReturn(99L);
        doNothing().when(facade).setTranscriptCurrentQuestion(7L, 99L);

        handler.pushQuestion("7", 1, "题目文本", 60);

        verify(facade).resolveQuestionId(7L, 1);
        verify(facade).setTranscriptCurrentQuestion(7L, 99L);
    }

    @Test
    void interimTranscriptIsPushedButNotPersistedAsAnAnswer() throws Exception {
        handler.afterConnectionEstablished(wsSession);

        ReflectionTestUtils.invokeMethod(handler, "handleTranscript", "7", "interim text", false);

        verify(facade, never()).appendTranscript(7L, "interim text");
        verify(facade, never()).appendTranscriptCurrentQuestion(7L, "interim text");
        verify(facade, never()).appendFollowUpTranscript(7L, "interim text");
    }

    private InterviewWebSocketSessionFacade.ResumeState resumeState(long deadlineEpochMillis) {
        return new InterviewWebSocketSessionFacade.ResumeState(
                "ANSWERING_PRESET", 1, "Question", null, null, null,
                deadlineEpochMillis, 60, 60, 1L);
    }
}
