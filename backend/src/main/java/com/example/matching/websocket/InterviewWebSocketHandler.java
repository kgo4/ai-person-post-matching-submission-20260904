package com.example.matching.websocket;

import com.example.matching.application.interview.InterviewWebSocketSessionFacade;
import com.example.matching.entity.interview.InterviewConversationState;
import com.example.matching.event.InterviewActionEvent;
import com.example.matching.integration.volcengine.asr.StreamingAsrClient;
import com.example.matching.service.interview.InterviewWsEventHandler;
import com.example.matching.utils.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面试WebSocket处理器
 * <p>
 * 处理前端WebSocket连接，负责：
 * - 接收前端音频数据，转发到流式ASR服务
 * - 推送当前题目、倒计时、切题事件
 * - 接收前端"下一题"动作
 */
@Slf4j
@Component
public class InterviewWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StreamingAsrClient streamingAsrClient;
    private final ApplicationEventPublisher eventPublisher;
    private final InterviewWebSocketSessionFacade interviewSessionFacade;

    public InterviewWebSocketHandler(StreamingAsrClient streamingAsrClient,
                                     ApplicationEventPublisher eventPublisher,
                                     InterviewWebSocketSessionFacade interviewSessionFacade) {
        this.streamingAsrClient = streamingAsrClient;
        this.eventPublisher = eventPublisher;
        this.interviewSessionFacade = interviewSessionFacade;
    }

    // 会话ID -> WebSocketSession映射
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 会话ID -> ASR会话映射
    private final Map<String, StreamingAsrClient.AsrSession> asrSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);
        if (sessionId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        sessions.put(sessionId, session);
        log.info("前端WebSocket连接已建立，sessionId: {}", sessionId);

        sendTextMessage(session, "CONNECTED", "连接成功");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = extractSessionId(session);
        if (sessionId == null) return;

        // WS 工作线程不经 JwtFilter，从握手阶段透传的 attributes 注入审计身份
        Long userId = extractUserId(session);
        try {
            if (userId != null) SecurityUtils.setCurrentUserId(userId);

            JsonNode jsonNode = objectMapper.readTree(message.getPayload());
            String action = jsonNode.get("action").asText();

            switch (action) {
                case "START_INTERVIEW" -> handleStartInterview(sessionId, jsonNode, session);
                case "RESUME_INTERVIEW" -> handleResumeInterview(sessionId, session);
                case "QUESTION_READ_COMPLETE" -> handleQuestionReadComplete(sessionId, jsonNode);
                case "NEXT_QUESTION" -> handleNextQuestion(sessionId);
                case "FINISH_INTERVIEW" -> handleFinishInterview(sessionId);
                case "PING" -> handlePing(session);
                default -> log.warn("未知的action: {}", action);
            }
        } catch (Exception e) {
            log.error("处理消息失败: {}", e.getMessage(), e);
            sendTextMessage(session, "ERROR", "处理消息失败: " + e.getMessage());
        } finally {
            SecurityUtils.clear();
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String sessionId = extractSessionId(session);
        if (sessionId == null) return;

        Long userId = extractUserId(session);
        try {
            if (userId != null) SecurityUtils.setCurrentUserId(userId);

            // 接收前端音频数据，转发到流式ASR服务
            StreamingAsrClient.AsrSession asrSession = asrSessions.get(sessionId);
            if (asrSession != null && asrSession.isConnected()) {
                try {
                    ByteBuffer buffer = message.getPayload();
                    byte[] audioData = new byte[buffer.remaining()];
                    buffer.get(audioData);
                    asrSession.sendAudio(audioData, false);
                } catch (Exception e) {
                    log.error("转发音频数据到ASR失败: {}", e.getMessage(), e);
                }
            }
        } finally {
            SecurityUtils.clear();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = extractSessionId(session);
        if (sessionId != null) {
            sessions.remove(sessionId);
            closeAsrSession(sessionId);
            log.info("前端WebSocket连接已关闭，sessionId: {}, code: {}, reason: {}",
                    sessionId, status.getCode(), status.getReason());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误: {}", exception.getMessage(), exception);
        session.close(CloseStatus.SERVER_ERROR);
    }

    /**
     * 处理开始面试
     */
    private void handleStartInterview(String sessionId, JsonNode jsonNode, WebSocketSession wsSession) throws Exception {
        log.info("开始面试，sessionId: {}", sessionId);

        // 服务端守卫：仅"题目已生成待开始"(status=1) 允许 START（业务判断下沉 Facade/Manager）。
        // 防止断线重连误发 START 或双标签页重复触发导致整个会话状态回拨到第 0 题；
        // 进行中(status=2)的会话必须走 RESUME 恢复。
        if (!interviewSessionFacade.canStartInterview(Long.parseLong(sessionId))) {
            log.warn("拒绝重复/非法 START_INTERVIEW: sessionId={}", sessionId);
            sendTextMessage(wsSession, "ERROR", "面试当前状态不允许重新开始，请使用恢复面试(RESUME)继续");
            return;
        }

        // 发布事件：启动面试流程（@EventListener 默认同步，publishEvent 返回时 startInterviewFlow 已执行完毕）
        eventPublisher.publishEvent(new InterviewWsEventHandler.StartInterviewEvent(sessionId));

        // INTERVIEW_STARTED 必须在 startInterviewFlow 成功之后发送，避免 CAS 竞争失败时
        // 客户端收到 STARTED 但永远收不到题目推送导致界面卡死。
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            if (interviewSessionFacade.isActiveSession(Long.parseLong(sessionId))) {
                sendTextMessage(session, "INTERVIEW_STARTED", sessionId);
            } else {
                // startInterviewFlow 失败（如 CAS 竞争失败、题目缺失），通知客户端重试
                log.warn("WS START_INTERVIEW 启动流程未成功，通知客户端: sessionId={}", sessionId);
                sendTextMessage(session, "ERROR", "面试启动失败，请刷新页面后重试");
            }
        }
    }

    private void openAsrSession(String sessionId, Long userId) throws Exception {
        StreamingAsrClient.AsrSession existing = asrSessions.get(sessionId);
        if (existing != null && existing.isConnected()) {
            return;
        }

        StreamingAsrClient.AsrSession asrSession = streamingAsrClient.createSession();
        asrSession.setTranscriptCallback((text, isFinal) -> {
            // ASR 回调在 ASR 客户端的 I/O 线程执行，须自行注入审计身份
            try {
                if (userId != null) {
                    SecurityUtils.setCurrentUserId(userId);
                }
                handleTranscript(sessionId, text, isFinal);
            } catch (Exception e) {
                log.error("处理转录失败: {}", e.getMessage(), e);
            } finally {
                SecurityUtils.clear();
            }
        });
        asrSessions.put(sessionId, asrSession);

        asrSession.connect();
    }

    /**
     * 处理下一题
     */
    private void handleResumeInterview(String sessionId, WebSocketSession wsSession) throws Exception {
        if (!interviewSessionFacade.isActiveSession(Long.parseLong(sessionId))) {
            sendTextMessage(wsSession, "ERROR", "Interview is not active");
            return;
        }
        InterviewWebSocketSessionFacade.ResumeState resumeState =
                interviewSessionFacade.recoverActiveSession(Long.parseLong(sessionId));
        if (resumeState == null) {
            closeAsrSession(sessionId);
            sendTextMessage(wsSession, "INTERVIEW_FINISHED", "");
            return;
        }
        if (resumeState.questionDeadlineEpochMillis() > 0) {
            openAsrSession(sessionId, SecurityUtils.getCurrentUserId());
        }
        sendResumeState(wsSession, resumeState);
    }

    private void handleNextQuestion(String sessionId) throws Exception {
        log.info("用户点击下一题，sessionId: {}", sessionId);
        closeAsrSession(sessionId);
        eventPublisher.publishEvent(InterviewActionEvent.manualNext(sessionId));
    }

    private void handleQuestionReadComplete(String sessionId, JsonNode jsonNode) throws Exception {
        Integer questionOrder = jsonNode.hasNonNull("questionOrder")
                ? jsonNode.get("questionOrder").asInt() : null;
        Long followUpId = jsonNode.hasNonNull("followUpId")
                ? jsonNode.get("followUpId").asLong() : null;
        openAsrSession(sessionId, SecurityUtils.getCurrentUserId());
        interviewSessionFacade.startAnswerPeriodAfterQuestionRead(
                Long.parseLong(sessionId), questionOrder, followUpId);
    }

    /**
     * 处理结束面试
     */
    private void handleFinishInterview(String sessionId) throws Exception {
        log.info("结束面试，sessionId: {}", sessionId);

        // 关闭ASR会话
        closeAsrSession(sessionId);

        // 统一收口到 session manager：落库 status=FINISHED、清理状态、发布 InterviewFinishedEvent 触发 AI 分析。
        // 修复：WS 路径此前只关 ASR 不落库，导致面试永停 IN_PROGRESS、无报告无分析。
        try {
            interviewSessionFacade.finishInterview(Long.parseLong(sessionId));
        } catch (Exception e) {
            log.error("结束面试处理失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            WebSocketSession wsSession = sessions.get(sessionId);
            if (wsSession != null) {
                sendTextMessage(wsSession, "ERROR", "结束面试处理失败: " + e.getMessage());
            }
            return;
        }

        // 通知前端面试已结束
        WebSocketSession wsSession = sessions.get(sessionId);
        if (wsSession != null) {
            sendTextMessage(wsSession, "INTERVIEW_FINISHED", "");
        }
    }

    /**
     * 处理客户端心跳，回 PONG 携带服务端时间戳。
     */
    private void handlePing(WebSocketSession session) throws IOException {
        sendTextMessage(session, "PONG", String.valueOf(System.currentTimeMillis()));
    }

    /**
     * 处理转录文本
     */
    private void handleTranscript(String sessionId, String text, boolean isFinal) {
        if (text == null || text.isBlank()) return;

        try {
            pushTranscript(sessionId, text, isFinal);
            if (!isFinal) {
                return;
            }
            Long sid = Long.parseLong(sessionId);
            interviewSessionFacade.appendTranscript(sid, text);

            InterviewConversationState state = interviewSessionFacade.getConversationState(sid);
            if (state == InterviewConversationState.ANSWERING_FOLLOW_UP) {
                interviewSessionFacade.appendFollowUpTranscript(sid, text);
            } else {
                interviewSessionFacade.appendTranscriptCurrentQuestion(sid, text);
            }
        } catch (Exception e) {
            log.error("保存实时转录失败，sessionId: {}", sessionId, e);
        }
    }

    // ==================== 消息发送方法 ====================

    private void sendTextMessage(WebSocketSession session, String type, String content) throws IOException {
        if (session != null && session.isOpen()) {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("type", type);
            response.put("content", content);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                }
            }
        }
    }

    private void sendResumeState(WebSocketSession session, InterviewWebSocketSessionFacade.ResumeState resumeState) throws IOException {
        if (session != null && session.isOpen()) {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("type", "RESUME_STATE");
            response.set("resume", objectMapper.valueToTree(resumeState));
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                }
            }
        }
    }

    public void sendMessage(String sessionId, String type, String content) throws IOException {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null) {
            sendTextMessage(session, type, content);
        }
    }

    public void pushQuestion(String sessionId, int questionOrder, String questionText, int durationSeconds) throws IOException {
        Long questionId = interviewSessionFacade.resolveQuestionId(Long.parseLong(sessionId), questionOrder);
        if (questionId != null) {
            interviewSessionFacade.setTranscriptCurrentQuestion(Long.parseLong(sessionId), questionId);
        }
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("type", "QUESTION");
            response.put("questionOrder", questionOrder);
            response.put("questionText", questionText);
            response.put("durationSeconds", durationSeconds);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            }
        }
    }

    public void pushCountdown(String sessionId, int remainingSeconds) throws IOException {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("type", "COUNTDOWN");
            response.put("remainingSeconds", remainingSeconds);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            }
        }
    }

    public void pushTranscript(String sessionId, String text, boolean isFinal) throws IOException {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("type", "TRANSCRIPT");
            response.put("text", text);
            response.put("isFinal", isFinal);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            }
        }
    }

    /**
     * 推送追问到前端
     */
    public void pushFollowUpQuestion(String sessionId, Long followUpId, Long parentQuestionId,
                                       String questionText, int durationSeconds, int followUpOrder) throws IOException {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("type", "FOLLOW_UP_QUESTION");
            response.put("sessionId", sessionId);
            response.put("parentQuestionId", parentQuestionId);
            response.put("followUpId", followUpId);
            response.put("questionText", questionText);
            response.put("durationSeconds", durationSeconds);
            response.put("followUpOrder", followUpOrder);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            }
            log.info("推送追问到前端，sessionId={}, followUpId={}, order={}", sessionId, followUpId, followUpOrder);
        }
    }

    // ==================== ASR会话管理 ====================

    private void closeAsrSession(String sessionId) {
        StreamingAsrClient.AsrSession asrSession = asrSessions.remove(sessionId);
        if (asrSession != null) {
            asrSession.close();
            log.info("ASR会话已关闭，sessionId: {}", sessionId);
        }
        interviewSessionFacade.flushTranscriptSession(Long.parseLong(sessionId));
    }

    private String extractSessionId(WebSocketSession session) {
        Object sessionId = session.getAttributes().get("sessionId");
        if (sessionId != null) {
            return sessionId.toString();
        }

        String uri = session.getUri() != null ? session.getUri().toString() : "";
        String[] parts = uri.split("/");
        if (parts.length >= 3) {
            return parts[parts.length - 1];
        }
        return null;
    }

    /**
     * 从握手阶段透传的 attributes 取出 userId。
     * {@link InterviewWebSocketAuthInterceptor#beforeHandshake} 在鉴权通过时写入。
     */
    private Long extractUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        if (userId instanceof Long l) return l;
        if (userId instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
